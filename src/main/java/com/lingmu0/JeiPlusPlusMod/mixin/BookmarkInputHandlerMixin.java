package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.RecipeSorterStage;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.BookmarkInputHandler;
import mezz.jei.gui.input.handlers.SameElementInputHandler;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.Collection;

/** Uses the preferred recipe only for output-slot bookmarks. */
@Mixin(value = BookmarkInputHandler.class, remap = false)
public abstract class BookmarkInputHandlerMixin {
    @Shadow @Final private BookmarkList bookmarkList;

    @Inject(method = "handleBookmark", at = @At("HEAD"), cancellable = true, remap = false)
    private void jeiPlusPlus$preferRecipeBookmark(
        UserInput input,
        IInternalKeyMappings keyBindings,
        CallbackInfoReturnable<java.util.Optional<IUserInputHandler>> cir
    ) {
        if (!JeiPlusPlusConfig.PREFER_BOOKMARKED_RECIPE_ON_INGREDIENT_BOOKMARK.get()
            || !isBookmarkedRecipeSortingEnabled()) {
            return;
        }
        IJeiRuntime runtime = Internal.getJeiRuntime();
        if (!(runtime.getRecipesGui() instanceof RecipesGui recipesGui)) {
            return;
        }

        RecipeGuiLayouts layouts = ((RecipesGuiAccessor) (Object) recipesGui).jeiPlusPlus$getLayouts();
        for (IRecipeLayoutWithButtons<?> layoutWithButtons :
            ((RecipeGuiLayoutsAccessor) (Object) layouts).jeiPlusPlus$getRecipeLayoutsWithButtons()) {
            IRecipeLayoutDrawable<?> layout = layoutWithButtons.getRecipeLayout();
            java.util.Optional<RecipeSlotUnderMouse> under = layout.getSlotUnderMouse(input.getMouseX(), input.getMouseY());
            if (under.isEmpty() || under.get().slot().isEmpty()) {
                continue;
            }
            // Do not turn an ingredient in an input slot into a recipe bookmark.
            if (under.get().slot().getRole() != RecipeIngredientRole.OUTPUT) {
                return;
            }
            RecipeBookmark<?, ?> bookmark = createBookmarkForHoveredOutput(layout,
                under.get().slot().getDisplayedIngredient()
                    .or(() -> under.get().slot().getAllIngredients().findFirst()), runtime);
            if (bookmark == null) {
                continue;
            }
            if (!input.isSimulate()) {
                bookmarkList.toggleBookmark(bookmark);
            }
            IUserInputHandler currentHandler = (IUserInputHandler) (Object) this;
            cir.setReturnValue(java.util.Optional.of(new SameElementInputHandler(currentHandler, layout::isMouseOver)));
            return;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RecipeBookmark<?, ?> createBookmarkForHoveredOutput(
        IRecipeLayoutDrawable<?> layout,
        java.util.Optional<ITypedIngredient<?>> output,
        IJeiRuntime runtime
    ) {
        if (output.isEmpty()) {
            return null;
        }
        ResourceLocation recipeUid = ((mezz.jei.api.recipe.category.IRecipeCategory) layout.getRecipeCategory())
            .getRegistryName(layout.getRecipe());
        if (recipeUid == null) {
            return null;
        }
        ITypedIngredient<?> normalized = runtime.getIngredientManager().normalizeTypedIngredient(output.get());
        return new RecipeBookmark(layout.getRecipeCategory(), layout.getRecipe(), recipeUid, normalized, true);
    }

    /**
     * JEI 19.38+ exposes RecipeSorterStage#isEnabled, while older JEI versions
     * expose the same setting through IClientConfig#getRecipeSorterStages.
     * Resolve the accessor at runtime so one build works with both APIs.
     */
    private static boolean isBookmarkedRecipeSortingEnabled() {
        IClientConfig clientConfig = Internal.getJeiClientConfigs().getClientConfig();
        try {
            Method isEnabled = RecipeSorterStage.class.getMethod("isEnabled", IClientConfig.class);
            return Boolean.TRUE.equals(isEnabled.invoke(RecipeSorterStage.BOOKMARKED, clientConfig));
        } catch (NoSuchMethodException ignored) {
            try {
                Method getRecipeSorterStages = IClientConfig.class.getMethod("getRecipeSorterStages");
                Object stages = getRecipeSorterStages.invoke(clientConfig);
                return stages instanceof Collection<?> collection
                    && collection.contains(RecipeSorterStage.BOOKMARKED);
            } catch (ReflectiveOperationException ignoredOldApi) {
                return false;
            }
        } catch (ReflectiveOperationException ignoredNewApi) {
            return false;
        }
    }
}
