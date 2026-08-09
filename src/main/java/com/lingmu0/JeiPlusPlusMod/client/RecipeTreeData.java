package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * JEI-backed material tree data, adapted to JEI from EMI's MIT-licensed
 * material-tree model. Only real item-output recipes are accepted; JEI's tag
 * information pages are deliberately excluded.
 */
public final class RecipeTreeData {
    public static final int MAX_DEPTH = 12;
    public static final int MAX_NODES = 384;
    private static final int MAX_CANDIDATE_SEARCH_DEPTH = 12;

    private static final Map<String, List<RecipeRef>> CANDIDATE_CACHE = new HashMap<>();

    private RecipeTreeData() {
    }

    public enum Progress {
        UNSTARTED,
        PARTIAL,
        COMPLETED
    }

    public record RecipeRef(
        IRecipeCategory<?> category,
        Object recipe,
        String key,
        String registryId
    ) {
    }

    /** One or more equivalent consumed recipe slots, merged for tree display. */
    public record RecipeInput(List<ItemStack> alternatives, List<Integer> slotIndexes) {
        public RecipeInput {
            alternatives = alternatives.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
            slotIndexes = List.copyOf(slotIndexes);
        }

        public ItemStack first() {
            return alternatives.isEmpty() ? ItemStack.EMPTY : alternatives.get(0).copy();
        }
    }

    public record RecipeSnapshot(
        RecipeRef ref,
        List<RecipeInput> inputs,
        int inputSlotCount,
        List<ItemStack> outputs
    ) {
        public boolean canExpand() {
            return !inputs.isEmpty();
        }

        public long outputAmount(String ingredientKey) {
            long amount = 0;
            for (ItemStack output : outputs) {
                if (ingredientKey(output).equals(ingredientKey)) {
                    amount += output.getCount();
                }
            }
            return Math.max(1, amount);
        }

        public boolean produces(String ingredientKey) {
            return outputs.stream().anyMatch(output -> ingredientKey(output).equals(ingredientKey));
        }
    }

    public static final class Node {
        private final ItemStack stack;
        private final String ingredientKey;
        private final RecipeSnapshot recipe;
        private final List<ItemStack> alternatives;
        private final String choiceKey;
        private final String path;
        private final List<Integer> inputSlotIndexes;
        private final boolean explicitChoice;
        private final List<Node> children = new ArrayList<>();
        private boolean expanded = true;
        private boolean cycle;
        private long amount = 1;
        private long crafts;
        private long remaining;
        private Progress progress = Progress.UNSTARTED;
        private int x;
        private int y;

        private Node(ItemStack stack, RecipeSnapshot recipe) {
            this(stack, recipe, List.of(stack), "", "root", List.of(), false);
        }

        private Node(
            ItemStack stack,
            RecipeSnapshot recipe,
            List<ItemStack> alternatives,
            String choiceKey,
            String path,
            List<Integer> inputSlotIndexes,
            boolean explicitChoice
        ) {
            this.stack = stack.copy();
            this.ingredientKey = RecipeTreeData.ingredientKey(stack);
            this.recipe = recipe;
            this.alternatives = alternatives.stream().map(ItemStack::copy).toList();
            this.choiceKey = choiceKey;
            this.path = path;
            this.inputSlotIndexes = List.copyOf(inputSlotIndexes);
            this.explicitChoice = explicitChoice;
        }

        public ItemStack stack() {
            return stack;
        }

        public String ingredientKey() {
            return ingredientKey;
        }

        public RecipeSnapshot recipe() {
            return recipe;
        }

        public List<ItemStack> alternatives() {
            return alternatives;
        }

        public boolean hasAlternatives() {
            return alternatives.size() > 1;
        }

        public boolean explicitChoice() {
            return explicitChoice;
        }

        public List<Node> children() {
            return children;
        }

        public boolean expanded() {
            return expanded;
        }

        public void setExpanded(boolean expanded) {
            this.expanded = expanded;
        }

        public boolean cycle() {
            return cycle;
        }

        public long amount() {
            return amount;
        }

        public long crafts() {
            return crafts;
        }

        public long remaining() {
            return remaining;
        }

        public Progress progress() {
            return progress;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }

        void setPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public record Cost(ItemStack stack, List<ItemStack> alternatives, long required, long supplied) {
        public Cost {
            stack = stack.copy();
            alternatives = alternatives.stream().map(ItemStack::copy).toList();
        }
    }

    public record Analysis(List<Cost> costs, List<Cost> leftovers) {
    }

    public record CraftStep(
        RecipeRef recipe,
        ItemStack stack,
        List<ItemStack> alternatives,
        List<String> selectedInputs,
        long batches,
        long remaining,
        long total,
        Progress progress,
        List<String> nodePaths
    ) {
        public CraftStep {
            stack = stack.copy();
            alternatives = alternatives.stream().map(ItemStack::copy).toList();
            selectedInputs = List.copyOf(selectedInputs);
            nodePaths = List.copyOf(nodePaths);
        }
    }

