package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeInputViewModel;
import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeInputViewModel.DisplayOption;
import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeRecipeViewModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JEI runtime lookup and immutable snapshots for the tree UI. */
public final class RecipeTreeJeiLookup {
    private RecipeTreeJeiLookup() {
    }

    public static RecipeTreeRecipeViewModel createRootSnapshot(Object recipe, IRecipeSlotsView slots,
            IRecipeCategory<?> category) {
        return createSnapshot(slots, category.getTitle(), category.getTitle(), category.getIcon(),
                registryName(category, recipe));
    }

    public static List<RecipeTreeRecipeViewModel> findRecipesByOutput(ITypedIngredient<?> output) {
        if (output == null) {
            return List.of();
        }
        IJeiRuntime runtime = DirectoryRecipePlugin.getJeiRuntime();
        if (runtime == null) {
            return List.of();
        }

        IFocus<?> focus = createOutputFocus(runtime.getJeiHelpers().getFocusFactory(), output);
        Map<String, RecipeTreeRecipeViewModel> unique = new LinkedHashMap<>();
        List<RecipeType<?>> types = orderedRecipeTypes(runtime);
        for (RecipeType<?> type : types) {
            collectFocused(runtime, type, focus, unique);
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(view -> view.title().getString()))
                .toList();
    }

