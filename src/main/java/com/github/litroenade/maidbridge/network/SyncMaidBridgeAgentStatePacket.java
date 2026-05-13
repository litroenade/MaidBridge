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

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端向客户端同步聊天窗口模式菜单需要的 MaidBridge 运行状态。
 */
public record SyncMaidBridgeAgentStatePacket(
        String chatMode,
        String activeAgentId,
        List<String> agentIds
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncMaidBridgeAgentStatePacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidBridge.MODID, "sync_maidbridge_agent_state")
    );
    public static final StreamCodec<ByteBuf, SyncMaidBridgeAgentStatePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SyncMaidBridgeAgentStatePacket decode(@NotNull ByteBuf byteBuf) {
            var buf = new FriendlyByteBuf(byteBuf);
            var chatMode = buf.readUtf();
            var activeAgentId = buf.readUtf();
            var size = buf.readVarInt();
            var agentIds = new ArrayList<String>(size);
            for (int i = 0; i < size; i++) {
                agentIds.add(buf.readUtf());
            }
            return new SyncMaidBridgeAgentStatePacket(chatMode, activeAgentId, agentIds);
        }

        @Override
        public void encode(@NotNull ByteBuf byteBuf, @NotNull SyncMaidBridgeAgentStatePacket packet) {
            var buf = new FriendlyByteBuf(byteBuf);
            buf.writeUtf(packet.chatMode);
            buf.writeUtf(packet.activeAgentId);
            buf.writeVarInt(packet.agentIds.size());
            for (var agentId : packet.agentIds) {
                buf.writeUtf(agentId);
            }
        }
    };

    public SyncMaidBridgeAgentStatePacket {
        chatMode = chatMode == null || chatMode.isBlank() ? Config.MAID_CHAT_MODE_NATIVE : chatMode.trim();
        activeAgentId = activeAgentId == null ? "" : activeAgentId.trim();
        agentIds = agentIds == null ? List.of() : agentIds.stream()
                .map(agent -> agent == null ? "" : agent.trim())
                .filter(agent -> !agent.isBlank())
                .distinct()
                .toList();
    }

    public static SyncMaidBridgeAgentStatePacket current() {
        return new SyncMaidBridgeAgentStatePacket(
                Config.maidAgentTurnMode,
                MaidExternalAgentDisplayState.activeAgentId(),
                MaidExternalAgentDisplayState.agentIds()
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