    /** A live tree keeps per-tree recipe resolutions separate from global defaults. */
    public static final class Tree {
        private final RecipeSnapshot rootRecipe;
        private final ItemStack rootStack;
        private final Map<String, RecipeRef> resolutions = new HashMap<>();
        private final Map<String, String> inputSelections = new HashMap<>();
        private Node root;
        private long batches = 1;
        private boolean craftingMode;

        private Tree(RecipeSnapshot rootRecipe, ItemStack rootStack) {
            this.rootRecipe = rootRecipe;
            this.rootStack = rootStack.copy();
            rebuild();
        }

        public Node root() {
            return root;
        }

        public long batches() {
            return batches;
        }

        public void setBatches(long batches) {
            this.batches = Math.max(1, Math.min(1_000_000L, batches));
        }

        public boolean craftingMode() {
            return craftingMode;
        }

        public void setCraftingMode(boolean craftingMode) {
            this.craftingMode = craftingMode;
        }

        public void resolve(String ingredientKey, RecipeRef recipe) {
            resolutions.put(ingredientKey, recipe);
            rebuild();
        }

        public void clearResolution(String ingredientKey) {
            // A present null value intentionally suppresses the global default,
            // matching EMI's per-tree resolution behavior.
            resolutions.put(ingredientKey, null);
            rebuild();
        }

        public void selectInput(Node node, ItemStack selected) {
            if (node == null || node.choiceKey.isEmpty() || selected == null || selected.isEmpty()) {
                return;
            }
            String selectedKey = ingredientKey(selected);
            boolean valid = node.alternatives.stream()
                .anyMatch(stack -> ingredientKey(stack).equals(selectedKey));
            if (valid) {
                inputSelections.put(node.choiceKey, selectedKey);
                rebuild();
            }
        }

        public void clearInputSelection(Node node) {
            if (node != null && !node.choiceKey.isEmpty()) {
                inputSelections.remove(node.choiceKey);
                rebuild();
            }
        }

        public void rebuild() {
            BuildContext context = new BuildContext();
            root = new Node(rootStack, rootRecipe);
            context.add();
            expand(root, context, new HashSet<>(), 0, "root");
        }

        public long idealBatch() {
            return Math.max(1, idealBatch(root, 1, 1));
        }

        public Analysis analyze() {
            Map<String, MutableCost> totalCosts = new LinkedHashMap<>();
            Map<String, MutableCost> totalRemainders = new LinkedHashMap<>();
            resetProgress(root);
            calculatePlan(root, safeMultiply(rootStack.getCount(), batches), totalCosts, totalRemainders);

            Map<String, MutableCost> missingCosts = new LinkedHashMap<>();
            if (craftingMode) {
                Map<String, MutableCost> inventory = playerInventory();
                calculateProgress(root, safeMultiply(rootStack.getCount(), batches), inventory, missingCosts);
            }

            List<Cost> costs = new ArrayList<>();
            for (Map.Entry<String, MutableCost> entry : totalCosts.entrySet()) {
                MutableCost total = entry.getValue();
                long missing = craftingMode && missingCosts.containsKey(entry.getKey())
                    ? missingCosts.get(entry.getKey()).amount
                    : (craftingMode ? 0 : total.amount);
                long supplied = craftingMode ? Math.max(0, total.amount - missing) : 0;
                costs.add(new Cost(total.stack.copy(), total.alternatives, total.amount, supplied));
            }

            List<Cost> leftovers = totalRemainders.values().stream()
                .filter(cost -> cost.amount > 0)
                .map(cost -> new Cost(cost.stack.copy(), cost.alternatives, cost.amount, 0))
                .toList();
            return new Analysis(List.copyOf(costs), leftovers);
        }

        public List<CraftStep> craftingSteps() {
            analyze();
            Map<String, MutableCraftStep> steps = new LinkedHashMap<>();
            collectCraftSteps(root, steps, false);
            return steps.values().stream()
                .map(MutableCraftStep::freeze)
                .toList();
        }

        /** Missing craftable dependencies first, followed by the clicked product. */
        public List<CraftStep> recursiveCraftingSteps(CraftStep target) {
            if (target == null) {
                return List.of();
            }
            analyze();
            Set<String> targets = Set.copyOf(target.nodePaths());
            // A shift-click is an explicit request to craft the clicked
            // product again.  The normal progress pass consumes an already
            // owned product before looking at its recipe, which is correct
            // for the inventory summary but would make this action produce
            // no transfer steps.  Re-run each clicked node with its own
            // output temporarily reserved so only its missing dependencies
            // are planned recursively.
            for (String path : targets) {
                Node node = findNode(root, path);
                if (node != null) {
                    forceTargetProgress(node);
                }
            }
            List<CraftStep> steps = new ArrayList<>();
            collectTargetSubtrees(root, targets, false, steps);
            return List.copyOf(steps);
        }

