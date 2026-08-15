package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.recipe.category.IRecipeCategory;

import java.util.Optional;

/** Carries the category of a recipe bookmark through JEI's focus lookup. */
public final class RecipeBookmarkNavigationContext {
    private static final ThreadLocal<IRecipeCategory<?>> CATEGORY = new ThreadLocal<>();

    private RecipeBookmarkNavigationContext() {
    }

    public static void showInCategoryFirst(IRecipeCategory<?> category, Runnable action) {
        IRecipeCategory<?> previous = CATEGORY.get();
        CATEGORY.set(category);
        try {
            action.run();
        } finally {
            if (previous == null) {
                CATEGORY.remove();
            } else {
                CATEGORY.set(previous);
            }
        }
    }

    public static Optional<IRecipeCategory<?>> category() {
        return Optional.ofNullable(CATEGORY.get());
    }
}
