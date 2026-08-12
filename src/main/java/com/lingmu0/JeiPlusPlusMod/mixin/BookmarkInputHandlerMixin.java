package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
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
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.resources.ResourceLocation;
import mezz.jei.api.recipe.RecipeIngredientRole;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;

/** Uses the recipe bookmark when bookmarking an ingredient in a recipe. */
@Mixin(value = BookmarkInputHandler.class, remap = false)
public abstract class BookmarkInputHandlerMixin {
    @Shadow @Final private BookmarkList bookmarkList;

    @Inject(
        method = {"handleBookmark", "handleIngredientBookmark"},
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void jeiPlusPlus$preferRecipeBookmark(
        UserInput input,
        IInternalKeyMappings keyBindings,
        CallbackInfoReturnable<Optional<IUserInputHandler>> cir
    ) {
        if (!JeiPlusPlusConfig.PREFER_BOOKMARKED_RECIPE_ON_INGREDIENT_BOOKMARK.get()) {
            return;
        }
        if (!isBookmarkedRecipeSortingEnabled()) {
            return;
        }

        IJeiRuntime runtime = Internal.getJeiRuntime();
        if (!(runtime.getRecipesGui() instanceof RecipesGui recipesGui)) {
            return;
        }

        RecipeGuiLayouts layouts = ((RecipesGuiAccessor) (Object) recipesGui).jeiPlusPlus$getLayouts();
        for (Object layoutWithButtons :
            ((RecipeGuiLayoutsAccessor) (Object) layouts).jeiPlusPlus$getRecipeLayoutsWithButtons()) {
            IRecipeLayoutDrawable<?> layout = getRecipeLayout(layoutWithButtons);
            if (layout == null) {
                continue;
            }
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
        /*
         * RecipeBookmark is an internal JEI value object and its constructor
         * changed between 15.20 and 15.21.  Keep the addon binary-compatible
         * with both lines instead of linking against either constructor
         * signature: 15.20 uses four arguments, 15.21 uses an output-role
         * argument, and the 19.x line uses a boolean output flag.
         */
        for (Constructor<?> constructor : RecipeBookmark.class.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            Object[] arguments;
            if (parameterTypes.length == 5 && parameterTypes[4] == RecipeIngredientRole.class) {
                arguments = new Object[] {
                    layout.getRecipeCategory(), layout.getRecipe(), recipeUid, normalized,
                    RecipeIngredientRole.OUTPUT
                };
            } else if (parameterTypes.length == 5 && parameterTypes[4] == boolean.class) {
                arguments = new Object[] {
                    layout.getRecipeCategory(), layout.getRecipe(), recipeUid, normalized, true
                };
            } else if (parameterTypes.length == 4) {
                arguments = new Object[] {
                    layout.getRecipeCategory(), layout.getRecipe(), recipeUid, normalized
                };
            } else {
                continue;
            }
            try {
                if (!constructor.trySetAccessible()) {
                    continue;
                }
                return (Optional) Optional.of(constructor.newInstance(arguments));
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // Try the next known JEI constructor shape, if present.
            }
        }
        return Optional.empty();
    }

    /**
     * JEI 19.38+ exposes RecipeSorterStage#isEnabled, while older JEI versions,
     * including the JEI 15.x line used by Minecraft 1.20.1, expose the same
     * setting through IClientConfig#getRecipeSorterStages.
     * Resolve the accessor at runtime so the bookmark behavior stays compatible
     * across JEI API generations.
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

    /**
     * JEI 15.21 stores its concrete record and exposes recipeLayout(), while
     * 15.48 stores an interface and exposes getRecipeLayout(). Avoid linking
     * against either container type so the same jar works with both.
     */
    private static IRecipeLayoutDrawable<?> getRecipeLayout(Object layoutWithButtons) {
        for (String name : new String[]{"getRecipeLayout", "recipeLayout"}) {
            try {
                Object value = layoutWithButtons.getClass().getMethod(name).invoke(layoutWithButtons);
                if (value instanceof IRecipeLayoutDrawable<?> layout) {
                    return layout;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the other JEI layout API name.
            }
        }
        return null;
    }
}
