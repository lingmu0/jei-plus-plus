package com.lingmu0.JeiPlusPlusMod.client;

import com.lingmu0.JeiPlusPlusMod.mixin.BasicRecipeTransferHandlerAccessor;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.lang.reflect.Field;
import java.util.stream.Stream;

/** Exact-count JEI transfer plus a bottom-up queue for instant crafting stations. */
public final class RecipeTreeTransfer {
    private static List<PendingCraft> pendingCrafts = List.of();
    private static int pendingCraftIndex;
    private static int pendingMenuId = -1;
    private static int pendingResultSlot = -1;
    private static int pendingChunk;
    private static int waitFrames;
    private static int emptyFrames;

    private RecipeTreeTransfer() {
    }

    public static void cancel() {
        clearPending();
    }

    public static boolean canTransfer(RecipeTreeData.CraftStep step, boolean recursive) {
        RecipeTreeData.CraftStep first = firstStep(step, recursive);
        if (first == null) {
            return false;
        }
        int chunk = transferChunk(first);
        return chunk > 0 && runTransfer(first, chunk, false);
    }

    public static boolean transfer(RecipeTreeData.CraftStep step, boolean recursive) {
        clearPending();
        if (!recursive) {
            if (!exposeParentContainer()) {
                return false;
            }
            int chunk = transferChunk(step);
            return chunk > 0 && runTransfer(step, chunk, true);
        }

        RecipeTreeData.Tree tree = RecipeTreeSession.craftingTree();
        if (tree == null) {
            return false;
        }
        List<RecipeTreeData.CraftStep> plan = tree.recursiveCraftingSteps(step);
        if (plan.isEmpty()) {
            return false;
        }
        pendingCrafts = plan.stream()
            .filter(candidate -> candidate.batches() > 0)
            .map(candidate -> new PendingCraft(candidate, candidate.batches()))
            .toList();
        if (!exposeParentContainer()) {
            clearPending();
            return false;
        }
        return startNextCraft();
    }

    /** Called from container rendering so each transfer/craft packet can settle before the next step. */
    public static void tick(Screen currentScreen) {
        if (pendingCrafts.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(currentScreen instanceof AbstractContainerScreen<?> screen)
            || minecraft.player == null
            || minecraft.gameMode == null
            || screen.getMenu().containerId != pendingMenuId) {
            clearPending();
            return;
        }
        if (waitFrames-- > 0) {
            return;
        }

        if (pendingResultSlot < 0) {
            startNextCraft();
            return;
        }
        if (pendingResultSlot >= screen.getMenu().slots.size()) {
            clearPending();
            return;
        }

        Slot result = screen.getMenu().getSlot(pendingResultSlot);
        if (!result.hasItem()) {
            if (++emptyFrames > 40) {
                clearPending();
            }
            return;
        }

        emptyFrames = 0;
        minecraft.gameMode.handleInventoryMouseClick(
            pendingMenuId,
            pendingResultSlot,
            0,
            ClickType.QUICK_MOVE,
            minecraft.player
        );
        PendingCraft craft = pendingCrafts.get(pendingCraftIndex);
        craft.remainingBatches = Math.max(0, craft.remainingBatches - pendingChunk);
        if (craft.remainingBatches <= 0) {
            pendingCraftIndex++;
        }
        pendingResultSlot = -1;
        pendingChunk = 0;
        waitFrames = 3;
    }

    private static RecipeTreeData.CraftStep firstStep(RecipeTreeData.CraftStep step, boolean recursive) {
        if (step == null) {
            return null;
        }
        if (!recursive) {
            return step;
        }
        RecipeTreeData.Tree tree = RecipeTreeSession.craftingTree();
        if (tree == null) {
            return null;
        }
        return tree.recursiveCraftingSteps(step).stream().findFirst().orElse(null);
    }

