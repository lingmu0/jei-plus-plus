package com.lingmu0.JeiPlusPlusMod.client;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import com.lingmu0.JeiPlusPlusMod.mixin.BookmarkListAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.platform.Services;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkType;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.IngredientGridTooltipHelper;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.util.FocusUtil;
import mezz.jei.library.gui.ingredients.TagContentTooltipComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** EMI-style, non-persistent tree products and costs appended to JEI bookmarks. */
public final class RecipeTreeFavorites {
    private static BookmarkList bookmarkList;
    private static List<IElement<?>> elements = List.of();
    private static Set<String> requiredIngredientKeys = Set.of();
    private static Set<String> intermediateIngredientKeys = Set.of();
    private static String signature = "";
    private static int lastRefreshTick = Integer.MIN_VALUE;

    private RecipeTreeFavorites() {
    }

    public static void bind(BookmarkList list) {
        bookmarkList = list;
    }

    public static List<IElement<?>> elements() {
        return elements;
    }

    public static boolean isActive() {
        RecipeTreeData.Tree tree = RecipeTreeSession.craftingTree();
        return tree != null && tree.craftingMode() && !elements.isEmpty();
    }

    public static boolean isRequired(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && isActive()
            && requiredIngredientKeys.contains(RecipeTreeData.ingredientKey(stack));
    }

    public static boolean isIntermediate(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && isActive()
            && intermediateIngredientKeys.contains(RecipeTreeData.ingredientKey(stack));
    }

    public static void refreshThrottled() {
        var player = Minecraft.getInstance().player;
        int tick = player == null ? 0 : player.tickCount;
        if (tick == lastRefreshTick || (tick & 3) != 0) {
            return;
        }
        lastRefreshTick = tick;
        refreshNow();
    }

    public static void refreshNow() {
        RecipeTreeData.Tree tree = RecipeTreeSession.craftingTree();
        IJeiRuntime runtime = DirectoryRecipePlugin.getJeiRuntime();
        List<IElement<?>> next = new ArrayList<>();
        Set<String> nextRequired = new HashSet<>();
        Set<String> nextIntermediate = new HashSet<>();
        StringBuilder nextSignature = new StringBuilder();

        if (tree != null && tree.craftingMode() && runtime != null) {
            String rootKey = tree.root().ingredientKey();
            for (RecipeTreeData.CraftStep step : tree.craftingSteps()) {
                String stepKey = RecipeTreeData.ingredientKey(step.stack());
                long owned = RecipeTreeData.inventoryAmount(step.alternatives());
                if (!stepKey.equals(rootKey)) {
                    for (ItemStack alternative : step.alternatives()) {
                        nextIntermediate.add(RecipeTreeData.ingredientKey(alternative));
                    }
                }
                createTyped(runtime, step.stack()).ifPresent(typed -> {
                    SyntheticBookmark bookmark = new SyntheticBookmark(typed, step, null, !stepKey.equals(rootKey));
                    next.add(bookmark.getElement());
                    nextSignature.append('R').append(step.recipe().key())
                        .append(':').append(owned).append(':').append(step.total())
                        .append(':').append(step.alternatives().stream()
                            .map(RecipeTreeData::ingredientKey).sorted().toList())
                        .append(':').append(step.selectedInputs()).append(';');
                });
            }

            RecipeTreeData.Analysis analysis = tree.analyze();
            for (RecipeTreeData.Cost cost : analysis.costs()) {
                long owned = RecipeTreeData.inventoryAmount(cost.alternatives());
                for (ItemStack alternative : cost.alternatives()) {
                    nextRequired.add(RecipeTreeData.ingredientKey(alternative));
                }
                createTyped(runtime, cost.stack()).ifPresent(typed -> {
                    SyntheticBookmark bookmark = new SyntheticBookmark(typed, null, cost, false);
                    next.add(bookmark.getElement());
                    nextSignature.append('C').append(cost.alternatives().stream()
                            .map(RecipeTreeData::ingredientKey).sorted().toList())
                        .append(':').append(owned).append(':').append(cost.required()).append(';');
                });
            }
        }

        String newSignature = nextSignature.toString();
        boolean changed = !newSignature.equals(signature);
        elements = List.copyOf(next);
        requiredIngredientKeys = Set.copyOf(nextRequired);
        intermediateIngredientKeys = Set.copyOf(nextIntermediate);
        signature = newSignature;
        if (changed && bookmarkList != null) {
            ((BookmarkListAccessor) (Object) bookmarkList).jeiPlusPlus$notifyListenersOfChange();
        }
    }

