package com.lingmu0.JeiPlusPlusMod.mixin;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipeGuiTabs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;

/** Adds category navigation to the row of recipe-category icons. */
@Mixin(value = RecipeGuiTabs.class, remap = false)
public abstract class RecipeGuiTabsMixin {
    @Shadow @Final private IRecipeGuiLogic recipeGuiLogic;
    @Shadow private ImmutableRect2i area;

    @Inject(method = "createInputHandler", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$wrapTabInput(CallbackInfoReturnable<IUserInputHandler> cir) {
        cir.setReturnValue(new TabScrollInputHandler(this, cir.getReturnValue()));
    }

    private static final class TabScrollInputHandler implements IUserInputHandler {
        private final RecipeGuiTabsMixin owner;
        private final IUserInputHandler delegate;

        private TabScrollInputHandler(RecipeGuiTabsMixin owner, IUserInputHandler delegate) {
            this.owner = owner;
            this.delegate = delegate;
        }

        @Override
        public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
            return delegate.handleUserInput(screen, input, keyBindings);
        }

        @Override
        public void unfocus() {
            delegate.unfocus();
        }

        @Override
        public Optional<IUserInputHandler> handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
            if (scrollDeltaY != 0 && owner.area.contains(mouseX, mouseY)) {
                if (scrollDeltaY < 0) {
                    owner.recipeGuiLogic.nextRecipeCategory();
                } else {
                    owner.recipeGuiLogic.previousRecipeCategory();
                }
                return Optional.of(this);
            }
            return delegate.handleMouseScrolled(mouseX, mouseY, scrollDeltaX, scrollDeltaY);
        }

    }
}
