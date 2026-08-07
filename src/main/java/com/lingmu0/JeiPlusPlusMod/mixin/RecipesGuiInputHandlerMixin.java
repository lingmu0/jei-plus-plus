package com.lingmu0.JeiPlusPlusMod.mixin;

import java.util.Optional;

import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Handles the JEI page-number strip at its actual scroll-router level. */
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui$UserInputHandler", remap = false)
public abstract class RecipesGuiInputHandlerMixin {
    @Shadow @Final private RecipesGui recipesGui;

    @Inject(method = "handleMouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void jeiPlusPlus$pageScroll(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY,
            CallbackInfoReturnable<Optional<IUserInputHandler>> cir) {
        if (scrollDeltaY == 0 || !isPageBand(mouseX, mouseY)) {
            return;
        }
        var logic = ((RecipesGuiAccessor) (Object) recipesGui).jeiPlusPlus$getLogic();
        if (scrollDeltaY < 0) {
            logic.nextPage();
        } else {
            logic.previousPage();
        }
        cir.setReturnValue(Optional.of((IUserInputHandler) (Object) this));
    }

    private boolean isPageBand(double mouseX, double mouseY) {
        var accessor = (RecipesGuiAccessor) (Object) recipesGui;
        var previous = accessor.jeiPlusPlus$getPreviousPage();
        var next = accessor.jeiPlusPlus$getNextPage();
        int left = previous.getX() - 20;
        int right = next.getX() + next.getWidth() + 20;
        int top = Math.min(previous.getY(), next.getY()) - 18;
        int bottom = Math.max(previous.getY() + previous.getHeight(), next.getY() + next.getHeight()) + 18;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }
}
