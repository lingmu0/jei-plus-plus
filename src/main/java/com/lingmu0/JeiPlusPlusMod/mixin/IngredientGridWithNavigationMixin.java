package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import com.lingmu0.JeiPlusPlusMod.client.CreativeTabBar;
import com.lingmu0.JeiPlusPlusMod.client.IngredientListFeatureSource;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.common.input.IInternalKeyMappings;
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

/** Draws and handles the creative-tab strip above JEI's ingredient grid. */
@Mixin(value = IngredientGridWithNavigation.class, remap = false)
public abstract class IngredientGridWithNavigationMixin {
    @Shadow @Final private IIngredientGridSource ingredientSource;
    @Shadow private ImmutableRect2i backgroundArea;

    @Shadow
    public abstract ImmutableRect2i getBackButtonArea();

    @Inject(method = "draw", at = @At("TAIL"), remap = false)
    private void jeiPlusPlus$drawCreativeTabs(
        Minecraft minecraft,
        GuiGraphics guiGraphics,
        int mouseX,
        int mouseY,
        float partialTicks,
        CallbackInfo ci
    ) {
        IngredientListFeatureSource source = jeiPlusPlus$getFeatureSource();
        if (source == null || !JeiPlusPlusConfig.CREATIVE_TAB_BAR_ENABLED.get()) {
            return;
        }
        ImmutableRect2i area = CreativeTabBar.getArea(backgroundArea, getBackButtonArea());
        CreativeTabBar.draw(source, guiGraphics, area, mouseX, mouseY);
    }

    @Inject(method = "drawTooltips", at = @At("TAIL"), remap = false)
    private void jeiPlusPlus$drawCreativeTabTooltip(
        Minecraft minecraft,
        GuiGraphics guiGraphics,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    ) {
        IngredientListFeatureSource source = jeiPlusPlus$getFeatureSource();
        if (source == null || !JeiPlusPlusConfig.CREATIVE_TAB_BAR_ENABLED.get()) {
            return;
        }
        ImmutableRect2i area = CreativeTabBar.getArea(backgroundArea, getBackButtonArea());
        CreativeTabBar.drawTooltip(source, guiGraphics, area, mouseX, mouseY);
    }

    @Inject(method = "createInputHandler", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$wrapCreativeTabInput(CallbackInfoReturnable<IUserInputHandler> cir) {
        IUserInputHandler delegate = cir.getReturnValue();
        cir.setReturnValue(new CreativeTabInputHandler(this, delegate));
    }

    @Unique
    private IngredientListFeatureSource jeiPlusPlus$getFeatureSource() {
        if (ingredientSource instanceof IngredientListFeatureSource source) {
            return source;
        }
        return null;
    }

    @Unique
    private ImmutableRect2i jeiPlusPlus$getCreativeTabArea() {
        return CreativeTabBar.getArea(backgroundArea, getBackButtonArea());
    }

    private static final class CreativeTabInputHandler implements IUserInputHandler {
        private final IngredientGridWithNavigationMixin owner;
        private final IUserInputHandler delegate;

        private CreativeTabInputHandler(IngredientGridWithNavigationMixin owner, IUserInputHandler delegate) {
            this.owner = owner;
            this.delegate = delegate;
        }

        @Override
        public Optional<IUserInputHandler> handleUserInput(
            Screen screen,
            UserInput input,
            IInternalKeyMappings keyBindings
        ) {
            IngredientListFeatureSource source = owner.jeiPlusPlus$getFeatureSource();
            if (source != null && CreativeTabBar.handleClick(source, owner.jeiPlusPlus$getCreativeTabArea(), input)) {
                return Optional.of(this);
            }
            return delegate.handleUserInput(screen, input, keyBindings);
        }

        @Override
        public void unfocus() {
            delegate.unfocus();
        }

        @Override
        public Optional<IUserInputHandler> handleMouseScrolled(
            double mouseX,
            double mouseY,
            double scrollDelta
        ) {
            IngredientListFeatureSource source = owner.jeiPlusPlus$getFeatureSource();
            if (source != null) {
                Optional<IUserInputHandler> result = CreativeTabBar.handleScroll(
                    source,
                    owner.jeiPlusPlus$getCreativeTabArea(),
                    mouseX,
                    mouseY,
                    scrollDelta,
                    this
                );
                if (result.isPresent()) {
                    return result;
                }
            }
            return delegate.handleMouseScrolled(mouseX, mouseY, scrollDelta);
        }
    }
}
