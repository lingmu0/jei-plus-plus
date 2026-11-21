package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small client-side bridge between JEI fluid ingredients and item containers.
 *
 * <p>JEI recipe transfer handlers receive fluid ingredients as typed values,
 * while a number of machines expose their input as an item slot accepting a
 * filled bucket (or another item fluid handler).  Keeping the original fluid
 * ingredient and adding matching item candidates lets both kinds of handler
 * work without a JEI++ server packet.</p>
 */
final class FluidRecipeCompat {
    private static final IdentityHashMap<ItemStack, FabricFluidStack> DISPLAY_FLUIDS = new IdentityHashMap<>();
    private static final Map<String, ItemStack> FLUID_REPRESENTATIONS = new ConcurrentHashMap<>();
    private static final List<String> FLUID_ACCESSOR_NAMES = List.of(
        "getFluidStack", "getReadOnlyStack", "getRenderStack", "getStack",
        "toFluidStack", "toStack", "getInstance", "getIngredient", "getResource",
        "getFluidResource", "getFluid", "fluid", "getWhat", "getKey", "getSource"
    );
    private static final Map<Class<?>, List<Method>> FLUID_ACCESSORS = new ConcurrentHashMap<>();

    private FluidRecipeCompat() {
    }

    /** A loader-neutral description of a fluid value returned by an optional storage API. */
    record FluidInfo(String key, long amount) {
    }

