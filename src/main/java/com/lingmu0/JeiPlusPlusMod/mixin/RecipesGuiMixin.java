package com.lingmu0.JeiPlusPlusMod.mixin;

import mezz.jei.gui.elements.GuiIconButton;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.common.util.ImmutableRect2i;
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

    /**
     * JEI 15.x is shipped re-obfuscated in Forge.  Include both the named
     * development method and its runtime name so a production client does
     * not fail mixin validation when one of them is absent.
     */
    @Inject(method = {"mouseClicked", "m_6375_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void jeiPlusPlus$clickRecipeTreeButton(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (com.lingmu0.JeiPlusPlusMod.client.RecipeTreeOverlay.click(
            ((RecipesGuiAccessor) this).jeiPlusPlus$getLayouts(), mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = {"mouseScrolled", "m_6050_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void jeiPlusPlus$scrollNavigation(
        double mouseX,
        double mouseY,
        double scrollDelta,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (scrollDelta == 0 || !insideBand(mouseX, mouseY, previousRecipeCategory, nextRecipeCategory)) {
            return;
        }
        if (scrollDelta < 0) {
            logic.nextRecipeCategory();
        } else {
            logic.previousRecipeCategory();
        }
        cir.setReturnValue(true);
    }

    private static boolean insideBand(double mouseX, double mouseY, GuiIconButton left, GuiIconButton right) {
        ImmutableRect2i leftArea = left.getArea();
        ImmutableRect2i rightArea = right.getArea();
        int leftEdge = leftArea.getX();
        int rightEdge = rightArea.getX() + rightArea.getWidth();
        int top = leftArea.getY() - 2;
        int bottom = leftArea.getY() + leftArea.getHeight() + 2;
        return mouseX >= leftEdge && mouseX <= rightEdge && mouseY >= top && mouseY <= bottom;
    }
}