    public static Optional<RecipeTreeRecipeViewModel> findRecipe(Object targetRecipe) {
        IJeiRuntime runtime = DirectoryRecipePlugin.getJeiRuntime();
        if (runtime == null || targetRecipe == null) {
            return Optional.empty();
        }
        for (RecipeType<?> type : orderedRecipeTypes(runtime)) {
            Optional<RecipeTreeRecipeViewModel> found = findRecipeInType(runtime, type, targetRecipe);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void collectFocused(IJeiRuntime runtime, RecipeType<?> type, IFocus<?> focus,
            Map<String, RecipeTreeRecipeViewModel> unique) {
        IRecipeCategory category = runtime.getRecipeManager().getRecipeCategory((RecipeType) type);
        if (category == null) {
            return;
        }
        List<?> recipes = runtime.getRecipeManager().createRecipeLookup((RecipeType) type)
                .limitFocus(List.of(focus)).get().limit(512).toList();
        for (Object recipe : recipes) {
            createSnapshotForRecipe(runtime, category, recipe)
                    .filter(view -> matchesFocus(runtime.getIngredientManager(), view.primaryOutputIngredient(),
                            focus.getTypedValue()))
                    .ifPresent(view -> unique.putIfAbsent(signature(runtime.getIngredientManager(), view), view));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Optional<RecipeTreeRecipeViewModel> findRecipeInType(IJeiRuntime runtime, RecipeType<?> type,
            Object targetRecipe) {
        IRecipeCategory category = runtime.getRecipeManager().getRecipeCategory((RecipeType) type);
        if (category == null) {
            return Optional.empty();
        }
        for (Object recipe : runtime.getRecipeManager().createRecipeLookup((RecipeType) type)
                .includeHidden().get().limit(512).toList()) {
            if (recipe == targetRecipe || recipe.equals(targetRecipe)) {
                return createSnapshotForRecipe(runtime, category, recipe);
            }
        }
        return Optional.empty();
    }

    private static Optional<RecipeTreeRecipeViewModel> createSnapshotForRecipe(IJeiRuntime runtime,
            IRecipeCategory<?> rawCategory, Object rawRecipe) {
        return createSnapshotForRecipeTyped(runtime, rawCategory, rawRecipe);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> Optional<RecipeTreeRecipeViewModel> createSnapshotForRecipeTyped(IJeiRuntime runtime,
            IRecipeCategory<?> rawCategory, Object rawRecipe) {
        IRecipeCategory category = rawCategory;
        T recipe;
        try {
            recipe = (T) rawRecipe;
        } catch (ClassCastException exception) {
            return Optional.empty();
        }
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        IRecipeLayoutDrawable<T> layout = (IRecipeLayoutDrawable<T>) runtime.getRecipeManager()
                .createRecipeLayoutDrawable(category, recipe, focusFactory.getEmptyFocusGroup()).orElse(null);
        if (layout == null) {
            return Optional.empty();
        }
        ResourceLocation recipeId = category.getRegistryName(recipe);
        Component title = outputTitle(runtime.getIngredientManager(), layout.getRecipeSlotsView(), category.getTitle());
        return Optional.of(createSnapshot(layout.getRecipeSlotsView(), title, category.getTitle(), category.getIcon(),
                recipeId));
    }

    private static RecipeTreeRecipeViewModel createSnapshot(IRecipeSlotsView slots, Component title,
            Component subtitle, IDrawable subtitleIcon, ResourceLocation recipeId) {
        IJeiRuntime runtime = DirectoryRecipePlugin.getJeiRuntime();
        IIngredientManager ingredients = runtime == null ? null : runtime.getIngredientManager();

        ITypedIngredient<?> primaryOutputIngredient = null;
        ItemStack primaryOutput = ItemStack.EMPTY;
        int primaryOutputAmount = 1;
        for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            ITypedIngredient<?> displayed = displayedIngredient(slot);
            if (displayed != null) {
                primaryOutputIngredient = displayed;
                primaryOutput = extractItemStack(displayed, 1);
                primaryOutputAmount = ingredients == null ? Math.max(1, primaryOutput.getCount())
                        : amount(ingredients, displayed, Math.max(1, primaryOutput.getCount()));
                break;
            }
        }

        List<RecipeTreeInputViewModel> inputs = new ArrayList<>();
        for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<DisplayOption> options = ingredients == null ? List.of() : displayOptions(ingredients, slot);
            if (options.isEmpty()) {
                continue;
            }
            ITypedIngredient<?> displayed = displayedIngredient(slot);
            int inputAmount = ingredients == null || displayed == null ? displayedStack(slot).getCount()
                    : amount(ingredients, displayed, Math.max(1, displayedStack(slot).getCount()));
            inputAmount = Math.max(1, inputAmount);
            inputs.add(new RecipeTreeInputViewModel(options, inputAmount, formatAmount(slot, inputAmount)));
        }
        if (title == null || title.getString().isBlank()) {
            title = primaryOutputIngredient == null ? Component.translatable("jei_plus_plus.recipe_tree.unknown")
                    : Component.literal(ingredients == null ? primaryOutput.getHoverName().getString()
                            : displayName(ingredients, primaryOutputIngredient));
        }
        return new RecipeTreeRecipeViewModel(primaryOutputIngredient, primaryOutput, primaryOutputAmount,
                title, subtitle, subtitleIcon, recipeId, inputs);
    }

    private static Component outputTitle(IIngredientManager ingredients, IRecipeSlotsView slots, Component fallback) {
        for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            ITypedIngredient<?> output = displayedIngredient(slot);
            if (output != null) {
                return Component.literal(displayName(ingredients, output));
            }
        }
        return fallback;
    }

    private static List<DisplayOption> displayOptions(IIngredientManager ingredients, IRecipeSlotView slot) {
        Map<String, DisplayOption> unique = new LinkedHashMap<>();
        for (ITypedIngredient<?> ingredient : slot.getAllIngredients().toList()) {
            if (ingredient == null) {
                continue;
            }
            String id = typedSignature(ingredients, ingredient);
            unique.putIfAbsent(id, new DisplayOption(ingredient, displayName(ingredients, ingredient),
                    extractItemStack(ingredient, 1)));
        }
        return List.copyOf(unique.values());
    }

    private static String signature(IIngredientManager ingredients, RecipeTreeRecipeViewModel view) {
        StringBuilder out = new StringBuilder();
        if (view.recipeId() != null) {
            out.append(view.recipeId());
        } else if (view.primaryOutputIngredient() != null && ingredients != null) {
            out.append(typedSignature(ingredients, view.primaryOutputIngredient()));
        } else {
            out.append(view.primaryOutput().getItem());
        }
        out.append('#').append(view.title().getString());
        for (RecipeTreeInputViewModel input : view.inputs()) {
            out.append('|').append(input.amount()).append(':').append(input.displayName());
        }
        return out.toString();
    }

    private static boolean matchesFocus(IIngredientManager ingredients, ITypedIngredient<?> output,
            ITypedIngredient<?> focus) {
        return output != null && focus != null && ingredients != null
                && typedSignature(ingredients, output).equals(typedSignature(ingredients, focus));
    }

    private static String typedSignature(IIngredientManager ingredients, ITypedIngredient<?> ingredient) {
        return typedSignatureTyped(ingredients, ingredient);
    }

    private static <T> String typedSignatureTyped(IIngredientManager ingredients, ITypedIngredient<?> raw) {
        ITypedIngredient<T> typed = (ITypedIngredient<T>) raw;
        IIngredientHelper<T> helper = ingredients.getIngredientHelper(typed.getType());
        return typed.getType().getUid() + "#" + helper.getUniqueId(typed.getIngredient(), UidContext.Ingredient);
    }

    private static String displayName(IIngredientManager ingredients, ITypedIngredient<?> raw) {
        return displayNameTyped(ingredients, raw);
    }

    private static <T> String displayNameTyped(IIngredientManager ingredients, ITypedIngredient<?> raw) {
        ITypedIngredient<T> typed = (ITypedIngredient<T>) raw;
        return ingredients.getIngredientHelper(typed.getType()).getDisplayName(typed.getIngredient());
    }

    private static int amount(IIngredientManager ingredients, ITypedIngredient<?> raw, int fallback) {
        ITypedIngredient<?> typed = raw;
        long value = amountTyped(ingredients, typed);
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, value <= 0 ? fallback : value));
    }

