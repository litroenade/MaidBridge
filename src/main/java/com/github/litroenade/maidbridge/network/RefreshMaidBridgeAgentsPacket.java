package com.github.litroenade.maidbridge.network;

import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatAccess;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 聊天窗口刷新按钮使用；只重发当前 agent 状态，避免从聊天 UI 断开正在接管的外部连接。
 */
public record RefreshMaidBridgeAgentsPacket(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RefreshMaidBridgeAgentsPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidBridge.MODID, "refresh_maidbridge_agents")
    );
    public static final StreamCodec<ByteBuf, RefreshMaidBridgeAgentsPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull RefreshMaidBridgeAgentsPacket decode(@NotNull ByteBuf byteBuf) {
            return new RefreshMaidBridgeAgentsPacket(new FriendlyByteBuf(byteBuf).readVarInt());
        }

        @Override
        public void encode(@NotNull ByteBuf byteBuf, @NotNull RefreshMaidBridgeAgentsPacket packet) {
            new FriendlyByteBuf(byteBuf).writeVarInt(packet.entityId);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RefreshMaidBridgeAgentsPacket packet, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            context.enqueueWork(() -> handle(packet, (ServerPlayer) context.player()));
        }
    }

    @SuppressWarnings("resource")
    private static void handle(RefreshMaidBridgeAgentsPacket packet, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        // Level 由 Minecraft 生命周期管理，这里只查询实体，不能用 try-with-resources 关闭。
        Entity entity = player.level().getEntity(packet.entityId);
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive() || !MaidAIChatAccess.canEditSettings(maid, player)) {
            return;
        }
        MaidBridgeNetwork.sendToClientPlayer(SyncMaidBridgeAgentStatePacket.current(), player);
    }
}
