package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeFavorites;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-applies priority after the current RS repository receives or sorts data. */
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryImpl", remap = false)
public abstract class RefinedStorageRepositoryMixin {
    @Inject(method = {"update", "sort"}, at = @At("TAIL"), remap = false, require = 0)
    private void jeiPlusPlus$prioritizeAfterRepositoryUpdate(CallbackInfo ci) {
        RecipeTreeFavorites.applyCurrentNetworkPriority();
    }
}