        private void forceTargetProgress(Node target) {
            if (target == null || target.amount <= 0) {
                return;
            }
            Map<String, MutableCost> available = playerInventory();
            // Reserve every equivalent candidate for the clicked output. This
            // prevents an existing output stack from satisfying the target,
            // while keeping all other inventory available to its recipe tree.
            if (target.explicitChoice) {
                available.remove(target.ingredientKey);
            } else {
                available.keySet().removeIf(key -> target.alternatives.stream()
                    .anyMatch(alternative -> ingredientKey(alternative).equals(key)));
            }
            resetProgress(target);
            calculateProgress(target, target.amount, available, new LinkedHashMap<>());
        }

        private Node findNode(Node node, String path) {
            if (node == null || path == null) {
                return null;
            }
            if (path.equals(node.path)) {
                return node;
            }
            for (Node child : node.children) {
                Node found = findNode(child, path);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        private void expand(Node node, BuildContext context, Set<String> activeRecipes, int depth, String path) {
            RecipeSnapshot snapshot = node.recipe();
            if (snapshot == null || !snapshot.canExpand() || depth >= MAX_DEPTH || context.count >= MAX_NODES) {
                return;
            }

            String recipeKey = snapshot.ref().key();
            if (!activeRecipes.add(recipeKey)) {
                node.cycle = true;
                node.expanded = false;
                return;
            }

            for (int inputIndex = 0; inputIndex < snapshot.inputs().size(); inputIndex++) {
                if (context.count >= MAX_NODES) {
                    break;
                }
                RecipeInput input = snapshot.inputs().get(inputIndex);
                if (input.alternatives().isEmpty()) {
                    continue;
                }
                String choiceKey = path + "/" + snapshot.ref().key() + "/" + inputIndex;
                SelectedInput selected = selectInput(input, choiceKey);
                ItemStack selectedStack = selected.stack();
                RecipeSnapshot childRecipe = preferredRecipe(selectedStack);
                String childPath = choiceKey + "/" + ingredientKey(selectedStack);
                Node child = new Node(
                    selectedStack,
                    childRecipe,
                    input.alternatives(),
                    choiceKey,
                    childPath,
                    input.slotIndexes(),
                    selected.explicit()
                );
                node.children.add(child);
                context.add();
                expand(child, context, activeRecipes, depth + 1, childPath);
            }
            activeRecipes.remove(recipeKey);
        }

        private SelectedInput selectInput(RecipeInput input, String choiceKey) {
            String selectedKey = inputSelections.get(choiceKey);
            if (selectedKey != null) {
                for (ItemStack alternative : input.alternatives()) {
                    if (ingredientKey(alternative).equals(selectedKey)) {
                        return new SelectedInput(alternative.copy(), true);
                    }
                }
                inputSelections.remove(choiceKey);
            }
            return findCandidateWithSupply(input.alternatives())
                .map(stack -> new SelectedInput(stack, false))
                .orElseGet(() -> new SelectedInput(input.first(), false));
        }

        private RecipeSnapshot preferredRecipe(ItemStack target) {
            String key = ingredientKey(target);
            List<RecipeRef> candidates = candidates(target);
            boolean explicitResolution = resolutions.containsKey(key);
            RecipeRef preferred = explicitResolution
                ? resolutions.get(key)
                : RecipeTreeDefaults.getPreferredRecipe(target, candidates);
            if (preferred != null) {
                RecipeSnapshot snapshot = snapshot(preferred);
                if (snapshot != null && snapshot.produces(key)) {
                    return snapshot;
                }
            }
            // A cleared per-tree resolution intentionally suppresses the
            // global default. Without an explicit or configured default, the
            // ingredient remains unexpanded until the player chooses a recipe.
            return null;
        }

        private long idealBatch(Node node, long total, long amount) {
            if (node == null || node.recipe == null) {
                return total;
            }
            long divisor = node.recipe.outputAmount(node.ingredientKey);
            if (divisor > 0) {
                long multiple = divisor / gcd(divisor, amount);
                total = safeMultiply(total, multiple / gcd(total, multiple));
            }
            for (Node child : node.children) {
                total = idealBatch(child, total, safeMultiply(amount, child.stack.getCount()));
            }
            return total;
        }

        private void calculatePlan(
            Node node,
            long desired,
            Map<String, MutableCost> costs,
            Map<String, MutableCost> remainders
        ) {
            node.amount = desired;
            long remaining = consumeForNode(remainders, node, desired);
            RecipeSnapshot recipe = node.recipe;
            if (remaining <= 0) {
                node.crafts = 0;
                return;
            }
            if (recipe == null || node.children.isEmpty()) {
                addCost(costs, node, remaining);
                return;
            }

            long outputAmount = recipe.outputAmount(node.ingredientKey);
            long crafts = ceilDiv(remaining, outputAmount);
            node.crafts = crafts;
            for (Node child : node.children) {
                calculatePlan(child, safeMultiply(child.stack.getCount(), crafts), costs, remainders);
            }

            long surplus = safeMultiply(crafts, outputAmount) - remaining;
            add(remainders, node.stack, surplus);
            for (ItemStack output : recipe.outputs) {
                if (!ingredientKey(output).equals(node.ingredientKey)) {
                    add(remainders, output, safeMultiply(output.getCount(), crafts));
                }
            }
        }

        private void calculateProgress(
            Node node,
            long desired,
            Map<String, MutableCost> available,
            Map<String, MutableCost> missingCosts
        ) {
            long remaining = consumeForNode(available, node, desired);
            node.remaining = remaining;
            long supplied = desired - remaining;
            if (remaining <= 0) {
                complete(node);
                return;
            }
            node.progress = supplied > 0 ? Progress.PARTIAL : Progress.UNSTARTED;
            RecipeSnapshot recipe = node.recipe;
            if (recipe == null || node.children.isEmpty()) {
                addCost(missingCosts, node, remaining);
                return;
            }

            long crafts = ceilDiv(remaining, recipe.outputAmount(node.ingredientKey));
            boolean childProgress = false;
            for (Node child : node.children) {
                calculateProgress(child, safeMultiply(child.stack.getCount(), crafts), available, missingCosts);
                childProgress |= child.progress != Progress.UNSTARTED;
            }
            if (node.progress == Progress.UNSTARTED && childProgress) {
                node.progress = Progress.PARTIAL;
            }
            for (ItemStack output : recipe.outputs) {
                long produced = safeMultiply(output.getCount(), crafts);
                if (ingredientKey(output).equals(node.ingredientKey)) {
                    produced -= remaining;
                }
                add(available, output, produced);
            }
        }

        private void collectCraftSteps(Node node, Map<String, MutableCraftStep> steps, boolean missingOnly) {
            if (node.recipe != null && node.crafts > 0 && (!missingOnly || node.remaining > 0)) {
                long craftBatches = node.remaining > 0
                    ? ceilDiv(node.remaining, node.recipe.outputAmount(node.ingredientKey))
                    : node.crafts;
                addCraftStep(node, craftBatches, steps);
            }
            for (Node child : node.children) {
                collectCraftSteps(child, steps, missingOnly);
            }
        }

        private void collectTargetSubtrees(
            Node node,
            Set<String> targets,
            boolean forceFullTree,
            List<CraftStep> steps
        ) {
            if (targets.contains(node.path)) {
                collectSubtreePostOrder(node, forceFullTree, steps);
                return;
            }
            for (Node child : node.children) {
                collectTargetSubtrees(child, targets, forceFullTree, steps);
            }
        }

        private void collectSubtreePostOrder(
            Node node,
            boolean forceFullTree,
            List<CraftStep> steps
        ) {
            for (Node child : node.children) {
                if (forceFullTree || child.remaining > 0) {
                    collectSubtreePostOrder(child, forceFullTree, steps);
                }
            }
            if (node.recipe == null
                || node.remaining <= 0
                || (forceFullTree && node.crafts <= 0)) {
                return;
            }
            long craftBatches = forceFullTree
                ? node.crafts
                : ceilDiv(node.remaining, node.recipe.outputAmount(node.ingredientKey));
            steps.add(createCraftStep(node, craftBatches));
        }

        private void addCraftStep(Node node, long craftBatches, Map<String, MutableCraftStep> steps) {
            CraftStep candidate = createCraftStep(node, craftBatches);
            String signature = candidate.recipe().key() + "|" + ingredientKey(candidate.stack()) + "|"
                + String.join(",", candidate.selectedInputs());
            MutableCraftStep step = steps.get(signature);
            if (step == null) {
                steps.put(signature, new MutableCraftStep(candidate));
            } else {
                step.mergeAlternatives(candidate.alternatives());
                step.batches = safeAdd(step.batches, candidate.batches());
                step.remaining = safeAdd(step.remaining, candidate.remaining());
                step.total = safeAdd(step.total, candidate.total());
                step.nodePaths.addAll(candidate.nodePaths());
                if (step.progress != candidate.progress()) {
                    step.progress = Progress.PARTIAL;
                }
            }
        }

        private CraftStep createCraftStep(Node node, long craftBatches) {
            List<String> selectedInputs = new ArrayList<>(java.util.Collections.nCopies(
                node.recipe.inputSlotCount(),
                ""
            ));
            for (Node child : node.children) {
                if (!child.explicitChoice) {
                    continue;
                }
                for (int slotIndex : child.inputSlotIndexes) {
                    if (slotIndex >= 0 && slotIndex < selectedInputs.size()) {
                        selectedInputs.set(slotIndex, child.ingredientKey);
                    }
                }
            }
            return new CraftStep(
                node.recipe.ref(),
                node.stack,
                node.explicitChoice ? List.of(node.stack) : node.alternatives,
                selectedInputs,
                craftBatches,
                node.remaining,
                node.amount,
                node.progress,
                List.of(node.path)
            );
        }
    }

    private static final class BuildContext {
        private int count;

        private void add() {
            count++;
        }
    }

    private static final class MutableCost {
        private final ItemStack stack;
        private final List<ItemStack> alternatives;
        private long amount;

        private MutableCost(ItemStack stack, long amount) {
            this(stack, List.of(stack), amount);
        }

        private MutableCost(ItemStack stack, List<ItemStack> alternatives, long amount) {
            this.stack = stack.copy();
            this.stack.setCount(1);
            this.alternatives = alternatives.stream()
                .map(alternative -> {
                    ItemStack copy = alternative.copy();
                    copy.setCount(1);
                    return copy;
                })
                .toList();
            this.amount = amount;
        }
    }

    private record SelectedInput(ItemStack stack, boolean explicit) {
    }

    private static final class MutableCraftStep {
        private final RecipeRef recipe;
        private final ItemStack stack;
        private final List<ItemStack> alternatives = new ArrayList<>();
        private final List<String> selectedInputs;
        private long batches;
        private long remaining;
        private long total;
        private Progress progress;
        private final List<String> nodePaths = new ArrayList<>();

        private MutableCraftStep(CraftStep source) {
            this.recipe = source.recipe();
            this.stack = source.stack().copy();
            mergeAlternatives(source.alternatives());
            this.selectedInputs = source.selectedInputs();
            this.batches = source.batches();
            this.remaining = source.remaining();
            this.total = source.total();
            this.progress = source.progress();
            this.nodePaths.addAll(source.nodePaths());
        }

        private CraftStep freeze() {
            return new CraftStep(recipe, stack, alternatives, selectedInputs, batches, remaining, total, progress, nodePaths);
        }

        private void mergeAlternatives(Collection<ItemStack> candidates) {
            Set<String> existing = alternatives.stream()
                .map(RecipeTreeData::ingredientKey)
                .collect(java.util.stream.Collectors.toSet());
            for (ItemStack candidate : candidates) {
                if (existing.add(ingredientKey(candidate))) {
                    alternatives.add(candidate.copy());
                }
            }
        }
    }

    public static Optional<Tree> build(IRecipeLayoutDrawable<?> rootLayout) {
        Optional<RecipeSnapshot> root = snapshot(rootLayout);
        if (root.isEmpty() || root.get().outputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Tree(root.get(), root.get().outputs.get(0)));
    }

    public static boolean isSupported(IRecipeLayoutDrawable<?> layout) {
        return layout != null && isSupportedCategory(layout.getRecipeCategory()) && firstOutput(layout).isPresent();
    }

    public static boolean isSupportedCategory(IRecipeCategory<?> category) {
        if (category == null) {
            return false;
        }
        String path = category.getRecipeType().getUid().getPath();
        return !path.startsWith("tag_recipes/");
    }

    public static Optional<ItemStack> firstOutput(IRecipeLayoutDrawable<?> layout) {
        if (layout == null || !isSupportedCategory(layout.getRecipeCategory())) {
            return Optional.empty();
        }
        return layout.getRecipeSlotsView().getSlotViews().stream()
            .filter(slot -> slot.getRole() == RecipeIngredientRole.OUTPUT)
            .flatMap(IRecipeSlotView::getItemStacks)
            .filter(stack -> !stack.isEmpty())
            .findFirst()
            .map(ItemStack::copy);
    }

    public static Optional<RecipeSnapshot> snapshot(IRecipeLayoutDrawable<?> layout) {
        if (!isSupported(layout)) {
            return Optional.empty();
        }
        RecipeRef ref = ref(layout.getRecipeCategory(), layout.getRecipe());
        return Optional.of(snapshot(ref, layout));
    }

    public static RecipeSnapshot snapshot(RecipeRef ref) {
        return createLayout(ref)
            .map(layout -> snapshot(ref, layout))
            .orElse(null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Optional<IRecipeLayoutDrawable<?>> createLayout(RecipeRef ref) {
        IJeiRuntime runtime = DirectoryRecipePlugin.getJeiRuntime();
        if (runtime == null || ref == null || !isSupportedCategory(ref.category())) {
            return Optional.empty();
        }
        IFocusGroup emptyFocus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        Optional<IRecipeLayoutDrawable<Object>> layout = runtime.getRecipeManager().createRecipeLayoutDrawable(
            (IRecipeCategory) ref.category(),
            ref.recipe(),
            emptyFocus
        );
        return (Optional) layout;
    }

    public static List<RecipeRef> candidates(ItemStack target) {
        IJeiRuntime runtime = DirectoryRecipePlugin.getJeiRuntime();
        if (runtime == null || target == null || target.isEmpty()) {
            return List.of();
        }

        String cacheKey = ingredientKey(target);
        List<RecipeRef> cached = CANDIDATE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        IRecipeManager manager = runtime.getRecipeManager();
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        ItemStack focusStack = target.copy();
        focusStack.setCount(1);
        IFocus<ItemStack> focus = focusFactory.createFocus(
            RecipeIngredientRole.OUTPUT,
            VanillaTypes.ITEM_STACK,
            focusStack
        );

        List<RecipeRef> result = new ArrayList<>();
        manager.createRecipeCategoryLookup()
            .limitFocus(List.of(focus))
            .get()
            .filter(RecipeTreeData::isSupportedCategory)
            .forEach(category -> addCandidates(manager, category, focus, result));
        List<RecipeRef> immutable = List.copyOf(result);
        CANDIDATE_CACHE.put(cacheKey, immutable);
        return immutable;
    }

    public static List<RecipeSnapshot> candidateSnapshots(ItemStack target) {
        String key = ingredientKey(target);
        return candidates(target).stream()
            .map(RecipeTreeData::snapshot)
            .filter(snapshot -> snapshot != null && snapshot.produces(key))
            .toList();
    }

    public static String ingredientKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }
        IJeiRuntime runtime = DirectoryRecipePlugin.getJeiRuntime();
        if (runtime != null) {
            try {
                return runtime.getIngredientManager()
                    .getIngredientHelper(VanillaTypes.ITEM_STACK)
                    .getUniqueId(stack, UidContext.Ingredient);
            } catch (RuntimeException ignored) {
                // Registry fallback keeps startup/reload paths safe.
            }
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static void clearCaches() {
        CANDIDATE_CACHE.clear();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addCandidates(
        IRecipeManager manager,
        IRecipeCategory<?> category,
        IFocus<ItemStack> focus,
        List<RecipeRef> result
    ) {
        RecipeType type = category.getRecipeType();
        manager.createRecipeLookup(type)
            .limitFocus((Collection) List.of(focus))
            .get()
            .forEach(recipe -> result.add(ref(category, recipe)));
    }

    private static RecipeSnapshot snapshot(RecipeRef ref, IRecipeLayoutDrawable<?> layout) {
        Map<String, MutableRecipeInput> groupedInputs = new LinkedHashMap<>();
        Map<String, ItemStack> outputs = new LinkedHashMap<>();
        int inputSlotCount = 0;
        for (IRecipeSlotView slot : layout.getRecipeSlotsView().getSlotViews()) {
            RecipeIngredientRole role = slot.getRole();
            // JEI catalysts are machines/tools and are not consumed materials.
            if (role != RecipeIngredientRole.INPUT && role != RecipeIngredientRole.OUTPUT) {
                continue;
            }
            int inputSlotIndex = role == RecipeIngredientRole.INPUT ? inputSlotCount++ : -1;
            List<ItemStack> alternatives = slot.getItemStacks()
                .filter(item -> !item.isEmpty())
                .map(ItemStack::copy)
                .toList();
            if (alternatives.isEmpty()) {
                continue;
            }
            if (role == RecipeIngredientRole.INPUT) {
                Map<String, ItemStack> unique = new LinkedHashMap<>();
                for (ItemStack alternative : alternatives) {
                    unique.putIfAbsent(ingredientKey(alternative), alternative);
                }
                List<ItemStack> uniqueAlternatives = List.copyOf(unique.values());
                String signature = uniqueAlternatives.stream()
                    .map(stack -> ingredientKey(stack) + "=" + stack.getCount())
                    .sorted()
                    .reduce((left, right) -> left + "\u001F" + right)
                    .orElse("");
                MutableRecipeInput group = groupedInputs.get(signature);
                if (group == null) {
                    groupedInputs.put(signature, new MutableRecipeInput(uniqueAlternatives, inputSlotIndex));
                } else {
                    group.merge(uniqueAlternatives, inputSlotIndex);
                }
            } else {
                merge(outputs, alternatives.get(0));
            }
        }
        List<RecipeInput> inputs = groupedInputs.values().stream()
            .map(MutableRecipeInput::freeze)
            .toList();
        return new RecipeSnapshot(ref, inputs, inputSlotCount, List.copyOf(outputs.values()));
    }

    private static final class MutableRecipeInput {
        private final Map<String, ItemStack> alternatives = new LinkedHashMap<>();
        private final List<Integer> slotIndexes = new ArrayList<>();

        private MutableRecipeInput(List<ItemStack> alternatives, int slotIndex) {
            merge(alternatives, slotIndex);
        }

        private void merge(List<ItemStack> additions, int slotIndex) {
            slotIndexes.add(slotIndex);
            for (ItemStack addition : additions) {
                String key = ingredientKey(addition);
                ItemStack current = alternatives.get(key);
                if (current == null) {
                    alternatives.put(key, addition.copy());
                } else {
                    current.setCount(current.getCount() + addition.getCount());
                }
            }
        }

        private RecipeInput freeze() {
            return new RecipeInput(List.copyOf(alternatives.values()), slotIndexes);
        }
    }

    private static void merge(Map<String, ItemStack> destination, ItemStack stack) {
        String key = ingredientKey(stack);
        ItemStack existing = destination.get(key);
        if (existing == null) {
            destination.put(key, stack.copy());
        } else {
            existing.setCount(existing.getCount() + stack.getCount());
        }
    }

    private static RecipeRef ref(IRecipeCategory<?> category, Object recipe) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        ResourceLocation id = ((IRecipeCategory) category).getRegistryName(recipe);
        String registryId = id == null ? "" : id.toString();
        String identity = registryId.isEmpty() ? "runtime/" + System.identityHashCode(recipe) : registryId;
        String key = category.getRecipeType().getUid() + "|" + identity;
        return new RecipeRef(category, recipe, key, registryId);
    }

    private static Map<String, MutableCost> playerInventory() {
        Map<String, MutableCost> result = new LinkedHashMap<>();
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return result;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                add(result, stack, stack.getCount());
            }
        }
        return result;
    }

    private static Map<String, Long> playerInventoryAmounts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, MutableCost> entry : playerInventory().entrySet()) {
            result.put(entry.getKey(), entry.getValue().amount);
        }
        return result;
    }

