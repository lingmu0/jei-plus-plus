package com.lingmu0.JeiPlusPlusMod.mixin;

import mezz.jei.gui.elements.IconButton;
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
    @Shadow @Final private IconButton nextRecipeCategory;
    @Shadow @Final private IconButton previousRecipeCategory;
    @Shadow @Final private IconButton nextPage;
    @Shadow @Final private IconButton previousPage;

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void jeiPlusPlus$scrollNavigation(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (scrollY == 0 || !insideBand(mouseX, mouseY, previousPage, nextPage)) {
            if (scrollY != 0 && insideBand(mouseX, mouseY, previousRecipeCategory, nextRecipeCategory)) {
                if (scrollY < 0) {
                    logic.nextRecipeCategory();
                } else {
                    logic.previousRecipeCategory();
                }
                cir.setReturnValue(true);
            }
            return;
        }

        if (scrollY < 0) {
            logic.nextPage();
        } else {
            logic.previousPage();
        }
        cir.setReturnValue(true);
    }

    private static boolean insideBand(double mouseX, double mouseY, IconButton left, IconButton right) {
        int leftEdge = left.getX();
        int rightEdge = right.getX() + right.getWidth();
        int top = left.getY() - 2;
        int bottom = left.getY() + left.getHeight() + 2;
        return mouseX >= leftEdge && mouseX <= rightEdge && mouseY >= top && mouseY <= bottom;
    }
}
