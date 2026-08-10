package com.lingmu0.JeiPlusPlusMod.client;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.overlay.IngredientGridTooltipHelper;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;
import mezz.jei.gui.util.FocusUtil;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * The list transformation used by the JEI ingredient filter mixin.  Reliable
 * EMI treats related variants as one expandable stack; this keeps the same
 * interaction model while letting JEI continue to own searching, sorting,
 * rendering and recipe lookups.
 */
public final class IngredientListFeatures {
    private IngredientListFeatures() {
    }

    public static List<IElement<?>> transform(
        IngredientListFeatureSource source,
        List<IElement<?>> original
    ) {
        List<IElement<?>> filtered = original;
        if (JeiPlusPlusConfig.CREATIVE_TAB_BAR_ENABLED.get() && source.jeiPlusPlus$getSelectedCreativeTab() > 0) {
            filtered = filterCreativeTab(source, original);
        }
        if (!JeiPlusPlusConfig.STACK_GROUPING_ENABLED.get()) {
            return filtered;
        }
        return groupElements(source, filtered);
    }

    private static List<IElement<?>> filterCreativeTab(
        IngredientListFeatureSource source,
        List<IElement<?>> original
    ) {
        List<net.minecraft.world.item.CreativeModeTab> tabs = source.jeiPlusPlus$getCreativeTabs();
        int selected = source.jeiPlusPlus$getSelectedCreativeTab() - 1;
        if (selected < 0 || selected >= tabs.size()) {
            return original;
        }
        net.minecraft.world.item.CreativeModeTab tab = tabs.get(selected);
        List<IElement<?>> result = new ArrayList<>();
        for (IElement<?> element : original) {
            Optional<ItemStack> itemStack = element.getTypedIngredient().getItemStack();
            if (itemStack.isPresent() && !itemStack.get().isEmpty() && tab.contains(itemStack.get())) {
                result.add(element);
            }
        }
        return List.copyOf(result);
    }

    private static List<IElement<?>> groupElements(
        IngredientListFeatureSource source,
        List<IElement<?>> original
    ) {
        List<StackGroupManager.GroupDefinition> definitions = StackGroupManager.getDefinitions();
        Map<String, GroupBuilder> groups = new LinkedHashMap<>();
        Map<Integer, String> groupAtIndex = new LinkedHashMap<>();
        // Matchers operate on the registered item, not on the stack count or
        // NBT. Cache the result so every potion/enchantment variant does not
        // re-run all tag, regex, and suffix matchers.
        Map<Item, StackGroupManager.Match> matchCache = new HashMap<>();
        for (int i = 0; i < original.size(); i++) {
            IElement<?> element = original.get(i);
            Optional<ItemStack> stack = element.getTypedIngredient().getItemStack();
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.get().getItem();
            StackGroupManager.Match match;
            if (matchCache.containsKey(item)) {
                match = matchCache.get(item);
            } else {
                match = StackGroupManager.findMatch(stack.get(), definitions);
                matchCache.put(item, match);
            }
            if (match == null) {
                continue;
            }
            String key = match.key();
            GroupBuilder builder = groups.computeIfAbsent(
                key,
                ignored -> new GroupBuilder(key, match.label())
            );
            builder.elements.add(element);
            groupAtIndex.putIfAbsent(i, key);
        }

        if (groups.isEmpty()) {
            return original;
        }

        // A group with only one matching element is not a group at all.  This
        // also prevents a mod that registers one custom variant from losing
        // its normal JEI entry.
        groups.values().removeIf(group -> group.elements.size() < 2);
        if (groups.isEmpty()) {
            return original;
        }

        List<IElement<?>> result = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        for (int i = 0; i < original.size(); i++) {
            String key = groupAtIndex.get(i);
            if (key == null || !groups.containsKey(key)) {
                result.add(original.get(i));
                continue;
            }
            if (!emitted.add(key)) {
                continue;
            }
            GroupBuilder group = groups.get(key);
            if (sourceIsExpanded(source, key)) {
                // Keep a group control in the list while expanded.  This is
                // the collapse affordance; without it the original group
                // element disappears and the user can only expand once.
                result.add(new GroupedIngredientElement(source, key, group.label, group.elements, true));
                result.addAll(group.elements);
            } else {
                result.add(new GroupedIngredientElement(source, key, group.label, group.elements, false));
            }
        }
        return List.copyOf(result);
    }

    private static boolean sourceIsExpanded(IngredientListFeatureSource source, String key) {
        // The mixin owns the expansion set.  A small optional interface keeps
        // this class usable with older JEI versions while retaining the same
        // source contract.
        return source instanceof IngredientListExpansionState state && state.jeiPlusPlus$isGroupExpanded(key);
    }

    private static final class GroupBuilder {
        private final Component label;
        private final List<IElement<?>> elements = new ArrayList<>();

        private GroupBuilder(String key, Component label) {
            this.label = label;
        }
    }

    /** Optional state interface implemented by the filter mixin. */
    public interface IngredientListExpansionState {
        boolean jeiPlusPlus$isGroupExpanded(String key);
    }

    /** A clickable JEI slot representing several related item variants. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final class GroupedIngredientElement extends IngredientElement<Object> {
        private final IngredientListFeatureSource source;
        private final String groupKey;
        private final Component label;
        private final List<IElement<?>> elements;
        private final boolean expanded;

        private GroupedIngredientElement(
            IngredientListFeatureSource source,
            String groupKey,
            Component label,
            List<IElement<?>> elements,
            boolean expanded
        ) {
            super((ITypedIngredient<Object>) (ITypedIngredient) elements.get(0).getTypedIngredient());
            this.source = source;
            this.groupKey = groupKey;
            this.label = label;
            this.elements = List.copyOf(elements);
            this.expanded = expanded;
        }

        @Override
        public boolean handleClick(UserInput input, IInternalKeyMappings keyBindings) {
            if (input.getKey().getType() == InputConstants.Type.MOUSE && input.getKey().getValue() == 0) {
                if (!input.isSimulate()) {
                    source.jeiPlusPlus$toggleGroup(groupKey);
                }
                return true;
            }
            return false;
        }

        @Override
        public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) {
            source.jeiPlusPlus$toggleGroup(groupKey);
        }

        @Override
        public void getTooltip(
            JeiTooltip tooltip,
            IngredientGridTooltipHelper tooltipHelper,
            IIngredientRenderer<Object> ingredientRenderer,
            IIngredientHelper<Object> ingredientHelper
        ) {
            tooltip.add(Component.translatable("jei_plus_plus.group.tooltip", label, elements.size()));
            super.getTooltip(tooltip, tooltipHelper, ingredientRenderer, ingredientHelper);
        }

        @Override
        public @Nullable IDrawable createRenderOverlay() {
            return new GroupCountOverlay(elements.size(), expanded);
        }
    }

    private static final class GroupCountOverlay implements IDrawable {
        private final int count;
        private final boolean expanded;

        private GroupCountOverlay(int count, boolean expanded) {
            this.count = count;
            this.expanded = expanded;
        }

        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 16;
        }

        @Override
        public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
            String label = (expanded ? "-" : "+") + count;
            var pose = guiGraphics.pose();
            pose.pushPose();
            // JEI renders item stacks with depth enabled.  Put the count in
            // a higher pose layer so it cannot be hidden by the icon below.
            pose.translate(0.0D, 0.0D, 300.0D);
            guiGraphics.drawString(Minecraft.getInstance().font, label, xOffset + 1, yOffset + 8, 0xFFFFFFFF, true);
            pose.popPose();
        }
    }
}
