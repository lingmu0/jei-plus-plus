package com.lingmu0.JeiPlusPlusMod.client.recipe_tree;

import net.minecraft.client.gui.screens.Screen;

/** State shared by the tree screen and its optional child-selection view. */
public final class RecipeTreeRootContext {
    private final RecipeTreeNodeViewModel root;
    private final Screen returnScreen;

    public RecipeTreeRootContext(RecipeTreeNodeViewModel root, Screen returnScreen) {
        this.root = root;
        this.returnScreen = returnScreen;
    }

    public RecipeTreeNodeViewModel root() {
        return root;
    }

    public Screen returnScreen() {
        return returnScreen;
    }
}
