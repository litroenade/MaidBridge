package com.github.litroenade.maidbridge.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Objects;

public final class MaidBridgeNetwork {
    private static final String VERSION = "1.0.0";
    private static Runnable webSocketRestartHandler = () -> {
    };

    private MaidBridgeNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(VERSION);
        registrar.playToClient(
                SyncMaidAIChatAttributionsPacket.TYPE,
                SyncMaidAIChatAttributionsPacket.STREAM_CODEC,
                SyncMaidAIChatAttributionsPacket::handle
        );
        registrar.playToClient(
                OpenReadonlyMaidAIChatPacket.TYPE,
                OpenReadonlyMaidAIChatPacket.STREAM_CODEC,
                OpenReadonlyMaidAIChatPacket::handle
        );
        registrar.playToClient(
                SyncMaidBridgeAgentStatePacket.TYPE,
                SyncMaidBridgeAgentStatePacket.STREAM_CODEC,
                SyncMaidBridgeAgentStatePacket::handle
        );
        registrar.playToClient(
                SyncExternalEmojiPacket.TYPE,
                SyncExternalEmojiPacket.STREAM_CODEC,
                SyncExternalEmojiPacket::handle
        );
        registrar.playToServer(
                SetExternalMaidAgentModePacket.TYPE,
                SetExternalMaidAgentModePacket.STREAM_CODEC,
                SetExternalMaidAgentModePacket::handle
        );
        registrar.playToServer(
                RefreshMaidBridgeAgentsPacket.TYPE,
                RefreshMaidBridgeAgentsPacket.STREAM_CODEC,
                RefreshMaidBridgeAgentsPacket::handle
        );
    }

    public static void setWebSocketRestartHandler(Runnable restartHandler) {
        webSocketRestartHandler = Objects.requireNonNull(restartHandler, "restartHandler");
    }

    public static void restartWebSocketFromClient() {
        webSocketRestartHandler.run();
    }

    public static void sendToClientPlayer(CustomPacketPayload payload, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }
}
