package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeFavorites;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-applies priority after Beyond Dimensions rebuilds its sorted index list. */
@Pseudo
@Mixin(targets = "com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu", remap = false)
public abstract class BeyondDimensionsNetMenuMixin {
    @Inject(method = {"buildIndexList", "loadSearchText", "setLines"}, at = @At("TAIL"), remap = false, require = 0)
    private void jeiPlusPlus$prioritizeAfterNativeRebuild(CallbackInfo ci) {
        RecipeTreeFavorites.applyCurrentNetworkPriority();
    }
}
