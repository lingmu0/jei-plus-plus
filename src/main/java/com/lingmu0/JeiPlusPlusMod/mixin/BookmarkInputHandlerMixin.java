package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.common.config.RecipeSorterStage;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.BookmarkInputHandler;
import mezz.jei.gui.input.handlers.SameElementInputHandler;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Uses the recipe bookmark when bookmarking an ingredient in a recipe. */
@Mixin(value = BookmarkInputHandler.class, remap = false)
public abstract class BookmarkInputHandlerMixin {
    @Shadow @Final private BookmarkList bookmarkList;

    @Inject(method = "handleBookmark", at = @At("HEAD"), cancellable = true, remap = false)
    private void jeiPlusPlus$preferRecipeBookmark(
        UserInput input,
        IInternalKeyMappings keyBindings,
        CallbackInfoReturnable<Optional<IUserInputHandler>> cir
    ) {
        if (!JeiPlusPlusConfig.PREFER_BOOKMARKED_RECIPE_ON_INGREDIENT_BOOKMARK.get()) {
            return;
        }
        if (!Internal.getJeiClientConfigs().getClientConfig().getRecipeSorterStages().contains(RecipeSorterStage.BOOKMARKED)) {
            return;
        }

        IJeiRuntime runtime = Internal.getJeiRuntime();
        if (!(runtime.getRecipesGui() instanceof RecipesGui recipesGui)) {
            return;
        }

        RecipeGuiLayouts layouts = ((RecipesGuiAccessor) (Object) recipesGui).jeiPlusPlus$getLayouts();
        for (RecipeLayoutWithButtons<?> layoutWithButtons :
            ((RecipeGuiLayoutsAccessor) (Object) layouts).jeiPlusPlus$getRecipeLayoutsWithButtons()) {
            IRecipeLayoutDrawable<?> layout = layoutWithButtons.recipeLayout();
            Optional<RecipeSlotUnderMouse> slotUnderMouse = layout.getSlotUnderMouse(input.getMouseX(), input.getMouseY());
            if (slotUnderMouse.isEmpty() || slotUnderMouse.get().slot().isEmpty()) {
                continue;
            }

            Optional<? extends RecipeBookmark<?, ?>> recipeBookmark =
                RecipeBookmark.create(layout, runtime.getIngredientManager());
            if (recipeBookmark.isEmpty()) {
                continue;
            }

            if (!input.isSimulate()) {
                bookmarkList.toggleBookmark(recipeBookmark.get());
            }
            IUserInputHandler currentHandler = (IUserInputHandler) (Object) this;
            cir.setReturnValue(Optional.of(new SameElementInputHandler(currentHandler, layout::isMouseOver)));
            return;
        }
    }
}
