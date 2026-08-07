package com.lingmu0.JeiPlusPlusMod.client.recipe_tree;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** State shared by the tree screen and its optional child-selection view. */
public final class RecipeTreeRootContext {
    private final RecipeTreeNodeViewModel root;
    private final Screen returnScreen;
    private final Map<String, RecipeTreeRecipeViewModel> rememberedSelections = new HashMap<>();
    private boolean disableExistingPatternExpansion = true;

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

    public Component title() {
        return root.recipe().title();
    }

    public void rememberSelection(String signature, RecipeTreeRecipeViewModel recipe) {
        if (signature != null && !signature.isBlank() && recipe != null) {
            rememberedSelections.put(signature, recipe);
        }
    }

    public RecipeTreeRecipeViewModel getRememberedSelection(String signature) {
        return signature == null || signature.isBlank() ? null : rememberedSelections.get(signature);
    }

    public void forgetSelection(String signature) {
        if (signature != null && !signature.isBlank()) {
            rememberedSelections.remove(signature);
        }
    }

    public boolean disableExistingPatternExpansion() {
        return disableExistingPatternExpansion;
    }

    public void setDisableExistingPatternExpansion(boolean value) {
        disableExistingPatternExpansion = value;
    }

    public List<RecipeTreeRecipeViewModel> collectSelectedRecipes() {
        List<RecipeTreeRecipeViewModel> raw = new ArrayList<>();
        root.collectSelectedRecipes(raw);
        List<RecipeTreeRecipeViewModel> unique = new ArrayList<>();
        for (RecipeTreeRecipeViewModel candidate : raw) {
            if (unique.stream().noneMatch(existing -> existing.sameRecipeAs(candidate))) {
                unique.add(candidate);
            }
        }
        return List.copyOf(unique);
    }
}
