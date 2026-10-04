package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.CreativeTabGridCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Do not extract inventory potion effects over the category selector. */
@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void jeiPlusPlus$hideEffectsWhileSelectorOpen(
        GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci
    ) {
        if (CreativeTabGridCompat.isAnySelectorOpen()) ci.cancel();
    }
}
