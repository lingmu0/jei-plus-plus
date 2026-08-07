package com.lingmu0.JeiPlusPlusMod.client.recipe_tree;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Local equivalent of AE2-Utility's requested-ingredient value object.  It
 * keeps the tree UI independent from AE2 while retaining the original tree
 * model's alternative/count semantics.
 */
public record RecipeTreeRequestedIngredient(List<ItemStack> alternatives, int count) {
    public RecipeTreeRequestedIngredient {
        alternatives = alternatives == null ? List.of() : alternatives.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
        count = Math.max(1, count);
    }

    public RecipeTreeRequestedIngredient copy() {
        return new RecipeTreeRequestedIngredient(new ArrayList<>(alternatives), count);
    }
}
