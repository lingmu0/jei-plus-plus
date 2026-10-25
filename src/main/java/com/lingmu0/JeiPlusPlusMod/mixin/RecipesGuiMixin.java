package com.lingmu0.JeiPlusPlusMod.mixin;

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

    /**
     * JEI 15.x is shipped re-obfuscated in Forge.  Include both the named
     * development method and its runtime name so a production client does
     * not fail mixin validation when one of them is absent.
     */
    @Inject(method = {"mouseClicked", "method_25402"}, at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void jeiPlusPlus$clickRecipeTreeButton(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (com.lingmu0.JeiPlusPlusMod.client.RecipeTreeOverlay.click(
            ((RecipesGuiAccessor) this).jeiPlusPlus$getLayouts(), mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = {"mouseScrolled", "method_25401"}, at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void jeiPlusPlus$scrollNavigation(
        double mouseX,
        double mouseY,
        double scrollDelta,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (scrollDelta == 0 || !insideBand(mouseX, mouseY, com.lingmu0.JeiPlusPlusMod.client.JeiReflectionCompat.readField(this, "previousRecipeCategory"), com.lingmu0.JeiPlusPlusMod.client.JeiReflectionCompat.readField(this, "nextRecipeCategory"))) {
            return;
        }
        if (scrollDelta < 0) {
            logic.nextRecipeCategory();
        } else {
            logic.previousRecipeCategory();
        }
        cir.setReturnValue(true);
    }

    private static boolean insideBand(double mouseX, double mouseY, Object left, Object right) {
        Object leftArea = com.lingmu0.JeiPlusPlusMod.client.JeiReflectionCompat.tryInvoke(left, "getArea");
        Object rightArea = com.lingmu0.JeiPlusPlusMod.client.JeiReflectionCompat.tryInvoke(right, "getArea");
        if (!(leftArea instanceof mezz.jei.common.util.ImmutableRect2i a)
            || !(rightArea instanceof mezz.jei.common.util.ImmutableRect2i b)) return false;
        return mouseX >= a.getX() && mouseX <= b.getX() + b.getWidth()
            && mouseY >= a.getY() - 2 && mouseY <= a.getY() + a.getHeight() + 2;
    }
}
