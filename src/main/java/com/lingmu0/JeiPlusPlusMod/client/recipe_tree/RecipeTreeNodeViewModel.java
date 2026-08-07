package com.lingmu0.JeiPlusPlusMod.client.recipe_tree;

/** A recipe node and its parent chain; the chain is used for recursion protection. */
public final class RecipeTreeNodeViewModel {
    private final RecipeTreeRecipeViewModel recipe;
    private final RecipeTreeNodeViewModel parent;

    public RecipeTreeNodeViewModel(RecipeTreeRecipeViewModel recipe, RecipeTreeNodeViewModel parent) {
        this.recipe = recipe;
        this.parent = parent;
    }

    public RecipeTreeRecipeViewModel recipe() {
        return recipe;
    }

    public RecipeTreeNodeViewModel parent() {
        return parent;
    }

    public boolean containsRecipe(RecipeTreeRecipeViewModel candidate) {
        for (RecipeTreeNodeViewModel node = this; node != null; node = node.parent) {
            if (node.recipe.sameRecipeAs(candidate)) {
                return true;
            }
        }
        return false;
    }
}
