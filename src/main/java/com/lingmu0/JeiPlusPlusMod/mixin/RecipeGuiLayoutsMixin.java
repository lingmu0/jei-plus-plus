package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.DirectoryIngredientElement;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.ClickableIngredientInternal;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/** Adds a directory action to cycling/multi-ingredient recipe slots. */
@Mixin(value = RecipeGuiLayouts.class, remap = false)
public abstract class RecipeGuiLayoutsMixin {
    @Inject(method = "getWidth", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$reserveRecipeTreeWidth(CallbackInfoReturnable<Integer> cir) {
        int extra = com.lingmu0.JeiPlusPlusMod.client.RecipeTreeOverlay.extraWidth(
            (RecipeGuiLayouts) (Object) this,
            cir.getReturnValue()
        );
        if (extra > 0) {
            cir.setReturnValue(cir.getReturnValue() + extra);
        }
    }

    @Inject(method = "draw", at = @At("TAIL"), remap = false)
    private void jeiPlusPlus$drawRecipeTreeButton(
        GuiGraphics guiGraphics,
        int mouseX,
        int mouseY,
        CallbackInfoReturnable<Optional<IRecipeLayoutDrawable<?>>> cir
    ) {
        com.lingmu0.JeiPlusPlusMod.client.RecipeTreeOverlay.draw((RecipeGuiLayouts) (Object) this, guiGraphics, mouseX, mouseY);
    }

    @Inject(method = "getClickedIngredient", at = @At("HEAD"), cancellable = true, remap = false)
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
