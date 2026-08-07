package com.lingmu0.JeiPlusPlusMod.mixin;

import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Handles the page-number strip at JEI's real scroll-router level.  JEI's
 * built-in handler intentionally treats the whole recipe window as a page
 * gesture; this injection keeps the arrows/number strip reliable even when
 * another JEI handler claims the mouse event first.
 */
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui$UserInputHandler", remap = false)
public abstract class RecipesGuiInputHandlerMixin {
    @Shadow @Final private RecipesGui recipesGui;

    @Inject(method = "handleMouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void jeiPlusPlus$pageScroll(double mouseX, double mouseY, double scrollDelta,
            CallbackInfoReturnable<Optional<IUserInputHandler>> cir) {
        if (scrollDelta == 0 || !isPageBand(mouseX, mouseY)) {
            return;
        }
        var logic = ((RecipesGuiAccessor) (Object) recipesGui).jeiPlusPlus$getLogic();
        if (scrollDelta < 0) {
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
        int left = previous.getX() - 10;
        int right = next.getX() + next.getWidth() + 10;
        int top = Math.min(previous.getY(), next.getY()) - 8;
        int bottom = Math.max(previous.getY() + previous.getHeight(), next.getY() + next.getHeight()) + 8;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }
}
