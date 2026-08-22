package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
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
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import mezz.jei.api.recipe.RecipeIngredientRole;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

/** Uses the recipe bookmark when bookmarking an ingredient in a recipe. */
@Mixin(value = BookmarkInputHandler.class, remap = false)
public abstract class BookmarkInputHandlerMixin {
    @Shadow @Final private BookmarkList bookmarkList;

    /**
     * JEI 15.x handles an output-slot bookmark in a separate
     * {@code handleRecipeBookmark} method before it reaches the ingredient
     * handler below.  If JEI's BOOKMARKED recipe-sort stage is off, route that
     * click through JEI's normal ingredient path instead of allowing the
     * recipe bookmark to be created.
     */
    @Inject(
        method = "handleRecipeBookmark",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void jeiPlusPlus$gateRecipeBookmark(
        UserInput input,
        CallbackInfoReturnable<java.util.Optional<IUserInputHandler>> cir
    ) {
        if (JeiPlusPlusConfig.PREFER_RECIPE_BOOKMARK_ON_OUTPUT.get()
            && !isJeiBookmarkedRecipeSortingEnabled()) {
            cir.setReturnValue(java.util.Optional.empty());
        }
    }

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
        IJeiRuntime runtime = Internal.getJeiRuntime();
        if (!(runtime.getRecipesGui() instanceof RecipesGui recipesGui)
            || Minecraft.getInstance().screen != recipesGui) {
            // RecipesGui remains alive after it is closed. Do not let its last
            // layout handle bookmarks clicked in the player's inventory.
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

            Optional<ITypedIngredient<?>> output = slotUnderMouse.get().slot().getDisplayedIngredient()
                .or(() -> slotUnderMouse.get().slot().getAllIngredients().findFirst());
            if (output.isEmpty()) {
                continue;
            }

            if (!input.isSimulate()) {
                if (JeiPlusPlusConfig.PREFER_RECIPE_BOOKMARK_ON_OUTPUT.get()
                    && isJeiBookmarkedRecipeSortingEnabled()) {
                    Optional<? extends RecipeBookmark<?, ?>> recipeBookmark =
                        createBookmarkForHoveredOutput(layout, output, runtime);
                    if (recipeBookmark.isPresent()) {
                        bookmarkList.toggleBookmark(recipeBookmark.get());
                    } else {
                        toggleIngredientBookmark(bookmarkList,
                            runtime.getIngredientManager().normalizeTypedIngredient(output.get()));
                    }
                } else {
                    toggleIngredientBookmark(bookmarkList,
                        runtime.getIngredientManager().normalizeTypedIngredient(output.get()));
                }
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void toggleIngredientBookmark(BookmarkList bookmarks, ITypedIngredient<?> ingredient) {
        IBookmark existing = null;
        for (Object value : bookmarks.getElements()) {
            if (!(value instanceof mezz.jei.gui.overlay.elements.IElement<?> element)) {
                continue;
            }
            Optional<IBookmark> bookmark = element.getBookmark();
            if (bookmark.isEmpty() || !isIngredientBookmark(bookmark.get())) {
                continue;
            }
            if (sameIngredient(ingredient, element.getTypedIngredient())) {
                existing = bookmark.get();
                break;
            }
        }
        if (existing != null) {
            bookmarks.remove(existing);
            return;
        }
        try {
            Method add = bookmarks.getClass().getMethod("addIngredientBookmark", ITypedIngredient.class);
            add.invoke(bookmarks, ingredient);
            return;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // JEI 15.x has no addIngredientBookmark method.
        }
        try {
            Class<?> type = Class.forName("mezz.jei.gui.bookmarks.IngredientBookmark");
            Method create = type.getMethod("create", ITypedIngredient.class, mezz.jei.api.runtime.IIngredientManager.class);
            IBookmark bookmark = (IBookmark) create.invoke(null, ingredient, Internal.getJeiRuntime().getIngredientManager());
            bookmarks.toggleBookmark(bookmark);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep JEI's normal input handling available if an older build has
            // neither helper shape.
        }
    }

    private static boolean isIngredientBookmark(IBookmark bookmark) {
        return bookmark.getClass().getName().endsWith("IngredientBookmark");
    }

    private static boolean sameIngredient(ITypedIngredient<?> first, ITypedIngredient<?> second) {
        if (first == null || second == null || !Objects.equals(first.getType(), second.getType())) {
            return false;
        }
        Object a = first.getIngredient();
        Object b = second.getIngredient();
        if (a instanceof net.minecraft.world.item.ItemStack firstStack
            && b instanceof net.minecraft.world.item.ItemStack secondStack) {
            return net.minecraft.world.item.ItemStack.matches(firstStack, secondStack);
        }
        return Objects.equals(a, b);
    }

    /**
     * Recipe bookmarks only make sense when JEI's own BOOKMARKED sorter is
     * enabled. When the player disables that stage, keep normal ingredient
     * bookmark behavior even if the JEI++ preference is enabled.
     */
    private static boolean isJeiBookmarkedRecipeSortingEnabled() {
        try {
            Object config = Internal.getJeiClientConfigs().getClientConfig();
            Object stages;
            try {
                stages = invokeNoArg(config, "getRecipeSorterStages");
            } catch (ReflectiveOperationException ignored) {
                Object value = invokeNoArg(config, "recipeSorterStages");
                stages = invokeNoArg(value, "getValue");
            }
            if (stages instanceof java.util.Collection<?> collection) {
                return collection.stream().anyMatch(BookmarkInputHandlerMixin::isBookmarkedStage);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // If a future JEI hides this config, fail closed: an ingredient
            // bookmark is safer than unexpectedly creating a recipe bookmark.
        }
        return false;
    }

    private static boolean isBookmarkedStage(Object value) {
        if (value instanceof Enum<?> enumValue) {
            return "BOOKMARKED".equals(enumValue.name());
        }
        return "BOOKMARKED".equals(String.valueOf(value));
    }

    private static Object invokeNoArg(Object target, String name) throws ReflectiveOperationException {
        if (target == null) {
            throw new NoSuchMethodException(name);
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
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