    /**
     * Finds the first candidate that is directly in the inventory or can be
     * supplied by recursively crafting its inputs. Direct inventory matches
     * always win over recursively craftable candidates, while candidate order
     * remains stable for ties.
     */
    static Optional<ItemStack> findCandidateWithSupply(Collection<ItemStack> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> valid = candidates.stream()
            .filter(stack -> stack != null && !stack.isEmpty())
            .map(ItemStack::copy)
            .toList();
        if (valid.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Long> available = playerInventoryAmounts();
        for (ItemStack candidate : valid) {
            if (availableAmount(available, ingredientKey(candidate)) > 0) {
                return Optional.of(candidate.copy());
            }
        }

        Map<String, List<RecipeSnapshot>> recipeCache = new HashMap<>();
        for (ItemStack candidate : valid) {
            Map<String, Long> trial = new LinkedHashMap<>(available);
            if (canSupplyCandidate(candidate, trial, new HashSet<>(), 0, recipeCache)) {
                return Optional.of(candidate.copy());
            }
        }
        return Optional.empty();
    }

    /**
     * Test whether a candidate can be supplied from the inventory or by any
     * recipe chain. This is used for unlocked candidate display and transfer;
     * it never changes the tree's recipe defaults or stores a resolution.
     */
    private static boolean canSupplyCandidate(
        ItemStack wanted,
        Map<String, Long> available,
        Set<String> active,
        int depth,
        Map<String, List<RecipeSnapshot>> recipeCache
    ) {
        if (wanted == null || wanted.isEmpty()) {
            return true;
        }
        String key = ingredientKey(wanted);
        long required = Math.max(1L, wanted.getCount());
        long present = availableAmount(available, key);
        if (present >= required) {
            setAvailable(available, key, present - required);
            return true;
        }
        if (depth >= MAX_CANDIDATE_SEARCH_DEPTH || !active.add(key)) {
            return false;
        }

        long missing = required - present;
        Map<String, Long> base = new LinkedHashMap<>(available);
        setAvailable(base, key, 0);
        List<RecipeSnapshot> recipes = recipeCache.computeIfAbsent(key, ignored -> candidateSnapshots(wanted));
        for (RecipeSnapshot recipe : recipes) {
            if (recipe == null || !recipe.produces(key) || recipe.inputs().isEmpty()) {
                continue;
            }
            long crafts = ceilDiv(missing, recipe.outputAmount(key));
            if (crafts <= 0) {
                continue;
            }

            Map<String, Long> branch = new LinkedHashMap<>(base);
            boolean inputsAvailable = true;
            for (RecipeInput input : recipe.inputs()) {
                boolean alternativeAvailable = false;
                for (ItemStack alternative : input.alternatives()) {
                    ItemStack requiredInput = copyWithCount(
                        alternative,
                        safeMultiply(alternative.getCount(), crafts)
                    );
                    Map<String, Long> inputBranch = new LinkedHashMap<>(branch);
                    if (canSupplyCandidate(
                        requiredInput,
                        inputBranch,
                        new HashSet<>(active),
                        depth + 1,
                        recipeCache
                    )) {
                        branch = inputBranch;
                        alternativeAvailable = true;
                        break;
                    }
                }
                if (!alternativeAvailable) {
                    inputsAvailable = false;
                    break;
                }
            }
            if (!inputsAvailable) {
                continue;
            }

            for (ItemStack output : recipe.outputs()) {
                addAvailable(branch, output, safeMultiply(output.getCount(), crafts));
            }
            if (availableAmount(branch, key) < missing) {
                continue;
            }
            consumeAvailable(branch, key, missing);
            available.clear();
            available.putAll(branch);
            active.remove(key);
            return true;
        }
        active.remove(key);
        return false;
    }

    private static ItemStack copyWithCount(ItemStack source, long count) {
        ItemStack copy = source.copy();
        copy.setCount((int) Math.min(Integer.MAX_VALUE, Math.max(1L, count)));
        return copy;
    }

    private static long availableAmount(Map<String, Long> available, String key) {
        return available.getOrDefault(key, 0L);
    }

    private static void setAvailable(Map<String, Long> available, String key, long amount) {
        if (amount <= 0) {
            available.remove(key);
        } else {
            available.put(key, amount);
        }
    }

    private static void addAvailable(Map<String, Long> available, ItemStack stack, long amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) {
            return;
        }
        String key = ingredientKey(stack);
        setAvailable(available, key, safeAdd(availableAmount(available, key), amount));
    }

