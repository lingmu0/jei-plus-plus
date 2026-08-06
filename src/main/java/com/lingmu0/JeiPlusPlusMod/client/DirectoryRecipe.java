package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.List;

/** One page of the ingredients represented by a cycling JEI recipe slot. */
public record DirectoryRecipe(List<ITypedIngredient<?>> ingredients) {
    public DirectoryRecipe {
        ingredients = List.copyOf(ingredients);
    }
}
