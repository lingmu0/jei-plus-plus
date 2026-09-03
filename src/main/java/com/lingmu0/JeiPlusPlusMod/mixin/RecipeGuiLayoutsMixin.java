package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.DirectoryIngredientElement;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.ClickableIngredientInternal;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/**
 * JEI <= 19.44 path for the multi-ingredient directory action.
 * JEI 19.51 removes getClickedIngredient from RecipeGuiLayouts, so this
 * injection is optional and the 19.51 path is handled by
 * RecipeSlotClickTargetFactoryMixin.
 */
@Mixin(value = RecipeGuiLayouts.class, remap = false)
public abstract class RecipeGuiLayoutsMixin {
    @Inject(
        method = "getClickedIngredient",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void jeiPlusPlus$directoryClick(
        RecipeSlotUnderMouse slotUnderMouse,
        CallbackInfoReturnable<Optional<IClickableIngredientInternal<?>>> cir
    ) {
        List<ITypedIngredient<?>> ingredients = slotUnderMouse.slot().getAllIngredients().toList();
        if (ingredients.size() <= 1) {
            return;
        }

        slotUnderMouse.slot().getDisplayedIngredient().ifPresent(displayed -> {
            cir.setReturnValue(Optional.of(new ClickableIngredientInternal<>(
                new DirectoryIngredientElement(displayed, ingredients),
                slotUnderMouse::isMouseOver,
                false,
                true
            )));
        });
    }
}
