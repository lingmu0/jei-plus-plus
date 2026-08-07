package com.lingmu0.JeiPlusPlusMod.client;

import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeNodeViewModel;
import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeRootContext;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opens the standalone tree screen from any hovered JEI recipe layout. */
public final class RecipeTreeOpenHelper {
    private RecipeTreeOpenHelper() {
    }

    public static void openFromLayout(IRecipeLayoutDrawable<?> layout, Screen returnScreen) {
        if (layout == null || DirectoryRecipePlugin.getJeiRuntime() == null) {
            return;
        }
        var rootRecipe = RecipeTreeJeiLookup.createRootSnapshot(layout.getRecipe(), layout.getRecipeSlotsView(),
                layout.getRecipeCategory());
        Minecraft.getInstance().setScreen(new RecipeTreeScreen(
                new RecipeTreeRootContext(new RecipeTreeNodeViewModel(rootRecipe, null), returnScreen)));
    }
}
