package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import com.lingmu0.JeiPlusPlusMod.JeiPlusPlus;

/** Registers the directory category with JEI when the optional dependency is present. */
@JeiPlugin
public final class DirectoryRecipePlugin implements IModPlugin {
    private static volatile DirectoryRecipeCategory category;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(JeiPlusPlus.MODID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        category = new DirectoryRecipeCategory(guiHelper);
        registration.addRecipeCategories(category);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // Directory recipes are supplied on demand through IRecipesGui.showRecipes.
    }

    public static DirectoryRecipeCategory getCategory() {
        return category;
    }
}
