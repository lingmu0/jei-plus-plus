package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeFavorites;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-applies priority after the legacy RS grid view changes or sorts its list. */
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.screen.grid.view.GridViewImpl", remap = false)
public abstract class RefinedStorageGridViewMixin {
    @Inject(method = {"setStacks", "sort", "forceSort"}, at = @At("TAIL"), remap = false, require = 0)
    private void jeiPlusPlus$prioritizeAfterViewUpdate(CallbackInfo ci) {
        RecipeTreeFavorites.applyCurrentNetworkPriority();
    }
}
