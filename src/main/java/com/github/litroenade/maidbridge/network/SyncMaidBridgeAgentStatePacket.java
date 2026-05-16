package com.github.litroenade.maidbridge.network;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidExternalAgentDisplayState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端向客户端同步聊天窗口模式菜单需要的 MaidBridge 运行状态。
 */
public record SyncMaidBridgeAgentStatePacket(
        String chatMode,
        Map<UUID, String> activeAgentIds
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncMaidBridgeAgentStatePacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidBridge.MODID, "sync_maidbridge_agent_state")
    );
    public static final StreamCodec<ByteBuf, SyncMaidBridgeAgentStatePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SyncMaidBridgeAgentStatePacket decode(@NotNull ByteBuf byteBuf) {
            var buf = new FriendlyByteBuf(byteBuf);
            var chatMode = buf.readUtf();
            var size = buf.readVarInt();
            var activeAgentIds = new LinkedHashMap<UUID, String>();
            for (int i = 0; i < size; i++) {
                activeAgentIds.put(buf.readUUID(), buf.readUtf());
            }
            return new SyncMaidBridgeAgentStatePacket(chatMode, activeAgentIds);
        }

        @Override
        public void encode(@NotNull ByteBuf byteBuf, @NotNull SyncMaidBridgeAgentStatePacket packet) {
            var buf = new FriendlyByteBuf(byteBuf);
            buf.writeUtf(packet.chatMode);
            buf.writeVarInt(packet.activeAgentIds.size());
            for (var entry : packet.activeAgentIds.entrySet()) {
                buf.writeUUID(entry.getKey());
                buf.writeUtf(entry.getValue());
            }
        }
    };

    public SyncMaidBridgeAgentStatePacket {
        chatMode = chatMode == null || chatMode.isBlank() ? Config.MAID_CHAT_MODE_NATIVE : chatMode.trim();
        var normalized = new LinkedHashMap<UUID, String>();
        if (activeAgentIds != null) {
            activeAgentIds.forEach((maidUuid, agentId) -> {
                var normalizedAgentId = agentId == null ? "" : agentId.trim();
                if (maidUuid != null && !normalizedAgentId.isBlank()) {
                    normalized.put(maidUuid, normalizedAgentId);
                }
            });
        }
        activeAgentIds = Map.copyOf(normalized);
    }

    public static SyncMaidBridgeAgentStatePacket current() {
        return new SyncMaidBridgeAgentStatePacket(
                Config.maidAgentTurnMode,
                MaidExternalAgentDisplayState.activeAgentsByMaid()
        );
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncMaidBridgeAgentStatePacket packet, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> MaidBridgeClientPayloadDispatch.handle(packet));
        }
    }
}
