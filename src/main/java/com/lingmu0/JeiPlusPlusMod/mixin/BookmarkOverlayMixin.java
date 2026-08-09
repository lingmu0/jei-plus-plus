package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeFavorites;
import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeScreen;
import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeSession;
import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeSidebarButtonController;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import mezz.jei.gui.input.handlers.ProxyInputHandler;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
    @Shadow @Final private IconButton historyButton;

    @Unique private IconButton jeiPlusPlus$treeButton;

    /**
     * JEI 19.27 uses ScreenPropertiesCache and a seven-argument constructor,
     * while JEI 19.39 uses GuiPropertiesCache and an eight-argument
     * constructor.  Keep this mixin independent of both implementation
     * details: create the extra button lazily and place it from the current
     * bounds every frame.
     */
    @Unique
    private void jeiPlusPlus$ensureTreeButton() {
        if (jeiPlusPlus$treeButton == null) {
            jeiPlusPlus$treeButton = new IconButton(new RecipeTreeSidebarButtonController());
        }
    }

    @Unique
    private void jeiPlusPlus$placeTreeButton() {
        jeiPlusPlus$ensureTreeButton();
        if (Minecraft.getInstance().screen instanceof RecipeTreeScreen) {
            jeiPlusPlus$treeButton.updateBounds(new ImmutableRect2i(
                6,
                Minecraft.getInstance().getWindow().getGuiScaledHeight() - 26,
                20,
                20
            ));
            return;
        }
        ImmutableRect2i history = historyButton.getArea();
        if (history.isEmpty()) {
            jeiPlusPlus$treeButton.updateBounds(ImmutableRect2i.EMPTY);
        } else {
            jeiPlusPlus$treeButton.updateBounds(history.moveRight(history.width() + 2));
        }
    }

    @Inject(method = "isListDisplayed", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$showTreeFavorites(CallbackInfoReturnable<Boolean> cir) {
        if (RecipeTreeFavorites.isActive() && jeiPlusPlus$isTreeButtonScreen() && contents.hasRoom()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "drawScreen", at = @At("TAIL"), remap = false)
    private void jeiPlusPlus$drawTreeButton(
        Minecraft minecraft,
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTicks,
        CallbackInfo ci
    ) {
        if (!jeiPlusPlus$isTreeButtonScreen()) {
            return;
        }
        jeiPlusPlus$placeTreeButton();
        RecipeTreeFavorites.refreshThrottled();
        jeiPlusPlus$treeButton.tick();
        jeiPlusPlus$treeButton.draw(graphics, mouseX, mouseY, partialTicks);
    }

    @Inject(method = "drawTooltips", at = @At("TAIL"), remap = false)
    private void jeiPlusPlus$drawTreeButtonTooltip(
        Minecraft minecraft,
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    ) {
        if (jeiPlusPlus$isTreeButtonScreen()) {
            jeiPlusPlus$placeTreeButton();
            jeiPlusPlus$treeButton.drawTooltips(graphics, mouseX, mouseY);
        }
    }

    @Inject(method = "createInputHandler", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$addTreeButtonInput(CallbackInfoReturnable<IUserInputHandler> cir) {
        jeiPlusPlus$ensureTreeButton();
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
            return jeiPlusPlus$isTreeButtonScreen() ? normalScreenInput : original;
        }));
    }

    @Unique
    private boolean jeiPlusPlus$isTreeButtonScreen() {
        Screen screen = Minecraft.getInstance().screen;
        return screen instanceof RecipeTreeScreen
            || screen instanceof AbstractContainerScreen<?>
            || screen instanceof mezz.jei.gui.recipes.RecipesGui;
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