    private static void consumeAvailable(Map<String, Long> available, String key, long amount) {
        if (amount <= 0) {
            return;
        }
        setAvailable(available, key, Math.max(0, availableAmount(available, key) - amount));
    }

    private static void resetProgress(Node node) {
        node.progress = Progress.UNSTARTED;
        node.crafts = 0;
        node.remaining = 0;
        for (Node child : node.children) {
            resetProgress(child);
        }
    }

    private static void complete(Node node) {
        node.progress = Progress.COMPLETED;
        for (Node child : node.children) {
            complete(child);
        }
    }

    private static void add(Map<String, MutableCost> map, ItemStack stack, long amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) {
            return;
        }
        String key = ingredientKey(stack);
        MutableCost cost = map.get(key);
        if (cost == null) {
            map.put(key, new MutableCost(stack, amount));
        } else {
            cost.amount = safeAdd(cost.amount, amount);
        }
    }

    private static void addCost(Map<String, MutableCost> map, Node node, long amount) {
        if (node == null || amount <= 0) {
            return;
        }
        List<ItemStack> alternatives = node.explicitChoice ? List.of(node.stack) : node.alternatives;
        String key = alternatives.stream()
            .map(RecipeTreeData::ingredientKey)
            .distinct()
            .sorted()
            .reduce((left, right) -> left + "\u001F" + right)
            .orElse(node.ingredientKey);
        MutableCost cost = map.get(key);
        if (cost == null) {
            map.put(key, new MutableCost(node.stack, alternatives, amount));
        } else {
            cost.amount = safeAdd(cost.amount, amount);
        }
    }