    private static boolean startNextCraft() {
        if (pendingCraftIndex >= pendingCrafts.size()) {
            clearPending();
            RecipeTreeFavorites.refreshNow();
            return true;
        }

        AbstractContainerScreen<?> screen = containerScreen();
        if (screen == null) {
            clearPending();
            return false;
        }
        PendingCraft craft = pendingCrafts.get(pendingCraftIndex);
        int chunk = transferChunk(craft.step, craft.remainingBatches);
        if (chunk <= 0 || !runTransfer(craft.step, chunk, true)) {
            clearPending();
            return false;
        }

        AbstractContainerMenu menu = screen.getMenu();
        int resultSlot = instantResultSlot(menu);
        if (resultSlot < 0 || resultSlot >= menu.slots.size()) {
            clearPending();
            return true;
        }
        pendingMenuId = menu.containerId;
        pendingResultSlot = resultSlot;
        pendingChunk = chunk;
        waitFrames = 3;
        emptyFrames = 0;
        return true;
    }

    private static int transferChunk(RecipeTreeData.CraftStep step) {
        return transferChunk(step, step == null ? 0 : step.batches());
    }

    private static int transferChunk(RecipeTreeData.CraftStep step, long requested) {
        if (step == null || requested <= 0) {
            return 0;
        }
        IRecipeLayoutDrawable<?> layout = RecipeTreeData.createLayout(step.recipe()).orElse(null);
        if (layout == null) {
            return 0;
        }
        int capacity = maxBatchesPerTransfer(layout.getRecipeSlotsView(), step.selectedInputs());
        return (int) Math.max(1, Math.min(Math.min(Integer.MAX_VALUE, requested), capacity));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean runTransfer(RecipeTreeData.CraftStep step, int batches, boolean doTransfer) {
        if (step == null || batches <= 0) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.gameMode == null) {
            return false;
        }
        AbstractContainerScreen<?> screen = containerScreen();
        if (screen == null) {
            return false;
        }
        IRecipeLayoutDrawable<?> layout = RecipeTreeData.createLayout(step.recipe()).orElse(null);
        if (layout == null) {
            return false;
        }
        AbstractContainerMenu menu = screen.getMenu();
        IRecipeTransferManager manager = Internal.getJeiRuntime().getRecipeTransferManager();
        Optional<IRecipeTransferHandler<AbstractContainerMenu, Object>> handler = (Optional) manager
            .getRecipeTransferHandler(menu, (IRecipeCategory) layout.getRecipeCategory());
        if (handler.isEmpty()) {
            return false;
        }
        IRecipeSlotsView slots = adjustInputs(layout.getRecipeSlotsView(), step.selectedInputs(), batches);
        IRecipeTransferError validation;
        try {
            validation = handler.get().transferRecipe(
                menu, layout.getRecipe(), slots, player, false, false
            );
        } catch (RuntimeException ignored) {
            return false;
        }
        if (validation != null && !validation.getType().allowsTransfer) {
            return false;
        }
        if (!doTransfer) {
            return true;
        }

        // JEI 15.x has no counted transfer packet. Its handler always moves
        // one set, even when an adjusted ingredient stack contains a larger
        // count. Fill the real recipe slots directly so the configured batch
        // count is honoured before crafting or recursive output extraction.
        if (batches > 1) {
            Boolean exact = transferExactInputs(
                handler.get(),
                menu,
                layout.getRecipe(),
                layout.getRecipeSlotsView(),
                step.selectedInputs(),
                batches,
                player
            );
            if (exact != null) {
                return exact;
            }
        }

        try {
            IRecipeTransferError error = handler.get().transferRecipe(
                menu, layout.getRecipe(), slots, player, false, true
            );
            return error == null || error.getType().allowsTransfer;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Boolean transferExactInputs(
        IRecipeTransferHandler handler,
        AbstractContainerMenu menu,
        Object recipe,
        IRecipeSlotsView slotsView,
        List<String> selectedInputs,
        int batches,
        Player player
    ) {
        TransferSlots transferSlots = findTransferSlots(handler, menu, recipe);
        if (transferSlots == null) {
            return null;
        }

        List<IRecipeSlotView> inputViews = slotsView.getSlotViews().stream()
            .filter(slot -> slot.getRole() == RecipeIngredientRole.INPUT)
            .toList();
        if (inputViews.size() > transferSlots.recipeSlots().size()) {
            return null;
        }

        List<InputRequirement> requirements = new ArrayList<>();
        for (int i = 0; i < inputViews.size(); i++) {
            IRecipeSlotView input = inputViews.get(i);
            Optional<ItemStack> perBatch = selectedInput(input,
                i < selectedInputs.size() ? selectedInputs.get(i) : "");
            if (perBatch.isEmpty()) {
                continue;
            }
            long amount = (long) Math.max(1, perBatch.get().getCount()) * batches;
            if (amount > Integer.MAX_VALUE) {
                return false;
            }
            Slot target = transferSlots.recipeSlots().get(i);
            if (!target.allowModification(player) || !target.mayPlace(perBatch.get())) {
                return null;
            }
            requirements.add(new InputRequirement(
                target,
                perBatch.get(),
                (int) amount
            ));
        }

        if (requirements.isEmpty()) {
            return null;
        }

        if (!hasExactSupply(requirements, transferSlots.inventorySlots(), transferSlots.recipeSlots())) {
            return false;
        }

        // Match JEI's normal transfer behaviour by returning any old recipe
        // inputs to the inventory before placing the requested amount.
        for (Slot slot : transferSlots.recipeSlots()) {
            if (!slot.getItem().isEmpty()) {
                if (!slot.allowModification(player)) {
                    return false;
                }
                Minecraft.getInstance().gameMode.handleInventoryMouseClick(
                    menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, player
                );
            }
        }

        for (InputRequirement requirement : requirements) {
            int remaining = requirement.count();
            while (remaining > 0) {
                Slot source = transferSlots.inventorySlots().stream()
                    .filter(slot -> !slot.getItem().isEmpty())
                    .filter(slot -> RecipeTreeData.ingredientKey(slot.getItem())
                        .equals(RecipeTreeData.ingredientKey(requirement.stack())))
                    .findFirst()
                    .orElse(null);
                if (source == null) {
                    return false;
                }
                int capacity = requirement.target().getMaxStackSize(requirement.stack())
                    - requirement.target().getItem().getCount();
                int move = Math.min(remaining, Math.min(source.getItem().getCount(), capacity));
                if (move <= 0 || !moveItems(menu, player, source, requirement.target(), move)) {
                    return false;
                }
                remaining -= move;
            }
        }
        return true;
    }

    private static Optional<ItemStack> selectedInput(IRecipeSlotView slot, String selectedKey) {
        String preferred = preferredInputKey(slot, selectedKey);
        return slot.getItemStacks()
            .filter(stack -> !stack.isEmpty())
            .filter(stack -> preferred.isEmpty() || RecipeTreeData.ingredientKey(stack).equals(preferred))
            .findFirst()
            .map(ItemStack::copy);
    }

    private static boolean hasExactSupply(
        List<InputRequirement> requirements,
        List<Slot> inventorySlots,
        List<Slot> recipeSlots
    ) {
        java.util.Map<String, Long> available = new HashMap<>();
        Stream.concat(inventorySlots.stream(), recipeSlots.stream())
            .map(Slot::getItem)
            .filter(stack -> !stack.isEmpty())
            .forEach(stack -> available.merge(
                RecipeTreeData.ingredientKey(stack),
                (long) stack.getCount(),
                Long::sum
            ));
        for (InputRequirement requirement : requirements) {
            String key = RecipeTreeData.ingredientKey(requirement.stack());
            long count = available.getOrDefault(key, 0L);
            if (count < requirement.count()) {
                return false;
            }
            available.put(key, count - requirement.count());
        }
        return true;
    }

    private static boolean moveItems(
        AbstractContainerMenu menu,
        Player player,
        Slot source,
        Slot target,
        int count
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || !menu.getCarried().isEmpty()) {
            return false;
        }
        ItemStack sourceStack = source.getItem();
        if (sourceStack.isEmpty()) {
            return false;
        }
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, source.index, 0, ClickType.PICKUP, player);
        if (menu.getCarried().isEmpty()) {
            return false;
        }

        int placed = Math.min(count, menu.getCarried().getCount());
        boolean canPlaceAll = target.getItem().isEmpty()
            && placed == menu.getCarried().getCount()
            && target.getMaxStackSize(menu.getCarried()) >= placed;
        if (canPlaceAll) {
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, target.index, 0, ClickType.PICKUP, player);
        } else {
            for (int i = 0; i < placed; i++) {
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, target.index, 1, ClickType.PICKUP, player);
            }
            if (!menu.getCarried().isEmpty()) {
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, source.index, 0, ClickType.PICKUP, player);
            }
        }
        return menu.getCarried().isEmpty() && target.getItem().getCount() >= placed;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TransferSlots findTransferSlots(
        IRecipeTransferHandler handler,
        AbstractContainerMenu menu,
        Object recipe
    ) {
        if (handler instanceof BasicRecipeTransferHandlerAccessor accessor) {
            try {
                IRecipeTransferInfo info = accessor.jeiPlusPlus$getTransferInfo();
                return new TransferSlots(
                    List.copyOf(info.getRecipeSlots(menu, recipe)),
                    List.copyOf(info.getInventorySlots(menu, recipe))
                );
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        for (Class<?> type = handler.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!IRecipeTransferInfo.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    IRecipeTransferInfo info = (IRecipeTransferInfo) field.get(handler);
                    return new TransferSlots(
                        List.copyOf(info.getRecipeSlots(menu, recipe)),
                        List.copyOf(info.getInventorySlots(menu, recipe))
                    );
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * During a tree view, JEI's container screen is the parent rather than the
     * current screen. Simulations can use it directly; real transfers first
     * return to it so menu clicks and result extraction target the live menu.
     */
    private static AbstractContainerScreen<?> containerScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AbstractContainerScreen<?> screen) {
            return screen;
        }
        if (minecraft.screen instanceof RecipeTreeScreen treeScreen
            && treeScreen.parentScreen() instanceof AbstractContainerScreen<?> screen) {
            return screen;
        }
        return null;
    }

    private static boolean exposeParentContainer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RecipeTreeScreen treeScreen) {
            if (!(treeScreen.parentScreen() instanceof AbstractContainerScreen<?>)) {
                return false;
            }
            treeScreen.onClose();
        }
        return containerScreen() != null;
    }

    private static IRecipeSlotsView adjustInputs(
        IRecipeSlotsView original,
        List<String> selectedInputs,
        int batches
    ) {
        List<IRecipeSlotView> adjusted = new ArrayList<>();
        int inputIndex = 0;
        for (IRecipeSlotView slot : original.getSlotViews()) {
            if (slot.getRole() == RecipeIngredientRole.INPUT) {
                String selected = preferredInputKey(
                    slot,
                    inputIndex < selectedInputs.size() ? selectedInputs.get(inputIndex) : ""
                );
                adjusted.add(new AdjustedSlot(slot, selected, batches));
                inputIndex++;
            } else {
                adjusted.add(slot);
            }
        }
        List<IRecipeSlotView> immutable = List.copyOf(adjusted);
        return () -> immutable;
    }

    private static int maxBatchesPerTransfer(IRecipeSlotsView slots, List<String> selectedInputs) {
        int result = Integer.MAX_VALUE;
        int inputIndex = 0;
        boolean foundItemInput = false;
        for (IRecipeSlotView slot : slots.getSlotViews()) {
            if (slot.getRole() != RecipeIngredientRole.INPUT) {
                continue;
            }
            String selected = preferredInputKey(
                slot,
                inputIndex < selectedInputs.size() ? selectedInputs.get(inputIndex) : ""
            );
            inputIndex++;
            int slotCapacity = slot.getItemStacks()
                .filter(stack -> selected.isEmpty() || RecipeTreeData.ingredientKey(stack).equals(selected))
                .mapToInt(stack -> Math.max(1, stack.getMaxStackSize() / Math.max(1, stack.getCount())))
                .max()
                .orElse(Integer.MAX_VALUE);
            if (slotCapacity != Integer.MAX_VALUE) {
                foundItemInput = true;
                result = Math.min(result, slotCapacity);
            }
        }
        return foundItemInput ? Math.max(1, result) : 1;
    }

    /**
     * JEI transfer handlers normally use the displayed ingredient of a slot.
     * For an unfixed tag/directory input that used to be the first entry,
     * even when another candidate was the one actually present in the
     * inventory or can be reached by recursively crafting its inputs. Resolve
     * a concrete candidate at transfer time instead.
     */
    private static String preferredInputKey(IRecipeSlotView slot, String selectedKey) {
        if (!selectedKey.isEmpty()) {
            return selectedKey;
        }
        return RecipeTreeData.findCandidateWithSupply(slot.getItemStacks()
                .filter(stack -> !stack.isEmpty())
                .toList())
            .map(RecipeTreeData::ingredientKey)
            .orElse("");
    }

    private static int instantResultSlot(AbstractContainerMenu menu) {
        if (menu instanceof CraftingMenu || menu instanceof InventoryMenu) return 0;
        if (menu instanceof StonecutterMenu) return 1;
        if (menu instanceof SmithingMenu || menu instanceof LoomMenu) return 3;
        if (menu instanceof CartographyTableMenu || menu instanceof GrindstoneMenu) return 2;
        return -1;
    }

    private static void clearPending() {
        pendingCrafts = List.of();
        pendingCraftIndex = 0;
        pendingMenuId = -1;
        pendingResultSlot = -1;
        pendingChunk = 0;
        waitFrames = 0;
        emptyFrames = 0;
    }

    private static final class PendingCraft {
        private final RecipeTreeData.CraftStep step;
        private long remainingBatches;

        private PendingCraft(RecipeTreeData.CraftStep step, long remainingBatches) {
            this.step = step;
            this.remainingBatches = remainingBatches;
        }
    }

    private record TransferSlots(List<Slot> recipeSlots, List<Slot> inventorySlots) {
    }

    private record InputRequirement(Slot target, ItemStack stack, int count) {
    }

    private static final class AdjustedSlot implements IRecipeSlotView {
        private final IRecipeSlotView delegate;
        private final List<ITypedIngredient<?>> ingredients;

        private AdjustedSlot(IRecipeSlotView delegate, String selectedKey, int batches) {
            this.delegate = delegate;
            IJeiRuntime runtime = DirectoryRecipePlugin.getJeiRuntime();
            List<ITypedIngredient<?>> adjusted = new ArrayList<>();
            if (runtime != null) {
                delegate.getAllIngredients().forEach(ingredient -> {
                    Optional<ItemStack> itemStack = ingredient.getItemStack();
                    if (itemStack.isEmpty()) {
                        if (selectedKey.isEmpty()) {
                            adjusted.add(ingredient);
                        }
                        return;
                    }
                    ItemStack stack = itemStack.get();
                    if (!selectedKey.isEmpty() && !RecipeTreeData.ingredientKey(stack).equals(selectedKey)) {
                        return;
                    }
                    ItemStack scaled = stack.copy();
                    long count = (long) Math.max(1, stack.getCount()) * batches;
                    if (count > stack.getMaxStackSize()) {
                        return;
                    }
                    scaled.setCount((int) count);
                    runtime.getIngredientManager()
                        .createTypedIngredient(VanillaTypes.ITEM_STACK, scaled)
                        .ifPresent(adjusted::add);
                });
            }
            this.ingredients = adjusted.isEmpty()
                ? delegate.getAllIngredients().toList()
                : List.copyOf(adjusted);
        }

        @Override
        public Stream<ITypedIngredient<?>> getAllIngredients() {
            return ingredients.stream();
        }

        @Override
        public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
            return ingredients.stream().findFirst();
        }

        @Override public RecipeIngredientRole getRole() { return delegate.getRole(); }
        @Override public void drawHighlight(GuiGraphics graphics, int color) { delegate.drawHighlight(graphics, color); }
        @Override public Optional<String> getSlotName() { return delegate.getSlotName(); }
    }
}