    /**
     * Reads a fluid from a foreign storage wrapper without linking against
     * that mod's classes.  AE2, Refined Storage, Beyond Dimensions and
     * Integrated Terminals have all used different wrapper types over time,
     * but they expose one of the small no-argument accessors below.  The
     * bounded identity walk keeps an unexpected/cyclic wrapper harmless.
     */
    static Optional<FluidInfo> describeFluid(Object value) {
        return describeFluid(value, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private static Optional<FluidInfo> describeFluid(Object value, Set<Object> seen, int depth) {
        if (value == null || depth > 4 || !seen.add(value)) {
            return Optional.empty();
        }
        if (value instanceof IJeiFluidIngredient ingredient) {
            return describeFluid(FabricFluidStack.from(ingredient), seen, depth + 1);
        }
        if (value instanceof FluidVariant variant) {
            return Optional.of(new FluidInfo("fluid:" + BuiltInRegistries.FLUID.getKey(variant.getFluid()), 0L));
        }
        Optional<FabricFluidStack> foreign = FabricFluidStack.fromForeign(value);
        if (foreign.isPresent()) return describeFluid(foreign.get(), seen, depth + 1);
        if (value instanceof FabricFluidStack stack) {
            if (stack.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new FluidInfo(
                "fluid:" + BuiltInRegistries.FLUID.getKey(stack.getFluid()),
                Math.max(0L, stack.getAmount())
            ));
        }
        if (value instanceof ItemStack stack) {
            return displayFluid(stack).map(fluid -> new FluidInfo(
                "fluid:" + BuiltInRegistries.FLUID.getKey(fluid.getFluid()),
                Math.max(0L, fluid.getAmount())
            ));
        }
        if (value instanceof Fluid fluid) {
            return Optional.of(new FluidInfo(
                "fluid:" + BuiltInRegistries.FLUID.getKey(fluid),
                0L
            ));
        }
        for (Method method : FLUID_ACCESSORS.computeIfAbsent(value.getClass(), FluidRecipeCompat::findFluidAccessors)) {
            try {
                Optional<FluidInfo> result = describeFluid(method.invoke(value), seen, depth + 1);
                if (result.isPresent()) {
                    return result;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Optional wrapper API; try the next accessor.
            }
        }
        return Optional.empty();
    }

    private static List<Method> findFluidAccessors(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (String name : FLUID_ACCESSOR_NAMES) {
            try {
                Method method = type.getMethod(name);
                try {
                    method.trySetAccessible();
                } catch (RuntimeException ignored) {
                    // Public methods remain invokable when access is restricted.
                }
                methods.add(method);
            } catch (NoSuchMethodException | SecurityException ignored) {
                // Optional wrapper API; try the next accessor.
            }
        }
        return List.copyOf(methods);
    }

    static void clear() {
        DISPLAY_FLUIDS.clear();
        MekanismChemicalIngredient.clear();
        FLUID_REPRESENTATIONS.clear();
        FLUID_ACCESSORS.clear();
    }

    static Optional<FabricFluidStack> fluid(ITypedIngredient<?> ingredient) {
        return ingredient == null
            ? Optional.empty()
            : ingredient.getIngredient(FabricTypes.FLUID_STACK).map(FabricFluidStack::from);
    }

    static Optional<ITypedIngredient<?>> scaled(
        ITypedIngredient<?> ingredient,
        int batches,
        IIngredientManager manager
    ) {
        Optional<FabricFluidStack> value = fluid(ingredient);
        if (value.isEmpty() || batches <= 1) {
            return Optional.ofNullable(ingredient);
        }
        FabricFluidStack copy = value.get().scale(batches);
        return manager.createTypedIngredient(FabricTypes.FLUID_STACK, copy.ingredient())
            .map(stack -> (ITypedIngredient<?>) stack);
    }

    /**
     * Finds filled item containers in the player's inventory that can supply
     * the requested fluid.  The returned stacks represent the number of
     * containers needed for one transfer batch and are intentionally copied so
     * JEI cannot mutate the real inventory stack while validating a transfer.
     */
    static List<ItemStack> matchingContainers(Player player, FabricFluidStack required, int batches) {
        if (player == null || required == null || required.isEmpty()) {
            return List.of();
        }
        int requiredAmount = safeAmount(required.getAmount(), batches);
        for (ItemStack inventoryStack : player.getInventory().getNonEquipmentItems()) {
            if (inventoryStack.isEmpty()) {
                continue;
            }
            FabricFluidStack contained = fluidFromItem(inventoryStack).orElse(FabricFluidStack.EMPTY);
            if (contained.isEmpty()
                || !FabricFluidStack.isSameFluidSameComponents(contained, required)) {
                continue;
            }
            int perContainer = Math.max(1, contained.getAmount());
            int containers = (int) Math.min(Integer.MAX_VALUE,
                Math.max(1L, ((long) requiredAmount + perContainer - 1L) / perContainer));
            if (containers <= inventoryStack.getCount()) {
                ItemStack candidate = inventoryStack.copy();
                candidate.setCount(containers);
                return List.of(candidate);
            }
        }
        return List.of();
    }

    static List<ItemStack> matchingContainers(Player player, ITypedIngredient<?> ingredient) {
        return fluid(ingredient)
            .map(value -> matchingContainers(player, value, 1))
            .orElseGet(List::of);
    }

    static Optional<ItemStack> representativeContainer(ITypedIngredient<?> ingredient) {
        return fluid(ingredient).map(value -> {
            // The representative is an internal bridge for item-slot
            // transfer only.  The tree renderer uses the registered
            // FabricFluidStack directly, so the bucket is never shown as the node.
            var bucketItem = value.getFluid().getBucket();
            ItemStack bucket = new ItemStack(bucketItem == Items.AIR ? Items.BUCKET : bucketItem);
            // 1000 mB is one bucket; partial buckets require one container.
            int count = (int) Math.min(Integer.MAX_VALUE, containerCount(value.getAmount()));
            bucket.setCount(count);
            registerDisplay(bucket, value);
            return bucket;
        }).filter(stack -> !stack.isEmpty());
    }

    /** A real bucket/item candidate for a fluid ingredient.  It deliberately
     * has no display registration, so its ingredient key remains the bucket
     * item rather than the synthetic fluid key. */
    static Optional<ItemStack> containerCandidate(ITypedIngredient<?> ingredient) {
        return fluid(ingredient).map(value -> {
            var bucketItem = value.getFluid().getBucket();
            ItemStack bucket = new ItemStack(bucketItem == Items.AIR ? Items.BUCKET : bucketItem);
            bucket.setCount((int) Math.min(Integer.MAX_VALUE, containerCount(value.getAmount())));
            return bucket;
        }).filter(stack -> !stack.isEmpty());
    }

    private static void registerDisplay(ItemStack stack, FabricFluidStack fluid) {
        if (stack == null || stack.isEmpty() || fluid == null || fluid.isEmpty()) {
            return;
        }
        DISPLAY_FLUIDS.put(stack, fluid.copy());
    }

    static Optional<FabricFluidStack> displayFluid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        FabricFluidStack fluid = DISPLAY_FLUIDS.get(stack);
        if (fluid != null) {
            return Optional.of(fluid.copy());
        }
        return fluidFromItem(stack);
    }

    private static Optional<FabricFluidStack> fluidFromItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        ItemStack probe = stack.copy();
        probe.setCount(1);
        var storage = ContainerItemContext.withConstant(probe).find(FluidStorage.ITEM);
        if (storage != null) {
            for (var view : storage) {
                if (!view.getResource().isBlank() && view.getAmount() > 0) {
                    return Optional.of(new FabricFluidStack(view.getResource(), view.getAmount()));
                }
            }
        }
        return Optional.empty();
    }

    /** Fluid explicitly represented by a recipe-tree synthetic stack. */
    static Optional<FabricFluidStack> treeFluid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        FabricFluidStack fluid = DISPLAY_FLUIDS.get(stack);
        return fluid == null ? Optional.empty() : Optional.of(fluid.copy());
    }

