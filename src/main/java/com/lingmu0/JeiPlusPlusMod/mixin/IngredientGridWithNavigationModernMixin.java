package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.CreativeTabGridCompat;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.IUserInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** JEI 15.48+ ingredient-grid hooks after its overlay.ingredients package move. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation", remap = false)
public abstract class IngredientGridWithNavigationModernMixin {
    @ModifyVariable(method = "updateBounds", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private ImmutableRect2i jeiPlusPlus$reserveCreativeTabRow(ImmutableRect2i availableArea) {
        return CreativeTabGridCompat.reserveRow(this, availableArea);
    }

    @Inject(method = "draw", at = @At("TAIL"), remap = false)
    private void jeiPlusPlus$drawCreativeTabs(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY,
                                               float partialTicks, CallbackInfo ci) {
        CreativeTabGridCompat.draw(this, graphics, mouseX, mouseY);
    }

    @Inject(method = "drawTooltips", at = @At("TAIL"), remap = false)
    private void jeiPlusPlus$drawCreativeTabTooltip(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY,
                                                     CallbackInfo ci) {
        CreativeTabGridCompat.drawTooltip(this, graphics, mouseX, mouseY);
    }

    @Inject(method = "createInputHandler", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$wrapCreativeTabInput(CallbackInfoReturnable<IUserInputHandler> cir) {
        cir.setReturnValue(CreativeTabGridCompat.wrapInput(this, cir.getReturnValue()));
    }
}
