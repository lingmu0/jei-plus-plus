package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeFavorites;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-applies priority after switching Integrated Terminals tabs/channels. */
@Pseudo
@Mixin(targets = "org.cyclops.integratedterminals.inventory.container.ContainerTerminalStorageBase", remap = false)
public abstract class IntegratedTerminalMenuMixin {
    @Inject(method = {"setSelectedTab", "setSelectedChannel"}, at = @At("TAIL"), remap = false, require = 0)
    private void jeiPlusPlus$prioritizeAfterTabChange(CallbackInfo ci) {
        RecipeTreeFavorites.applyCurrentNetworkPriority();
    }
}