    private static long consumeForNode(Map<String, MutableCost> map, Node node, long desired) {
        if (node == null || desired <= 0) {
            return desired;
        }
        long remaining = consume(map, node.ingredientKey, desired);
        if (!node.explicitChoice && remaining > 0) {
            for (ItemStack alternative : node.alternatives) {
                String key = ingredientKey(alternative);
                if (!key.equals(node.ingredientKey)) {
                    remaining = consume(map, key, remaining);
                    if (remaining <= 0) {
                        break;
                    }
                }
            }
        }
        return remaining;
    }

    private static long consume(Map<String, MutableCost> map, String key, long desired) {
        MutableCost cost = map.get(key);
        if (cost == null || desired <= 0) {
            return desired;
        }
        long used = Math.min(desired, cost.amount);
        cost.amount -= used;
        if (cost.amount <= 0) {
            map.remove(key);
        }
        return desired - used;
    }

    public static long inventoryAmount(ItemStack wanted) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || wanted == null || wanted.isEmpty()) {
            return 0;
        }
        String key = ingredientKey(wanted);
        long amount = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack present = player.getInventory().getItem(slot);
            if (!present.isEmpty() && ingredientKey(present).equals(key)) {
                amount = safeAdd(amount, present.getCount());
            }
        }
        return amount;
    }

    public static long inventoryAmount(Collection<ItemStack> alternatives) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || alternatives == null || alternatives.isEmpty()) {
            return 0;
        }
        Set<String> keys = alternatives.stream()
            .filter(stack -> stack != null && !stack.isEmpty())
            .map(RecipeTreeData::ingredientKey)
            .collect(java.util.stream.Collectors.toSet());
        long amount = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack present = player.getInventory().getItem(slot);
            if (!present.isEmpty() && keys.contains(ingredientKey(present))) {
                amount = safeAdd(amount, present.getCount());
            }
        }
        return amount;
    }

    private static long ceilDiv(long value, long divisor) {
        if (value <= 0) {
            return 0;
        }
        return 1 + (value - 1) / Math.max(1, divisor);
    }

    private static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long next = a % b;
            a = b;
            b = next;
        }
        return Math.max(1, a);
    }

    private static long safeMultiply(long first, long second) {
        if (first <= 0 || second <= 0) {
            return 0;
        }
        if (first > Long.MAX_VALUE / second) {
            return Long.MAX_VALUE;
        }
        return first * second;
    }

    private static long safeAdd(long first, long second) {
        if (second > 0 && first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }
}
