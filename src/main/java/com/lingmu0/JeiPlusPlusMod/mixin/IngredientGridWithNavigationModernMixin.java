package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.CreativeTabGridCompat;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.IUserInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** JEI 19.42+ ingredient-grid hooks after its overlay.ingredients package move. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation", remap = false)
public abstract class IngredientGridWithNavigationModernMixin {
    @ModifyVariable(method = "updateBounds", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private ImmutableRect2i jeiPlusPlus$reserveCreativeTabRow(ImmutableRect2i availableArea) {
        return CreativeTabGridCompat.reserveRow(this, availableArea);
    }

    @Inject(method = "drawForeground", at = @At("TAIL"), remap = false, require = 0)
    private void jeiPlusPlus$drawCreativeTabs(
        Minecraft minecraft,
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTicks,
        CallbackInfo ci
    ) {
        CreativeTabGridCompat.draw(this, graphics, mouseX, mouseY);
    }

    /** JEI 15.x/19.27-19.41 exposes the same pass as drawOnForeground. */
    @Inject(method = "drawOnForeground", at = @At("TAIL"), remap = false, require = 0)
    private void jeiPlusPlus$drawCreativeTabsLegacy(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    ) {
        if (jeiPlusPlus$hasModernForegroundPass()) {
            return;
        }
        CreativeTabGridCompat.draw(this, graphics, mouseX, mouseY);
    }

    @Unique
    private boolean jeiPlusPlus$hasModernForegroundPass() {
        try {
            getClass().getDeclaredMethod(
                "drawForeground",
                Minecraft.class,
                GuiGraphics.class,
                int.class,
                int.class,
                float.class
            );
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    @Inject(method = "drawTooltips", at = @At("TAIL"), remap = false)
    private void jeiPlusPlus$drawCreativeTabTooltip(
        Minecraft minecraft,
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    ) {
        CreativeTabGridCompat.drawTooltip(this, graphics, mouseX, mouseY);
    }

    @Inject(method = "createInputHandler", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$wrapCreativeTabInput(CallbackInfoReturnable<IUserInputHandler> cir) {
        cir.setReturnValue(CreativeTabGridCompat.wrapInput(this, cir.getReturnValue()));
    }
}
