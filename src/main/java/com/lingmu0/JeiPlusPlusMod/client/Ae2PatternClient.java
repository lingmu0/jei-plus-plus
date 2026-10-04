package com.lingmu0.JeiPlusPlusMod.client;

import com.lingmu0.JeiPlusPlusMod.Ae2PatternNetwork;
import com.lingmu0.JeiPlusPlusMod.Ae2PatternPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Client-only recipe-tree export for AE2's encode button modifier actions. */
public final class Ae2PatternClient {
    private static final int CHUNK_SIZE = 8;
    private Ae2PatternClient() {}

    public static boolean onEncode() {
        if (!Minecraft.getInstance().hasShiftDown() && !Minecraft.getInstance().hasControlDown()) return false;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.containerMenu == null) return false;
        if (!Ae2PatternNetwork.available(minecraft.getConnection() == null
                ? null : minecraft.getConnection().getConnection())) {
            minecraft.player.sendOverlayMessage(Component.translatable("jei_plus_plus.ae2.server_required"));
            return true;
        }
        RecipeTreeData.Tree tree = RecipeTreeSession.tree();
        if (tree == null || tree.root() == null) {
            minecraft.player.sendOverlayMessage(Component.translatable("jei_plus_plus.ae2.no_tree"));
            return true;
        }
        List<Ae2PatternPlan> plans = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collect(tree.root(), plans, seen);
        if (plans.isEmpty()) {
            minecraft.player.sendOverlayMessage(Component.translatable("jei_plus_plus.ae2.no_patterns"));
            return true;
        }
        send(plans, minecraft.hasControlDown(), minecraft.player.containerMenu.containerId);
        return true;
    }

    private static void send(List<Ae2PatternPlan> plans, boolean force, int menuId) {
        int batch = ThreadLocalRandom.current().nextInt();
        int count = (plans.size() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        for (int i = 0; i < count; i++) {
            ClientPacketDistributor.sendToServer(new Ae2PatternNetwork.Request(
                menuId, batch, i, count, force,
                List.copyOf(plans.subList(i * CHUNK_SIZE, Math.min(plans.size(), (i + 1) * CHUNK_SIZE)))));
        }
    }

    private static void collect(RecipeTreeData.Node node, List<Ae2PatternPlan> plans, Set<String> seen) {
        for (RecipeTreeData.Node child : node.children()) collect(child, plans, seen);
        RecipeTreeData.RecipeSnapshot recipe = node.recipe();
        if (recipe == null || recipe.inputs().isEmpty() || recipe.outputs().isEmpty()
                || recipe.inputSlotCount() > 9
                || plans.size() >= RecipeTreeData.MAX_NODES) return;
        ItemStack[] inputs = new ItemStack[Math.min(9, recipe.inputSlotCount())];
        Arrays.fill(inputs, ItemStack.EMPTY);
        boolean substitute = false;
        for (RecipeTreeData.RecipeInput input : recipe.inputs()) {
            ItemStack chosen = input.first();
            boolean pinned = false;
            for (RecipeTreeData.Node child : node.children()) {
                if (child.inputSlotIndexes().equals(input.slotIndexes()) && child.explicitChoice()) {
                    chosen = child.stack();
                    pinned = true;
                    break;
                }
            }
            if (!pinned && input.alternatives().size() > 1) substitute = true;
            for (int slot : input.slotIndexes()) {
                if (slot >= 0 && slot < inputs.length) {
                    inputs[slot] = chosen.copy();
                    if (input.slotIndexes().size() > 1 && !inputs[slot].isEmpty()) {
                        inputs[slot].setCount(Math.max(1,
                                (chosen.getCount() + input.slotIndexes().size() - 1) / input.slotIndexes().size()));
                    }
                }
            }
        }
        // JEI can list alternative results in one output slot. Do not encode
        // those variants as simultaneous processing byproducts.
        List<ItemStack> outputs = List.of(node.stack().copy());
        Identifier id = Identifier.tryParse(recipe.ref().registryId());
        // A stable signature avoids producing duplicate patterns for repeated tree nodes.
        String signature = recipe.ref().key() + Arrays.toString(Arrays.stream(inputs)
                .map(RecipeTreeData::ingredientKey).toArray()) + substitute;
        if (seen.add(signature)) plans.add(new Ae2PatternPlan(id, Arrays.asList(inputs), outputs, substitute));
    }
}
