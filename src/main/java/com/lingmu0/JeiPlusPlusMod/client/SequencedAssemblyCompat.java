package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Optional Create sequenced-assembly support.
 *
 * <p>Create deliberately adds the inputs of loops after the first one with
 * JEI's {@code addInvisibleIngredients}. JEI keeps those builders out of the
 * public recipe-slot view, so a normal layout cannot describe the complete
 * material cost. This bridge reads only the stable accessor names used by
 * Create and never links the optional Create classes at compile time.</p>
 */
final class SequencedAssemblyCompat {
    private SequencedAssemblyCompat() {
    }

    static List<List<ITypedIngredient<?>>> repeatedInputSlots(
        Object recipe,
        IIngredientManager ingredientManager
    ) {
        if (recipe == null || ingredientManager == null
            || !recipe.getClass().getName().endsWith(".SequencedAssemblyRecipe")) {
            return List.of();
        }

        int loops = Math.max(1, invokeInt(recipe, "getLoops").orElse(1));
        if (loops <= 1) {
            return List.of();
        }
        List<?> sequence = asList(invokeNoArg(recipe, "getSequence").orElse(null));
        if (sequence.isEmpty()) {
            return List.of();
        }

        List<List<ITypedIngredient<?>>> result = new ArrayList<>();
        for (int loop = 1; loop < loops; loop++) {
            for (Object sequencedRecipe : sequence) {
                Object stepRecipe = invokeNoArg(sequencedRecipe, "getRecipe").orElse(null);
                if (stepRecipe == null) {
                    continue;
                }

                List<?> itemIngredients = asList(invokeNoArg(stepRecipe, "getIngredients").orElse(null));
                // The first item is the item carried through the assembly line.
                // Create's category displays it once as the assembly input; only
                // the remaining ingredients are consumed on every loop.
                for (int index = 1; index < itemIngredients.size(); index++) {
                    List<ITypedIngredient<?>> converted = itemIngredient(
                        itemIngredients.get(index), ingredientManager
                    );
                    if (!converted.isEmpty()) {
                        result.add(converted);
                    }
                }

                List<?> fluidIngredients = asList(
                    invokeNoArg(stepRecipe, "getFluidIngredients").orElse(null)
                );
                for (Object fluidIngredient : fluidIngredients) {
                    List<ITypedIngredient<?>> converted = fluidIngredient(
                        fluidIngredient, ingredientManager
                    );
                    if (!converted.isEmpty()) {
                        result.add(converted);
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<ITypedIngredient<?>> itemIngredient(
        Object value,
        IIngredientManager ingredientManager
    ) {
        if (!(value instanceof Ingredient ingredient)) {
            return List.of();
        }
        List<ITypedIngredient<?>> result = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack.copy())
                .ifPresent(typed -> result.add((ITypedIngredient<?>) typed));
        }
        return List.copyOf(result);
    }

    private static List<ITypedIngredient<?>> fluidIngredient(
        Object value,
        IIngredientManager ingredientManager
    ) {
        List<?> fluids = asList(invokeNoArg(value, "getFluids").orElse(null));
        List<ITypedIngredient<?>> result = new ArrayList<>();
        for (Object candidate : fluids) {
            if (!(candidate instanceof FluidStack stack) || stack.isEmpty()) {
                continue;
            }
            ingredientManager.createTypedIngredient(ForgeTypes.FLUID_STACK, stack.copy())
                .ifPresent(typed -> result.add((ITypedIngredient<?>) typed));
        }
        return List.copyOf(result);
    }

    private static Optional<Integer> invokeInt(Object target, String name) {
        return invokeNoArg(target, name)
            .filter(Number.class::isInstance)
            .map(value -> ((Number) value).intValue());
    }

    private static Optional<Object> invokeNoArg(Object target, String name) {
        if (target == null) {
            return Optional.empty();
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.trySetAccessible();
                return Optional.ofNullable(method.invoke(target));
            } catch (NoSuchMethodException ignored) {
                // Continue through the class hierarchy.
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static List<?> asList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Collection<?> collection) {
            return List.copyOf(collection);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(result::add);
            return List.copyOf(result);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(Array.get(value, index));
            }
            return List.copyOf(result);
        }
        return List.of();
    }
}
