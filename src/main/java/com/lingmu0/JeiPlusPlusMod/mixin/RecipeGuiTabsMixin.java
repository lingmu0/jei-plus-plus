package com.lingmu0.JeiPlusPlusMod.mixin;

import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.PageNavigation;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipeGuiTabs;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Adds category navigation to the row of recipe-category icons. */
@Mixin(value = RecipeGuiTabs.class, remap = false)
public abstract class RecipeGuiTabsMixin {
    @Shadow @Final private IRecipeGuiLogic recipeGuiLogic;
    @Shadow private ImmutableRect2i area;
    @Shadow @Final private PageNavigation pageNavigation;
    @Shadow public abstract boolean nextPage();
    @Shadow public abstract boolean previousPage();

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
        public Optional<IUserInputHandler> handleMouseScrolled(double mouseX, double mouseY, double scrollDelta) {
            if (scrollDelta != 0 && owner.isPageNavigationBand(mouseX, mouseY)) {
                if (scrollDelta < 0) {
                    owner.nextPage();
                } else {
                    owner.previousPage();
                }
                return Optional.of(this);
            }
            if (scrollDelta != 0 && owner.area.contains(mouseX, mouseY)) {
                if (scrollDelta < 0) {
                    owner.recipeGuiLogic.nextRecipeCategory();
                } else {
                    owner.recipeGuiLogic.previousRecipeCategory();
                }
                return Optional.of(this);
            }
            return delegate.handleMouseScrolled(mouseX, mouseY, scrollDelta);
        }
    }

    /**
     * JEI's top page-number strip belongs to PageNavigation, not to the
     * RecipesGui page buttons.  Use its actual button bounds and include the
     * unbuttoned number area between them.
     */
    private boolean isPageNavigationBand(double mouseX, double mouseY) {
        ImmutableRect2i back = pageNavigation.getBackButtonArea();
        ImmutableRect2i next = pageNavigation.getNextButtonArea();
        if (back.isEmpty() || next.isEmpty()) {
            return false;
        }
        int left = Math.min(back.getX(), next.getX()) - 2;
        int right = Math.max(back.getX() + back.getWidth(), next.getX() + next.getWidth()) + 2;
        int top = Math.min(back.getY(), next.getY()) - 2;
        int bottom = Math.max(back.getY() + back.getHeight(), next.getY() + next.getHeight()) + 2;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }
}
