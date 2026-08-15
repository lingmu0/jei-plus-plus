package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.List;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Optional;

/** Client-side bridge between JEI fluid ingredients and filled item containers. */
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
            : ingredient.getIngredient(ForgeTypes.FLUID_STACK);
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
        FluidStack copy = value.get().copy();
        copy.setAmount((int) amount);
        return manager.createTypedIngredient(ForgeTypes.FLUID_STACK, copy, false)
            .map(stack -> (ITypedIngredient<?>) stack);
    }

    static List<ItemStack> matchingContainers(Player player, FluidStack required, int batches) {
        if (player == null || required == null || required.isEmpty()) {
            return List.of();
        }
        int requiredAmount = safeAmount(required.getAmount(), batches);
        for (ItemStack inventoryStack : player.getInventory().items) {
            if (inventoryStack.isEmpty()) {
                continue;
            }
            IFluidHandler handler = inventoryStack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElse(null);
            if (handler == null) {
                continue;
            }
            FluidStack contained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (contained == null || contained.isEmpty() || !contained.isFluidEqual(required)) {
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
            // This is an internal bridge for item-slot transfer. The tree
            // renderer draws the registered FluidStack itself.
            var bucketItem = value.getFluid().getBucket();
            ItemStack bucket = new ItemStack(bucketItem == Items.AIR ? Items.BUCKET : bucketItem);
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
        IFluidHandler handler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
            .resolve().orElse(null);
        if (handler != null) {
            FluidStack contained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (contained != null && !contained.isEmpty()) {
                return Optional.of(contained.copy());
            }
        }
        return Optional.empty();
    }

    static Optional<String> fluidKey(ItemStack stack) {
        return displayFluid(stack)
            .map(value -> "fluid:" + net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(value.getFluid()));
    }

    static Optional<ITypedIngredient<?>> toTyped(IIngredientManager manager, ItemStack stack) {
        if (manager == null || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Optional<FluidStack> fluid = displayFluid(stack);
        if (fluid.isPresent()) {
            return manager.createTypedIngredient(ForgeTypes.FLUID_STACK, fluid.get(), false)
                .map(value -> (ITypedIngredient<?>) value);
        }
        return manager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false)
            .map(value -> (ITypedIngredient<?>) value);
    }

    static long containerCount(long millibuckets) {
        return Math.max(1, (millibuckets + net.minecraftforge.fluids.FluidType.BUCKET_VOLUME - 1)
            / net.minecraftforge.fluids.FluidType.BUCKET_VOLUME);
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
        if (denominator == 1 && value.get().getAmount() > net.minecraftforge.fluids.FluidType.BUCKET_VOLUME) {
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
        if (value < net.minecraftforge.fluids.FluidType.BUCKET_VOLUME) {
            return value + "mB";
        }
        return String.format(Locale.ROOT, "%.1fB",
            value / (double) net.minecraftforge.fluids.FluidType.BUCKET_VOLUME);
    }

    static Optional<IFocus<FluidStack>> createOutputFocus(IJeiRuntime runtime, ItemStack stack) {
        Optional<FluidStack> value = displayFluid(stack);
        if (runtime == null || value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(runtime.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.OUTPUT,
                ForgeTypes.FLUID_STACK,
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
        IIngredientRenderer<FluidStack> renderer = manager.getIngredientRenderer(ForgeTypes.FLUID_STACK);
        renderer.render(graphics, value.get(), x, y);
        return true;
    }

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
        IIngredientRenderer<FluidStack> renderer = manager.getIngredientRenderer(ForgeTypes.FLUID_STACK);
        List<Component> tooltip = renderer.getTooltip(value.get(), TooltipFlag.Default.NORMAL);
        graphics.renderTooltip(Minecraft.getInstance().font, tooltip, Optional.empty(), mouseX, mouseY);
        return true;
    }

    private static int safeAmount(int amount, int batches) {
        long value = (long) Math.max(1, amount) * Math.max(1, batches);
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
}
