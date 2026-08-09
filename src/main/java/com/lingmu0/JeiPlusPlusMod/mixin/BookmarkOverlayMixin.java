package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeFavorites;
import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeScreen;
import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeSession;
import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeSidebarButton;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import mezz.jei.gui.input.handlers.ProxyInputHandler;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.ScreenPropertiesCache;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = BookmarkOverlay.class, remap = false)
public abstract class BookmarkOverlayMixin {
    @Shadow @Final private IngredientGridWithNavigation contents;
    @Shadow @Final private ScreenPropertiesCache screenPropertiesCache;

    @Unique private RecipeTreeSidebarButton jeiPlusPlus$treeButton;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void jeiPlusPlus$createTreeButton(
        BookmarkList bookmarkList,
        IngredientGridWithNavigation contents,
        IClientToggleState toggleState,
        IScreenHelper screenHelper,
        IInternalKeyMappings keyBindings,
        CallbackInfo ci
    ) {
        this.jeiPlusPlus$treeButton = new RecipeTreeSidebarButton();
    }

    @Inject(method = "updateBounds", at = @At("TAIL"), remap = false)
    private void jeiPlusPlus$placeTreeButton(IGuiProperties guiProperties, CallbackInfo ci) {
        int leftWidth = Math.max(0, guiProperties.getGuiLeft());
        ImmutableRect2i bookmarkArea = new ImmutableRect2i(0, 0, leftWidth, guiProperties.getScreenHeight())
            .insetBy(6);
        if (contents.hasRoom()) {
            bookmarkArea = bookmarkArea.matchWidthAndX(contents.getBackgroundArea());
        }
        bookmarkArea = bookmarkArea.keepBottom(20).keepLeft(20);
        jeiPlusPlus$treeButton.updateBounds(bookmarkArea.moveRight(22));
    }

    @Inject(method = "isListDisplayed", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$showTreeFavorites(CallbackInfoReturnable<Boolean> cir) {
        if (RecipeTreeFavorites.isActive() && screenPropertiesCache.hasValidScreen() && contents.hasRoom()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "drawScreen", at = @At("TAIL"), remap = false)
    private void jeiPlusPlus$drawTreeButton(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY,
                                             float partialTicks, CallbackInfo ci) {
        if (!jeiPlusPlus$isTreeButtonScreen()) {
            return;
        }
        if (Minecraft.getInstance().screen instanceof RecipeTreeScreen) {
            jeiPlusPlus$treeButton.updateBounds(new ImmutableRect2i(6, minecraft.getWindow().getGuiScaledHeight() - 26, 20, 20));
        }
        RecipeTreeFavorites.refreshThrottled();
        jeiPlusPlus$treeButton.tick();
        jeiPlusPlus$treeButton.draw(graphics, mouseX, mouseY, partialTicks);
    }

    @Inject(method = "drawTooltips", at = @At("TAIL"), remap = false)
    private void jeiPlusPlus$drawTreeButtonTooltip(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY,
                                                    CallbackInfo ci) {
        if (jeiPlusPlus$isTreeButtonScreen()) {
            jeiPlusPlus$treeButton.drawTooltips(graphics, mouseX, mouseY);
        }
    }

    @Inject(method = "createInputHandler", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$addTreeButtonInput(CallbackInfoReturnable<IUserInputHandler> cir) {
        IUserInputHandler original = cir.getReturnValue();
        IUserInputHandler treeButtonInput = new CombinedInputHandler(
            "JeiPlusPlusRecipeTreeButton",
            new JeiPlusPlusRightClickHandler(),
            jeiPlusPlus$treeButton.createInputHandler()
        );
        IUserInputHandler normalScreenInput = new CombinedInputHandler(
            "JeiPlusPlusRecipeTreeAndBookmarks",
            treeButtonInput,
            original
        );
        cir.setReturnValue(new ProxyInputHandler(() -> {
            if (Minecraft.getInstance().screen instanceof RecipeTreeScreen) {
                return treeButtonInput;
            }
            return screenPropertiesCache.hasValidScreen() ? normalScreenInput : original;
        }));
    }

    @Unique
    private boolean jeiPlusPlus$isTreeButtonScreen() {
        return screenPropertiesCache.hasValidScreen() || Minecraft.getInstance().screen instanceof RecipeTreeScreen;
    }

    @Unique
    private final class JeiPlusPlusRightClickHandler implements IUserInputHandler {
        @Override
        public Optional<IUserInputHandler> handleUserInput(
            Screen screen,
            UserInput input,
            IInternalKeyMappings keyBindings
        ) {
            if (input.getKey().getType() != InputConstants.Type.MOUSE
                || input.getKey().getValue() != 1
                || !jeiPlusPlus$treeButton.isMouseOver(input.getMouseX(), input.getMouseY())) {
                return Optional.empty();
            }
            if (!input.isSimulate()) {
                RecipeTreeSession.clear();
                if (screen instanceof RecipeTreeScreen treeScreen) {
                    treeScreen.onClose();
                }
            }
            return Optional.of(this);
        }
    }
}
