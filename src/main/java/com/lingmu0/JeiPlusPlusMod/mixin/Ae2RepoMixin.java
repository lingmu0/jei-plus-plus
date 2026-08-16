package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeFavorites;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-applies the recipe-tree partition after AE2 rebuilds its terminal view. */
@Pseudo
@Mixin(targets = "appeng.client.gui.me.common.Repo", remap = false)
public abstract class Ae2RepoMixin {
    @Inject(method = "updateView", at = @At("TAIL"), remap = false, require = 0)
    private void jeiPlusPlus$prioritizeAfterViewUpdate(CallbackInfo ci) {
        RecipeTreeFavorites.applyCurrentNetworkPriority();
    }
}
