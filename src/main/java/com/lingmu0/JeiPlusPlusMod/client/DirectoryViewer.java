package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IRecipesGui;

import java.util.ArrayList;
import java.util.List;

/** Opens the directory as normal JEI recipe pages. */
public final class DirectoryViewer {
    private static final int PAGE_SIZE = 9 * 9;

    private DirectoryViewer() {
    }

    public static void show(IRecipesGui recipesGui, List<ITypedIngredient<?>> ingredients) {
        DirectoryRecipeCategory category = DirectoryRecipePlugin.getCategory();
        if (category == null || ingredients.isEmpty()) {
            return;
        }

        List<DirectoryRecipe> pages = new ArrayList<>();
        for (int from = 0; from < ingredients.size(); from += PAGE_SIZE) {
            int to = Math.min(from + PAGE_SIZE, ingredients.size());
            pages.add(new DirectoryRecipe(ingredients.subList(from, to)));
        }
        recipesGui.showRecipes(category, pages, List.of());
    }
}