    private static <T> long amountTyped(IIngredientManager ingredients, ITypedIngredient<?> raw) {
        ITypedIngredient<T> typed = (ITypedIngredient<T>) raw;
        return ingredients.getIngredientHelper(typed.getType()).getAmount(typed.getIngredient());
    }

    private static String formatAmount(IRecipeSlotView slot, int fallback) {
        int count = slot.getIngredients(VanillaTypes.ITEM_STACK).mapToInt(ItemStack::getCount)
                .filter(value -> value > 1).findFirst().orElse(0);
        return count > 1 ? "x" + count : "x" + Math.max(1, fallback);
    }

    private static ITypedIngredient<?> displayedIngredient(IRecipeSlotView slot) {
        return slot.getDisplayedIngredient().orElseGet(() -> slot.getAllIngredients().findFirst().orElse(null));
    }

    private static ItemStack displayedStack(IRecipeSlotView slot) {
        return slot.getDisplayedItemStack().or(() -> slot.getItemStacks().findFirst())
                .map(ItemStack::copy).orElse(ItemStack.EMPTY);
    }

    private static ItemStack extractItemStack(ITypedIngredient<?> raw, int fallbackCount) {
        return raw.getIngredient(VanillaTypes.ITEM_STACK)
                .map(stack -> stack.copyWithCount(Math.max(1, fallbackCount))).orElse(ItemStack.EMPTY);
    }

    private static IFocus<?> createOutputFocus(IFocusFactory factory, ITypedIngredient<?> ingredient) {
        return createOutputFocusTyped(factory, ingredient);
    }

    @SuppressWarnings("unchecked")
    private static <T> IFocus<T> createOutputFocusTyped(IFocusFactory factory, ITypedIngredient<?> raw) {
        return factory.createFocus(RecipeIngredientRole.OUTPUT, (ITypedIngredient<T>) raw);
    }

    private static List<RecipeType<?>> orderedRecipeTypes(IJeiRuntime runtime) {
        List<RecipeType<?>> all = new ArrayList<>(runtime.getJeiHelpers().getAllRecipeTypes().toList());
        List<RecipeType<?>> ordered = new ArrayList<>(all.size());
        if (all.remove(RecipeTypes.CRAFTING)) {
            ordered.add(RecipeTypes.CRAFTING);
        }
        ordered.addAll(all);
        return ordered;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceLocation registryName(IRecipeCategory<?> category, Object recipe) {
        return ((IRecipeCategory) category).getRegistryName(recipe);
    }
}
