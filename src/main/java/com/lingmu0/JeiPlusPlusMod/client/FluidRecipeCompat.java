package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import java.util.List;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Optional;

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
    private static final IdentityHashMap<ItemStack, FluidStack> DISPLAY_FLUIDS = new IdentityHashMap<>();

    private FluidRecipeCompat() {
    }

    static void clear() {
        DISPLAY_FLUIDS.clear();
    }

    static Optional<FluidStack> fluid(ITypedIngredient<?> ingredient) {
        return ingredient == null
            ? Optional.empty()
            : ingredient.getIngredient(NeoForgeTypes.FLUID_STACK);
    }

    static Optional<ITypedIngredient<?>> scaled(
        ITypedIngredient<?> ingredient,
        int batches,
        IIngredientManager manager
    ) {
        Optional<FluidStack> value = fluid(ingredient);
        if (value.isEmpty() || batches <= 1) {
            return Optional.ofNullable(ingredient);
        }
        long amount = (long) value.get().getAmount() * batches;
        if (amount > Integer.MAX_VALUE) {
            return Optional.of(ingredient);
        }
        FluidStack copy = value.get().copyWithAmount((int) amount);
        return manager.createTypedIngredient(NeoForgeTypes.FLUID_STACK, copy, false)
            .map(stack -> (ITypedIngredient<?>) stack);
    }

    /**
     * Finds filled item containers in the player's inventory that can supply
     * the requested fluid.  The returned stacks represent the number of
     * containers needed for one transfer batch and are intentionally copied so
     * JEI cannot mutate the real inventory stack while validating a transfer.
     */
    static List<ItemStack> matchingContainers(Player player, FluidStack required, int batches) {
        if (player == null || required == null || required.isEmpty()) {
            return List.of();
        }
        int requiredAmount = safeAmount(required.getAmount(), batches);
        for (ItemStack inventoryStack : player.getInventory().items) {
            if (inventoryStack.isEmpty()) {
                continue;
            }
            IFluidHandlerItem handler = inventoryStack.getCapability(Capabilities.FluidHandler.ITEM);
            if (handler == null) {
                continue;
            }
            FluidStack contained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (contained == null || contained.isEmpty()
                || !FluidStack.isSameFluidSameComponents(contained, required)) {
                continue;
            }
            int perContainer = Math.max(1, contained.getAmount());
            int containers = (int) Math.min(Integer.MAX_VALUE,
                Math.max(1L, ((long) requiredAmount + perContainer - 1L) / perContainer));
            if (containers <= inventoryStack.getCount()) {
                ItemStack candidate = inventoryStack.copy();
                candidate.setCount(containers);
                registerDisplay(candidate, required);
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
            // FluidStack directly, so the bucket is never shown as the node.
            var bucketItem = value.getFluid().getBucket();
            ItemStack bucket = new ItemStack(bucketItem == Items.AIR ? Items.BUCKET : bucketItem);
            // 1000 mB is one bucket; partial buckets require one container.
            int count = (int) Math.min(Integer.MAX_VALUE, containerCount(value.getAmount()));
            bucket.setCount(count);
            registerDisplay(bucket, value);
            return bucket;
        }).filter(stack -> !stack.isEmpty());
    }

    private static void registerDisplay(ItemStack stack, FluidStack fluid) {
        if (stack == null || stack.isEmpty() || fluid == null || fluid.isEmpty()) {
            return;
        }
        DISPLAY_FLUIDS.put(stack, fluid.copy());
    }

    static Optional<FluidStack> displayFluid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        FluidStack fluid = DISPLAY_FLUIDS.get(stack);
        if (fluid != null) {
            return Optional.of(fluid.copy());
        }
        if (stack.getCapability(Capabilities.FluidHandler.ITEM) instanceof IFluidHandlerItem handler) {
            FluidStack contained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (contained != null && !contained.isEmpty()) {
                return Optional.of(contained.copy());
            }
        }
        return Optional.empty();
    }

    static Optional<String> fluidKey(ItemStack stack) {
        return displayFluid(stack).map(value -> "fluid:" + net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(value.getFluid()));
    }

    static Optional<ITypedIngredient<?>> toTyped(IIngredientManager manager, ItemStack stack) {
        if (manager == null || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Optional<FluidStack> fluid = displayFluid(stack);
        if (fluid.isPresent()) {
            return manager.createTypedIngredient(NeoForgeTypes.FLUID_STACK, fluid.get(), false)
                .map(value -> (ITypedIngredient<?>) value);
        }
        return manager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false)
            .map(value -> (ITypedIngredient<?>) value);
    }

    static long containerCount(long millibuckets) {
        return Math.max(1, (millibuckets + FluidType.BUCKET_VOLUME - 1) / FluidType.BUCKET_VOLUME);
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
        Optional<FluidStack> value = displayFluid(stack);
        if (value.isEmpty()) {
            return 0;
        }
        long denominator = Math.max(1, stack.getCount());
        // Cost records normalize their display stack count to one.  When
        // that happened to a synthetic partial-fluid container, recover the
        // original number of containers from the fluid amount itself.
        if (denominator == 1 && value.get().getAmount() > FluidType.BUCKET_VOLUME) {
            denominator = containerCount(value.get().getAmount());
        }
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
        if (value < FluidType.BUCKET_VOLUME) {
            return value + "mB";
        }
        return String.format(Locale.ROOT, "%.1fB", value / (double) FluidType.BUCKET_VOLUME);
    }

    static Optional<IFocus<FluidStack>> createOutputFocus(IJeiRuntime runtime, ItemStack stack) {
        Optional<FluidStack> value = displayFluid(stack);
        if (runtime == null || value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(runtime.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.OUTPUT,
                NeoForgeTypes.FLUID_STACK,
                value.get()
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    static ItemStack copyWithDisplay(ItemStack stack) {
        ItemStack copy = stack == null ? ItemStack.EMPTY : stack.copy();
        if (stack != null) {
            FluidStack fluid = DISPLAY_FLUIDS.get(stack);
            if (fluid != null) {
                DISPLAY_FLUIDS.put(copy, fluid.copy());
            }
        }
        return copy;
    }

    /** Draws a registered FluidStack and returns whether the stack was fluid-backed. */
    static boolean render(GuiGraphics graphics, ItemStack stack, int x, int y) {
        Optional<FluidStack> value = displayFluid(stack);
        if (value.isEmpty()) {
            return false;
        }
        IIngredientManager manager = DirectoryRecipePlugin.getJeiRuntime() == null
            ? null
            : DirectoryRecipePlugin.getJeiRuntime().getIngredientManager();
        if (manager == null) {
            return false;
        }
        IIngredientRenderer<FluidStack> renderer = manager.getIngredientRenderer(NeoForgeTypes.FLUID_STACK);
        renderer.render(graphics, value.get(), x, y);
        return true;
    }

    /** Draws the JEI fluid tooltip for a tree node or candidate choice. */
    static boolean renderTooltip(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        Optional<FluidStack> value = displayFluid(stack);
        if (value.isEmpty()) {
            return false;
        }
        IIngredientManager manager = DirectoryRecipePlugin.getJeiRuntime() == null
            ? null
            : DirectoryRecipePlugin.getJeiRuntime().getIngredientManager();
        if (manager == null) {
            return false;
        }
        IIngredientRenderer<FluidStack> renderer = manager.getIngredientRenderer(NeoForgeTypes.FLUID_STACK);
        List<Component> tooltip = renderer.getTooltip(value.get(), TooltipFlag.Default.NORMAL);
        graphics.renderTooltip(Minecraft.getInstance().font, tooltip, Optional.empty(), mouseX, mouseY);
        return true;
    }

    private static int safeAmount(int amount, int batches) {
        long value = (long) Math.max(1, amount) * Math.max(1, batches);
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
}