    private static Optional<ITypedIngredient<ItemStack>> createTyped(IJeiRuntime runtime, ItemStack source) {
        ItemStack stack = source.copy();
        stack.setCount(1);
        return runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false);
    }

    private static String amount(long value) {
        if (value >= 1_000_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fB", value / 1_000_000_000.0).replace(".0", "");
        }
        if (value >= 1_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0).replace(".0", "");
        }
        if (value >= 10_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fK", value / 1_000.0).replace(".0", "");
        }
        return Long.toString(value);
    }

    private static final class SyntheticBookmark implements IBookmark {
        private final SyntheticElement element;
        private final BookmarkType type;

        private SyntheticBookmark(
            ITypedIngredient<ItemStack> typed,
            RecipeTreeData.CraftStep step,
            RecipeTreeData.Cost cost,
            boolean intermediate
        ) {
            this.type = step == null ? BookmarkType.INGREDIENT : BookmarkType.RECIPE;
            this.element = new SyntheticElement(this, typed, step, cost, intermediate);
        }

        @Override
        public BookmarkType getType() {
            return type;
        }

        @Override
        public IElement<?> getElement() {
            return element;
        }

        @Override
        public boolean isVisible() {
            return true;
        }

        @Override
        public void setVisible(boolean visible) {
        }
    }

    private static final class SyntheticElement implements IElement<ItemStack> {
        private final IBookmark bookmark;
        private final ITypedIngredient<ItemStack> typed;
        private final RecipeTreeData.CraftStep step;
        private final RecipeTreeData.Cost cost;
        private final boolean intermediate;

        private SyntheticElement(
            IBookmark bookmark,
            ITypedIngredient<ItemStack> typed,
            RecipeTreeData.CraftStep step,
            RecipeTreeData.Cost cost,
            boolean intermediate
        ) {
            this.bookmark = bookmark;
            this.typed = typed;
            this.step = step;
            this.cost = cost;
            this.intermediate = intermediate;
        }

        @Override
        public ITypedIngredient<ItemStack> getTypedIngredient() {
            return typed;
        }

        @Override
        public Optional<IBookmark> getBookmark() {
            return Optional.of(bookmark);
        }

        @Override
        public IDrawable createRenderOverlay() {
            long remaining = Math.max(0, total() - owned());
            int color = step == null
                ? (remaining == 0 ? 0xFF55FF55 : 0xFFFF5555)
                : (remaining == 0 ? 0xFF55FF55 : (intermediate ? 0xFFFFAA33 : 0xFF55CCFF));
            return new AmountOverlay(remaining, color);
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) {
            if (step != null) {
                IRecipeCategory category = step.recipe().category();
                recipesGui.showRecipes(category, List.of(step.recipe().recipe()), List.<IFocus<?>>of());
                return;
            }
            List<IFocus<?>> focuses = focusUtil.createFocuses(typed, roles);
            recipesGui.show(focuses);
        }

        @Override
        public boolean handleClick(UserInput input, IInternalKeyMappings keyBindings) {
            if (step == null
                || input.getKey().getType() != InputConstants.Type.MOUSE
                || input.getKey().getValue() != 0) {
                return false;
            }
            // A normal click only transfers this recipe's direct inputs.  The
            // recursive plan is an explicit Shift-click action, including for
            // intermediate products whose direct inputs are not in the inventory.
            boolean recursive = JeiPlusPlusConfig.AUTOMATIC_CRAFTING_ENABLED.get()
                && net.minecraft.client.gui.screens.Screen.hasShiftDown();
            if (input.isSimulate()) {
                return RecipeTreeTransfer.canTransfer(step, recursive);
            }
            return RecipeTreeTransfer.transfer(step, recursive);
        }

        @Override
        public void getTooltip(
            JeiTooltip tooltip,
            IngredientGridTooltipHelper tooltipHelper,
            IIngredientRenderer<ItemStack> ingredientRenderer,
            IIngredientHelper<ItemStack> ingredientHelper
        ) {
            tooltipHelper.getIngredientTooltip(tooltip, typed, ingredientRenderer, ingredientHelper);
            List<ItemStack> alternatives = tooltipAlternatives();
            if (alternatives.size() > 1) {
                ingredientHelper.getTagKeyEquivalent(alternatives).ifPresent(tagKey -> {
                    tooltip.add(Component.translatable("jei.tooltip.recipe.tag", "")
                        .withStyle(ChatFormatting.GRAY));
                    tooltip.add(Services.PLATFORM.getRenderHelper().getName(tagKey)
                        .copy().withStyle(ChatFormatting.GRAY));
                });
                tooltip.add(new TagContentTooltipComponent<>(ingredientRenderer, alternatives));
            }
            long owned = owned();
            long total = total();
            long remaining = Math.max(0, total - owned);
            tooltip.add(Component.translatable("jei_plus_plus.recipe_tree.favorite.remaining", amount(remaining))
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("jei_plus_plus.recipe_tree.favorite.obtained", amount(owned), amount(total))
                .withStyle(ChatFormatting.GRAY));
            if (step != null) {
                String clickKey = intermediate
                    ? "jei_plus_plus.recipe_tree.favorite.click_intermediate"
                    : "jei_plus_plus.recipe_tree.favorite.click";
                tooltip.add(Component.translatable(clickKey).withStyle(ChatFormatting.AQUA));
                if (JeiPlusPlusConfig.AUTOMATIC_CRAFTING_ENABLED.get()) {
                    tooltip.add(Component.translatable("jei_plus_plus.recipe_tree.favorite.shift_click")
                        .withStyle(ChatFormatting.AQUA));
                }
            }
        }

        private List<ItemStack> tooltipAlternatives() {
            List<ItemStack> source = step == null
                ? (cost == null ? List.of(typed.getIngredient()) : cost.alternatives())
                : step.alternatives();
            return source.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(stack -> {
                    ItemStack copy = stack.copy();
                    copy.setCount(1);
                    return copy;
                })
                .toList();
        }

        private long owned() {
            return step == null
                ? RecipeTreeData.inventoryAmount(cost.alternatives())
                : RecipeTreeData.inventoryAmount(step.alternatives());
        }

        private long total() {
            return step == null ? cost.required() : step.total();
        }

        @Override
        public boolean isVisible() {
            return true;
        }
    }

    private record AmountOverlay(long value, int color) implements IDrawable {
        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 16;
        }

        @Override
        public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
            String text = amount(value);
            var font = Minecraft.getInstance().font;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.fill(xOffset, yOffset, xOffset + 2, yOffset + 2, color);
            graphics.drawString(font, text, xOffset + 17 - font.width(text), yOffset + 9, color, true);
            graphics.pose().popPose();
        }
    }
}
