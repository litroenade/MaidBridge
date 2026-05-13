package com.github.litroenade.maidbridge.network;

import com.github.litroenade.maidbridge.Config;
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
 * 聊天窗口模式菜单使用；只切换 MaidBridge 接管模式，不直接执行 agent 动作。
 */
public record SetExternalMaidAgentModePacket(
        int entityId,
        String chatMode
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetExternalMaidAgentModePacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidBridge.MODID, "set_external_maid_agent_mode")
    );
    public static final StreamCodec<ByteBuf, SetExternalMaidAgentModePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SetExternalMaidAgentModePacket decode(@NotNull ByteBuf byteBuf) {
            var buf = new FriendlyByteBuf(byteBuf);
            return new SetExternalMaidAgentModePacket(buf.readVarInt(), buf.readUtf());
        }

        @Override
        public void encode(@NotNull ByteBuf byteBuf, @NotNull SetExternalMaidAgentModePacket packet) {
            var buf = new FriendlyByteBuf(byteBuf);
            buf.writeVarInt(packet.entityId);
            buf.writeUtf(packet.chatMode);
        }
    };

    public SetExternalMaidAgentModePacket {
        chatMode = chatMode == null || chatMode.isBlank() ? Config.MAID_CHAT_MODE_NATIVE : chatMode.trim();
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetExternalMaidAgentModePacket packet, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            context.enqueueWork(() -> handle(packet, (ServerPlayer) context.player()));
        }
    }

    @SuppressWarnings("resource")
    private static void handle(SetExternalMaidAgentModePacket packet, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        // Level 由 Minecraft 生命周期管理，这里只查询实体，不能用 try-with-resources 关闭。
        Entity entity = player.level().getEntity(packet.entityId);
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive() || !MaidAIChatAccess.canEditSettings(maid, player)) {
            return;
        }
        Config.setMaidAgentTurnMode(packet.chatMode);
        MaidBridge.LOGGER.info(
                "玩家切换女仆 AI 聊天模式 player={} maidUuid={} mode={}",
                player.getScoreboardName(),
                maid.getUUID(),
                packet.chatMode
        );
        MaidBridgeNetwork.sendToClientPlayer(SyncMaidBridgeAgentStatePacket.current(), player);
        MaidBridgeNetwork.sendToClientPlayer(SyncMaidAIChatAttributionsPacket.from(maid, player), player);
    }
}
