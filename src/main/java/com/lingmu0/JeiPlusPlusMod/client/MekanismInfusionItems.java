package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Reads the effective conversion recipes from Mekanism's JEI layouts. */
final class MekanismInfusionItems {
    private record Conversion(String key, long amount) {}
    private static final Map<Conversion, List<ItemStack>> CACHE = new HashMap<>();

    private MekanismInfusionItems() {}

    static void clear() {
        CACHE.clear();
    }

    static List<ItemStack> alternatives(ITypedIngredient<?> ingredient) {
        var runtime = DirectoryRecipePlugin.getJeiRuntime();
        if (runtime == null || !MekanismChemicalIngredient.isChemical(ingredient)) return List.of();
        try {
            var conversion = new Conversion(MekanismChemicalIngredient.keyOf(ingredient.getIngredient()),
                MekanismChemicalIngredient.amountOf(ingredient.getIngredient()));
            if (conversion.key().isEmpty() || conversion.amount() <= 0) return List.of();
            var cached = CACHE.get(conversion);
            if (cached == null) {
                cached = List.copyOf(alternatives(runtime, ingredient, conversion));
                CACHE.put(conversion, cached);
            }
            return cached;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    private static <T> List<ItemStack> alternatives(IJeiRuntime runtime, ITypedIngredient<T> ingredient, Conversion conversion)
            throws ReflectiveOperationException {
        var manager = runtime.getRecipeManager();
        var focus = runtime.getJeiHelpers().getFocusFactory()
            .createFocus(RecipeIngredientRole.OUTPUT, ingredient.getType(), ingredient.getIngredient());
        var category = manager.createRecipeCategoryLookup().limitFocus(List.of(focus)).get()
            .filter(candidate -> candidate.getRecipeType().getUid().toString().equals("mekanism:chemical_conversion"))
            .findFirst().orElse(null);
        if (category == null) return List.of();
        return collect(runtime, category, focus, conversion.key(), conversion.amount());
    }

    private static <R> List<ItemStack> collect(IJeiRuntime runtime, IRecipeCategory<R> category,
                                               mezz.jei.api.recipe.IFocus<?> focus, String key, long amount)
            throws ReflectiveOperationException {
        var manager = runtime.getRecipeManager();
        var emptyFocus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        var recipes = manager.createRecipeLookup(category.getRecipeType()).limitFocus(List.of(focus)).get().toList();
        List<ItemStack> result = new ArrayList<>();
        for (R recipe : recipes) {
            var layout = manager.createRecipeLayoutDrawable(category, recipe, emptyFocus).orElse(null);
            if (layout == null) continue;
            List<IRecipeSlotView> slots = layout.getRecipeSlotsView().getSlotViews();
            long produced = 0;
            for (IRecipeSlotView slot : slots) {
                if (slot.getRole() != RecipeIngredientRole.OUTPUT) continue;
                for (var output : slot.getAllIngredients().toList()) {
                    if (MekanismChemicalIngredient.isChemical(output)
                        && key.equals(MekanismChemicalIngredient.keyOf(output.getIngredient()))) {
                        produced = Math.max(produced, MekanismChemicalIngredient.amountOf(output.getIngredient()));
                    }
                }
            }
            if (produced <= 0 || amount <= 0) continue;
            long batches = 1 + (amount - 1) / produced;
            for (IRecipeSlotView slot : slots) {
                if (slot.getRole() != RecipeIngredientRole.INPUT) continue;
                for (ItemStack stack : slot.getItemStacks().toList()) {
                    if (!stack.isEmpty() && batches <= Integer.MAX_VALUE / (long) stack.getCount()) {
                        result.add(stack.copyWithCount((int) (batches * stack.getCount())));
                    }
                }
            }
        }
        return result;
    }
}
