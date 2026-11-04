package com.lingmu0.JeiPlusPlusMod.mixin;

import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipeGuiTabs;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accessors used only by the JEI 19.64+ wheel hook. */
@Mixin(value = RecipesGui.class, remap = false)
public interface RecipesGuiScrollAccessor {
    @Accessor("logic")
    IRecipeGuiLogic jeiPlusPlus$scrollLogic();

    @Accessor("recipeGuiTabs")
    RecipeGuiTabs jeiPlusPlus$scrollTabs();

    @Accessor("previousRecipeCategory")
    IconButton jeiPlusPlus$scrollPreviousCategory();

    @Accessor("nextRecipeCategory")
    IconButton jeiPlusPlus$scrollNextCategory();
}
