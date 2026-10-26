package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.CreativeTabGridCompat;
import mezz.jei.gui.overlay.IngredientListOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draw the picker after recipes, containers, overlays and native interactive tooltip layers. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.events.GuiEventHandler", remap = false)
public abstract class JeiModalRenderMixin {
    @Shadow @Final private IngredientListOverlay ingredientListOverlay;

    @Group(name = "jeiPlusPlus$modalPass", min = 1)
    @Inject(method = "drawForScreenForeground", at = @At("TAIL"), remap = false, require = 0)
    private void jeiPlusPlus$modal(Screen screen, GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        CreativeTabGridCompat.drawModal(ingredientListOverlay, graphics, x, y);
    }

    @Group(name = "jeiPlusPlus$modalPass", min = 1)
    @Inject(method = "onDrawScreenPost", at = @At("TAIL"), remap = false, require = 0)
    private void jeiPlusPlus$legacyModal(Screen screen, GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        if (!com.lingmu0.JeiPlusPlusMod.client.JeiReflectionCompat.hasMethod(this, "drawForScreenForeground")) {
            CreativeTabGridCompat.drawModal(ingredientListOverlay, graphics, x, y);
        }
    }
}
