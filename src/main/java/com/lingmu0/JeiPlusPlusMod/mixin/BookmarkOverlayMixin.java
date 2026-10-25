package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import com.lingmu0.JeiPlusPlusMod.client.*;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.lang.reflect.*;
import java.util.*;

/** Overlay button shared by the legacy input handlers and new interaction targets. */
@Mixin(targets = "mezz.jei.gui.overlay.bookmarks.BookmarkOverlay", remap = false)
public abstract class BookmarkOverlayMixin {
    @Shadow @Final private BookmarkList bookmarkList;
    @Unique private ImmutableRect2i jeiPlusPlus$area = ImmutableRect2i.EMPTY;

    @Unique private boolean jeiPlusPlus$visible() {
        Screen screen = Minecraft.getInstance().screen;
        return JeiPlusPlusConfig.RECIPE_TREE_ENABLED.get() && (screen instanceof RecipeTreeScreen
            || screen instanceof AbstractContainerScreen<?> || screen instanceof mezz.jei.gui.recipes.RecipesGui);
    }

    @Unique private void jeiPlusPlus$place() {
        RecipeTreeFavorites.bind(bookmarkList);
        Object button = JeiReflectionCompat.readField(this, "historyButton");
        Object bounds = button == null ? null : JeiReflectionCompat.tryInvoke(button, "getArea");
        if (!(bounds instanceof ImmutableRect2i)) bounds = JeiReflectionCompat.readField(button, "area");
        if (Minecraft.getInstance().screen instanceof RecipeTreeScreen) {
            jeiPlusPlus$area = new ImmutableRect2i(6, Minecraft.getInstance().getWindow().getGuiScaledHeight() - 26, 20, 20);
        } else if (bounds instanceof ImmutableRect2i area && !area.isEmpty()) {
            jeiPlusPlus$area = area.moveRight(area.getWidth() + 2);
        } else {
            jeiPlusPlus$area = new ImmutableRect2i(28, Minecraft.getInstance().getWindow().getGuiScaledHeight() - 26, 20, 20);
        }
    }

    @Inject(method = "isListDisplayed", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$list(CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().screen instanceof RecipeTreeScreen) cir.setReturnValue(false);
        else if (jeiPlusPlus$visible() && RecipeTreeFavorites.isActive()) cir.setReturnValue(true);
    }

    @Inject(method = "drawForeground", at = @At("TAIL"), remap = false, require = 0)
    private void jeiPlusPlus$draw(Minecraft mc, GuiGraphics graphics, int x, int y, float partial, CallbackInfo ci) {
        jeiPlusPlus$render(graphics, x, y);
    }

    @Inject(method = "drawScreen", at = @At("TAIL"), remap = false, require = 0)
    private void jeiPlusPlus$drawLegacy(Minecraft mc, GuiGraphics graphics, int x, int y, float partial, CallbackInfo ci) {
        if (!JeiReflectionCompat.hasMethod(this, "drawForeground")) jeiPlusPlus$render(graphics, x, y);
    }

    @Unique private void jeiPlusPlus$render(GuiGraphics graphics, int x, int y) {
        if (!jeiPlusPlus$visible()) return;
        jeiPlusPlus$place();
        if (jeiPlusPlus$area.isEmpty()) return;
        int left = jeiPlusPlus$area.getX(), top = jeiPlusPlus$area.getY();
        graphics.fill(left, top, left + 20, top + 20, 0xFF101010);
        graphics.fill(left + 1, top + 1, left + 19, top + 19,
            jeiPlusPlus$area.contains(x, y) ? 0xFF777777 : 0xFF444444);
        RecipeTreeIcons.drawGlobalTree(graphics, left + 1, top + 1);
    }

    @Inject(method = "drawTooltips", at = @At("HEAD"), cancellable = true, remap = false)
    private void jeiPlusPlus$tooltip(Minecraft mc, GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        if (CreativeTabGridCompat.isAnySelectorOpen()) { ci.cancel(); return; }
        if (jeiPlusPlus$visible() && jeiPlusPlus$area.contains(x, y)) {
            graphics.renderTooltip(mc.font, List.of(Component.translatable("jei_plus_plus.recipe_tree.global_button"),
                Component.translatable("jei_plus_plus.recipe_tree.global_button.clear")), Optional.empty(), x, y);
        }
    }

    @Inject(method = "createInputHandler", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$input(CallbackInfoReturnable<Object> cir) {
        Object original = cir.getReturnValue();
        Object tree = JeiReflectionCompat.customInputHandler((proxy, method, args) -> {
            if (method.getName().equals("toString")) return "JEI++ tree button";
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("unfocus")) return null;
            if (!method.getName().equals("handleUserInput") || !jeiPlusPlus$visible()) return Optional.empty();
            Object input = JeiReflectionCompat.inputArgument(args);
            var key = JeiReflectionCompat.inputKey(input);
            jeiPlusPlus$place();
            if (key.getType() != com.mojang.blaze3d.platform.InputConstants.Type.MOUSE
                || key.getValue() > 1 || !jeiPlusPlus$area.contains(
                    JeiReflectionCompat.inputMouseX(input), JeiReflectionCompat.inputMouseY(input))) return Optional.empty();
            if (!JeiReflectionCompat.isInputSimulated(input)) {
                Screen screen = Minecraft.getInstance().screen;
                if (key.getValue() == 1) RecipeTreeSession.clear();
                if (screen instanceof RecipeTreeScreen treeScreen) treeScreen.onClose();
                else if (key.getValue() == 0) RecipeTreeScreen.openCurrent(screen);
            }
            return Optional.of(proxy);
        });
        Object combined = JeiReflectionCompat.combineInputHandlers("JEI++ bookmarks", tree, original);
        cir.setReturnValue(JeiReflectionCompat.proxyInputHandler(() ->
            Minecraft.getInstance().screen instanceof RecipeTreeScreen ? tree : combined));
    }
}
