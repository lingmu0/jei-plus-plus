package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeFavorites;
import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeTransfer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void jeiPlusPlus$updateRecipeTreeCrafting(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo ci
    ) {
        RecipeTreeFavorites.refreshThrottled();
        RecipeTreeTransfer.tick((Screen) (Object) this);
    }

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void jeiPlusPlus$highlightRequiredInventoryStack(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if (!(slot.container instanceof Inventory) || slot.getItem().isEmpty()) {
            return;
        }
        boolean intermediate = RecipeTreeFavorites.isIntermediate(slot.getItem());
        boolean required = RecipeTreeFavorites.isRequired(slot.getItem());
        if (!intermediate && !required) {
            return;
        }
        int fill = intermediate ? 0x44FF2222 : 0x3300BBFF;
        int border = intermediate ? 0xDDFF5555 : 0xCC55DDFF;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, fill);
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 1, border);
        graphics.fill(slot.x, slot.y + 15, slot.x + 16, slot.y + 16, border);
        graphics.fill(slot.x, slot.y, slot.x + 1, slot.y + 16, border);
        graphics.fill(slot.x + 15, slot.y, slot.x + 16, slot.y + 16, border);
        graphics.pose().popPose();
    }
}
