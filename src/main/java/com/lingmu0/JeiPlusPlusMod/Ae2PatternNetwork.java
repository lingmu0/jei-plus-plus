package com.lingmu0.JeiPlusPlusMod;

import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Optional play payload: old client-only features do not depend on this channel. */
public final class Ae2PatternNetwork {
    private static final Identifier CHANNEL = Identifier.fromNamespaceAndPath(JeiPlusPlus.MODID, "ae2_patterns");
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final int CHUNK_SIZE = 8;

    private Ae2PatternNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(Request.TYPE, Request.CODEC,
                (message, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) receive(player, message);
                }));
    }

    public static boolean available(Connection connection) {
        return connection != null && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, CHANNEL);
    }

    private static void receive(ServerPlayer player, Request request) {
        if (request.total < 1 || request.total > 48 || request.index < 0 || request.index >= request.total
                || request.plans.isEmpty() || request.plans.size() > CHUNK_SIZE
                || player.containerMenu.containerId != request.menuId) return;
        UUID id = player.getUUID();
        Pending pending = PENDING.get(id);
        if (pending == null || pending.batch != request.batch || pending.menuId != request.menuId
                || System.nanoTime() - pending.created > 10_000_000_000L) {
            if (request.index != 0) return;
            pending = new Pending(request.batch, request.menuId, request.total, request.force);
            PENDING.put(id, pending);
        }
        if (request.index != pending.next || request.total != pending.total || request.force != pending.force) {
            PENDING.remove(id);
            return;
        }
        pending.parts.addAll(request.plans);
        pending.next++;
        if (request.index + 1 == request.total) {
            PENDING.remove(id);
            Ae2PatternServer.create(player, pending.parts, pending.force);
        }
    }

    private static final class Pending {
        final int batch, menuId, total;
        final boolean force;
        final long created = System.nanoTime();
        final List<Ae2PatternPlan> parts = new ArrayList<>();
        int next;
        Pending(int batch, int menuId, int total, boolean force) {
            this.batch = batch;
            this.menuId = menuId;
            this.total = total;
            this.force = force;
        }
    }

    public record Request(int menuId, int batch, int index, int total, boolean force,
                          List<Ae2PatternPlan> plans) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
                StreamCodec.of((buffer, request) -> request.write(buffer), Request::read);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(menuId);
            buffer.writeInt(batch);
            buffer.writeVarInt(index);
            buffer.writeVarInt(total);
            buffer.writeBoolean(force);
            buffer.writeVarInt(plans.size());
            for (Ae2PatternPlan plan : plans) {
                buffer.writeBoolean(plan.recipeId() != null);
                if (plan.recipeId() != null) buffer.writeIdentifier(plan.recipeId());
                buffer.writeVarInt(plan.inputs().size());
                for (ItemStack stack : plan.inputs()) ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
                buffer.writeVarInt(plan.outputs().size());
                for (ItemStack stack : plan.outputs()) ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
                buffer.writeBoolean(plan.substitute());
            }
        }

        private static Request read(RegistryFriendlyByteBuf buffer) {
            int menuId = buffer.readVarInt();
            int batch = buffer.readInt();
            int index = buffer.readVarInt();
            int total = buffer.readVarInt();
            boolean force = buffer.readBoolean();
            int size = buffer.readVarInt();
            if (size < 0 || size > CHUNK_SIZE) throw new IllegalArgumentException("Invalid pattern chunk");
            List<Ae2PatternPlan> plans = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                Identifier recipeId = buffer.readBoolean() ? buffer.readIdentifier() : null;
                int inputCount = buffer.readVarInt();
                if (inputCount < 0 || inputCount > 9) throw new IllegalArgumentException("Invalid pattern inputs");
                List<ItemStack> inputs = new ArrayList<>(inputCount);
                for (int j = 0; j < inputCount; j++) inputs.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
                int outputCount = buffer.readVarInt();
                if (outputCount < 0 || outputCount > 3) throw new IllegalArgumentException("Invalid pattern outputs");
                List<ItemStack> outputs = new ArrayList<>(outputCount);
                for (int j = 0; j < outputCount; j++) outputs.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
                plans.add(new Ae2PatternPlan(recipeId, inputs, outputs, buffer.readBoolean()));
            }
            return new Request(menuId, batch, index, total, force, plans);
        }
    }
}
