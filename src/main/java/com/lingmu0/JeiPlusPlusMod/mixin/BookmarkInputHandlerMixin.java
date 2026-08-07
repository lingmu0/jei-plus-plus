package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
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
import net.minecraft.resources.ResourceLocation;
import mezz.jei.api.recipe.RecipeIngredientRole;

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

            // Input slots must keep JEI's normal ingredient bookmark behavior.
            // The recipe bookmark preference only applies when the hovered slot is
            // an output slot.
            if (slotUnderMouse.get().slot().getRole() != RecipeIngredientRole.OUTPUT) {
                return;
            }

            Optional<? extends RecipeBookmark<?, ?>> recipeBookmark =
                createBookmarkForHoveredOutput(layout, slotUnderMouse.get().slot().getDisplayedIngredient()
                    .or(() -> slotUnderMouse.get().slot().getAllIngredients().findFirst()),
                    runtime);
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Optional<? extends RecipeBookmark<?, ?>> createBookmarkForHoveredOutput(
        IRecipeLayoutDrawable<?> layout,
        Optional<ITypedIngredient<?>> output,
        IJeiRuntime runtime
    ) {
        if (output.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation recipeUid = ((mezz.jei.api.recipe.category.IRecipeCategory) layout.getRecipeCategory())
            .getRegistryName(layout.getRecipe());
        if (recipeUid == null) {
            return Optional.empty();
        }
        ITypedIngredient<?> normalized = runtime.getIngredientManager().normalizeTypedIngredient(output.get());
        return (Optional) Optional.of(new RecipeBookmark(
            layout.getRecipeCategory(), layout.getRecipe(), recipeUid, normalized));
    }
}
