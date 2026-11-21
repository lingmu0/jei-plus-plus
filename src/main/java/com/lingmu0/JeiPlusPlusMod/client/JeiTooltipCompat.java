package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.platform.Services;
import mezz.jei.common.util.SafeIngredientUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds JEI tooltips for the tree's item, fluid and chemical ingredients. */
final class JeiTooltipCompat {
    private static final String TAG_CONTENT_COMPONENT =
        "mezz.jei.library.gui.ingredients.TagContentTooltipComponent";

    private JeiTooltipCompat() {
    }

    static <T> void addIngredientTooltip(JeiTooltip tooltip, IIngredientManager manager, ITypedIngredient<T> ingredient) {
        IIngredientRenderer<T> renderer = manager.getIngredientRenderer(ingredient.getType());
        // JEI 15.48 renamed getTooltip to getRichTooltip.
        for (String name : List.of("getRichTooltip", "getTooltip")) {
            for (Method method : SafeIngredientUtil.class.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 4) {
                    try {
                        method.invoke(null, tooltip, manager, renderer, ingredient);
                        return;
                    } catch (IllegalAccessException ignored) {
                        // Try the other method name.
                    } catch (InvocationTargetException exception) {
                        return;
                    }
                }
            }
        }
    }

    static void addAlternatives(JeiTooltip tooltip, IJeiRuntime runtime, List<ItemStack> alternatives) {
        if (runtime == null || alternatives.size() < 2) return;

        IIngredientManager manager = runtime.getIngredientManager();
        List<ItemStack> displayStacks = new ArrayList<>();
        List<ITypedIngredient<?>> ingredients = new ArrayList<>();
        for (ItemStack stack : alternatives) {
            if (stack == null || stack.isEmpty()) continue;
            ItemStack display = FluidRecipeCompat.copyWithDisplay(stack);
            display.setCount(1);
            FluidRecipeCompat.toTyped(manager, display).ifPresent(typed -> {
                displayStacks.add(display);
                ingredients.add(typed);
            });
        }
        if (ingredients.size() < 2) return;

        if (ingredients.stream().allMatch(ingredient -> ingredient.getType() == VanillaTypes.ITEM_STACK)) {
            manager.getIngredientHelper(VanillaTypes.ITEM_STACK).getTagKeyEquivalent(displayStacks).ifPresent(tag -> {
                tooltip.add(Component.translatable("jei.tooltip.recipe.tag", "").withStyle(ChatFormatting.GRAY));
                tooltip.add(Services.PLATFORM.getRenderHelper().getName(tag).copy().withStyle(ChatFormatting.GRAY));
            });
        }
        createTagContent(manager, ingredients).ifPresent(tooltip::add);
    }

    static Optional<TooltipComponent> createTagContent(
        IIngredientManager manager,
        List<ITypedIngredient<?>> ingredients
    ) {
        try {
            Class<?> componentType = Class.forName(TAG_CONTENT_COMPONENT);
            for (Constructor<?> constructor : componentType.getDeclaredConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length != 2 || parameters[1] != List.class) {
                    continue;
                }
                Object rendererOrManager;
                if (parameters[0].isInstance(manager)) {
                    // Newer JEI resolves each ingredient's renderer itself.
                    rendererOrManager = manager;
                } else if (parameters[0] == IIngredientRenderer.class) {
                    // Older JEI delegates every cell to one renderer.
                    rendererOrManager = new TagIngredientRenderer(manager);
                } else {
                    continue;
                }
                constructor.trySetAccessible();
                return Optional.of((TooltipComponent) constructor.newInstance(rendererOrManager, ingredients));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // The candidate grid is optional.
        }
        return Optional.empty();
    }

    private record TagIngredientRenderer(IIngredientManager manager) implements IIngredientRenderer<ITypedIngredient<?>> {
        @Override
        public void render(GuiGraphics graphics, ITypedIngredient<?> ingredient) {
            renderIngredient(graphics, ingredient);
        }

        private <T> void renderIngredient(GuiGraphics graphics, ITypedIngredient<T> ingredient) {
            manager.getIngredientRenderer(ingredient.getType()).render(graphics, ingredient.getIngredient(), 0, 0);
        }

        @Override
        public List<Component> getTooltip(ITypedIngredient<?> ingredient, TooltipFlag flag) {
            return List.of();
        }
    }
}
