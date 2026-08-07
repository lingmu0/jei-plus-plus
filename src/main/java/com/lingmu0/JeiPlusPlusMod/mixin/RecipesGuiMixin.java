package com.lingmu0.JeiPlusPlusMod.mixin;

import mezz.jei.gui.elements.GuiIconButton;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the two JEI navigation rows independently mouse-wheel aware. */
@Mixin(value = RecipesGui.class, remap = false)
public abstract class RecipesGuiMixin {
    @Shadow @Final private IRecipeGuiLogic logic;
    @Shadow @Final private GuiIconButton nextRecipeCategory;
    @Shadow @Final private GuiIconButton previousRecipeCategory;
    @Shadow @Final private GuiIconButton nextPage;
    @Shadow @Final private GuiIconButton previousPage;

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void jeiPlusPlus$scrollNavigation(
        double mouseX,
        double mouseY,
        double scrollDelta,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (scrollDelta == 0 || !insidePageRow(mouseX, mouseY)) {
            if (scrollDelta != 0 && insideBand(mouseX, mouseY, previousRecipeCategory, nextRecipeCategory)) {
                if (scrollDelta < 0) {
                    logic.nextRecipeCategory();
                } else {
                    logic.previousRecipeCategory();
                }
                cir.setReturnValue(true);
            }
            return;
        }

        if (scrollDelta < 0) {
            logic.nextPage();
        } else {
            logic.previousPage();
        }
        cir.setReturnValue(true);
    }

    private static boolean insideBand(double mouseX, double mouseY, GuiIconButton left, GuiIconButton right) {
        int leftEdge = left.getX();
        int rightEdge = right.getX() + right.getWidth();
        int top = left.getY() - 2;
        int bottom = left.getY() + left.getHeight() + 2;
        return mouseX >= leftEdge && mouseX <= rightEdge && mouseY >= top && mouseY <= bottom;
    }

    /**
     * The page number is rendered between the two page arrows, but it is not a
     * widget itself.  Treat the whole second navigation row as a page control
     * so a wheel event over the number advances one complete JEI page.
     */
    private boolean insidePageRow(double mouseX, double mouseY) {
        int left = previousPage.getX() - 8;
        int right = nextPage.getX() + nextPage.getWidth() + 8;
        int top = Math.min(previousPage.getY(), nextPage.getY()) - 6;
        int bottom = Math.max(previousPage.getY() + previousPage.getHeight(),
            nextPage.getY() + nextPage.getHeight()) + 6;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }
}
