package com.lingmu0.JeiPlusPlusMod.client.recipe_tree;

import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeJeiLookup;

import java.util.List;

/** Resolves all JEI recipes which can produce the selected ingredient. */
public final class RecipeTreeExpansionResolver {
    private RecipeTreeExpansionResolver() {
    }

    public static List<RecipeTreeRecipeViewModel> resolveCandidates(RecipeTreeInputViewModel input) {
        return RecipeTreeJeiLookup.findRecipesByOutput(input.displayIngredient());
    }
}
