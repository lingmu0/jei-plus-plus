package com.lingmu0.JeiPlusPlusMod.client.recipe_tree;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** A JEI recipe snapshot used by the independent tree viewer. */
public final class RecipeTreeRecipeViewModel {
    private final ITypedIngredient<?> primaryOutputIngredient;
    private final ItemStack primaryOutput;
    private final int primaryOutputAmount;
    private final Component title;
    private final Component subtitle;
    private final IDrawable subtitleIcon;
    private final ResourceLocation recipeId;
    private final List<RecipeTreeInputViewModel> inputs;

    public RecipeTreeRecipeViewModel(ITypedIngredient<?> primaryOutputIngredient, ItemStack primaryOutput,
            int primaryOutputAmount, Component title, Component subtitle, IDrawable subtitleIcon,
            ResourceLocation recipeId, List<RecipeTreeInputViewModel> inputs) {
        this.primaryOutputIngredient = primaryOutputIngredient;
        this.primaryOutput = primaryOutput == null ? ItemStack.EMPTY : primaryOutput.copy();
        this.primaryOutputAmount = Math.max(1, primaryOutputAmount);
        this.title = title == null ? Component.empty() : title.copy();
        this.subtitle = subtitle == null ? Component.empty() : subtitle.copy();
        this.subtitleIcon = subtitleIcon;
        this.recipeId = recipeId;
        this.inputs = List.copyOf(new ArrayList<>(inputs));
    }

    public ITypedIngredient<?> primaryOutputIngredient() {
        return primaryOutputIngredient;
    }

    public ItemStack primaryOutput() {
        return primaryOutput.copy();
    }

    public int primaryOutputCount() {
        return primaryOutputAmount;
    }

    public Component title() {
        return title.copy();
    }

    public Component subtitle() {
        return subtitle.copy();
    }

    public IDrawable subtitleIcon() {
        return subtitleIcon;
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    public List<RecipeTreeInputViewModel> inputs() {
        return inputs;
    }

    public boolean sameRecipeAs(RecipeTreeRecipeViewModel other) {
        if (recipeId != null && other.recipeId != null) {
            return recipeId.equals(other.recipeId);
        }
        return ItemStack.isSameItem(primaryOutput, other.primaryOutput)
                && title.getString().equals(other.title.getString());
    }
}