    static Optional<String> fluidKey(ItemStack stack) {
        return treeFluid(stack).map(value -> fluidKey(value));
    }

    static String fluidKey(FabricFluidStack value) {
        return value == null || value.isEmpty()
            ? ""
            : "fluid:" + net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(value.getFluid());
    }

    /** Returns the fluid key carried by a real container without converting it
     * into a synthetic tree stack. Used by inventory highlighting only. */
    static Optional<String> displayFluidKey(ItemStack stack) {
        return displayFluid(stack)
            .map(FluidRecipeCompat::fluidKey)
            .filter(key -> !key.isEmpty());
    }

    /** Creates a fluid-key alias for a real filled container in inventory. */
    static Optional<ItemStack> fluidRepresentation(ItemStack stack) {
        Optional<FabricFluidStack> value = displayFluid(stack);
        if (value.isEmpty() || fluidKey(stack).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(cachedRepresentation(value.get()));
    }

    static Optional<ITypedIngredient<?>> toTyped(IIngredientManager manager, ItemStack stack) {
        if (!MekanismChemicalIngredient.key(stack).isEmpty()) {
            return MekanismChemicalIngredient.typed(stack);
        }
        if (manager == null || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Optional<FabricFluidStack> fluid = treeFluid(stack);
        if (fluid.isPresent()) {
            return manager.createTypedIngredient(FabricTypes.FLUID_STACK, fluid.get().ingredient())
                .map(value -> (ITypedIngredient<?>) value);
        }
        return manager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack)
            .map(value -> (ITypedIngredient<?>) value);
    }

    static long containerCount(long millibuckets) {
        return Math.max(1, (millibuckets + 1000 - 1) / 1000);
    }

    /** Returns the actual fluid amount represented by every item in a stack. */
    static long amountInContainers(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0L;
        }
        Optional<FabricFluidStack> fluid = displayFluid(stack);
        if (fluid.isEmpty()) {
            return 0L;
        }
        try {
            return Math.multiplyExact(
                Math.max(0L, fluid.get().getAmount()),
                Math.max(1L, stack.getCount())
            );
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /** Recreates a representative tree item for a fluid key reported by a network. */
    static Optional<ItemStack> representativeForKey(String key, long amount) {
        if (key == null || !key.startsWith("fluid:") || amount <= 0) {
            return Optional.empty();
        }
        Identifier id = Identifier.tryParse(key.substring("fluid:".length()));
        if (id == null) {
            return Optional.empty();
        }
        Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
        if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
            return Optional.empty();
        }
        int safeAmount = (int) Math.min(Integer.MAX_VALUE, amount);
        FabricFluidStack value = new FabricFluidStack(fluid, safeAmount);
        return Optional.of(cachedRepresentation(value));
    }

    private static ItemStack cachedRepresentation(FabricFluidStack value) {
        String cacheKey = fluidKey(value) + "|" + value.getAmount();
        ItemStack prototype = FLUID_REPRESENTATIONS.computeIfAbsent(cacheKey, ignored -> {
            var bucketItem = value.getFluid().getBucket();
            ItemStack bucket = new ItemStack(bucketItem == Items.AIR ? Items.BUCKET : bucketItem);
            registerDisplay(bucket, value);
            return bucket;
        });
        return copyWithDisplay(prototype);
    }

    /**
     * Converts the tree's synthetic container units back to the amount of
     * fluid represented by the original recipe slot.  A recipe slot carrying
     * 1500 mB is represented by two containers, so two tree units correspond
     * to 1500 mB rather than 3000 mB.
     */
    static long amountForUnits(ItemStack stack, long units) {
        if (stack == null || stack.isEmpty() || units <= 0) {
            return 0;
        }
        Optional<FabricFluidStack> value = displayFluid(stack);
        if (value.isEmpty()) {
            return 0;
        }
        long denominator = Math.max(1, stack.getCount());
        long numerator;
        try {
            numerator = Math.multiplyExact((long) value.get().getAmount(), units);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
        if (numerator > Long.MAX_VALUE - (denominator - 1L)) {
            return Long.MAX_VALUE;
        }
        return (numerator + denominator - 1L) / denominator;
    }

    /** Formats fluid quantities using JEI-style mB below one bucket and B above it. */
    static String formatAmount(long millibuckets) {
        long value = Math.max(0, millibuckets);
        if (value < 1000) {
            return value + "mB";
        }
        return String.format(Locale.ROOT, "%.1fB", value / (double) 1000);
    }

    static Optional<IFocus<IJeiFluidIngredient>> createOutputFocus(IJeiRuntime runtime, ItemStack stack) {
        Optional<FabricFluidStack> value = treeFluid(stack);
        if (runtime == null || value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(runtime.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.OUTPUT,
                FabricTypes.FLUID_STACK,
                value.get().ingredient()
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    static ItemStack copyWithDisplay(ItemStack stack) {
        ItemStack copy = stack == null ? ItemStack.EMPTY : stack.copy();
        MekanismChemicalIngredient.copyDisplay(stack, copy);
        if (stack != null) {
            FabricFluidStack fluid = DISPLAY_FLUIDS.get(stack);
            if (fluid != null) {
                DISPLAY_FLUIDS.put(copy, fluid.copy());
            }
        }
        return copy;
    }

    /** Draws a registered FabricFluidStack and returns whether the stack was fluid-backed. */
    static boolean render(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        if (MekanismChemicalIngredient.render(graphics, stack, x, y)) return true;
        Optional<FabricFluidStack> value = treeFluid(stack);
        if (value.isEmpty()) {
            return false;
        }
        IIngredientManager manager = DirectoryRecipePlugin.getJeiRuntime() == null
            ? null
            : DirectoryRecipePlugin.getJeiRuntime().getIngredientManager();
        if (manager == null) {
            return false;
        }
        IIngredientRenderer<IJeiFluidIngredient> renderer = manager.getIngredientRenderer(FabricTypes.FLUID_STACK);
        renderer.render(graphics, value.get().ingredient(), x, y);
        return true;
    }

    /** Draws the JEI fluid tooltip for a tree node or candidate choice. */
    static boolean renderTooltip(GuiGraphicsExtractor graphics, ItemStack stack, int mouseX, int mouseY) {
        if (MekanismChemicalIngredient.renderTooltip(graphics, stack, mouseX, mouseY)) return true;
        Optional<FabricFluidStack> value = treeFluid(stack);
        if (value.isEmpty()) {
            return false;
        }
        IIngredientManager manager = DirectoryRecipePlugin.getJeiRuntime() == null
            ? null
            : DirectoryRecipePlugin.getJeiRuntime().getIngredientManager();
        if (manager == null) {
            return false;
        }
        IIngredientRenderer<IJeiFluidIngredient> renderer = manager.getIngredientRenderer(FabricTypes.FLUID_STACK);
        List<Component> tooltip = renderer.getTooltip(value.get().ingredient(), TooltipFlag.Default.NORMAL);
        graphics.setTooltipForNextFrame(Minecraft.getInstance().font, tooltip, Optional.empty(), mouseX, mouseY);
        return true;
    }

    private static int safeAmount(int amount, int batches) {
        long value = (long) Math.max(1, amount) * Math.max(1, batches);
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
}
