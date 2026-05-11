package com.github.litroenade.maidbridge.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class MaidBridgeNetwork {
    private static final String VERSION = "1.0.0";

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
    }

    public static void sendToClientPlayer(CustomPacketPayload payload, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
