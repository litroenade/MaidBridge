package com.github.litroenade.maidbridge.network;

import java.util.Objects;
import java.util.function.Consumer;

public final class MaidBridgeClientPayloadDispatch {
    private static Consumer<SyncMaidAIChatAttributionsPacket> syncMaidAIChatAttributionsHandler = ignored -> {
    };
    private static Consumer<OpenReadonlyMaidAIChatPacket> openReadonlyMaidAIChatHandler = ignored -> {
    };
    private static Consumer<SyncMaidBridgeAgentStatePacket> syncMaidBridgeAgentStateHandler = ignored -> {
    };
    private static Consumer<SyncExternalEmojiPacket> syncExternalEmojiHandler = ignored -> {
    };

    private MaidBridgeClientPayloadDispatch() {
    }

    public static void register(
            Consumer<SyncMaidAIChatAttributionsPacket> syncMaidAIChatAttributionsHandler,
            Consumer<OpenReadonlyMaidAIChatPacket> openReadonlyMaidAIChatHandler,
            Consumer<SyncMaidBridgeAgentStatePacket> syncMaidBridgeAgentStateHandler,
            Consumer<SyncExternalEmojiPacket> syncExternalEmojiHandler
    ) {
        MaidBridgeClientPayloadDispatch.syncMaidAIChatAttributionsHandler =
                Objects.requireNonNull(syncMaidAIChatAttributionsHandler, "syncMaidAIChatAttributionsHandler");
        MaidBridgeClientPayloadDispatch.openReadonlyMaidAIChatHandler =
                Objects.requireNonNull(openReadonlyMaidAIChatHandler, "openReadonlyMaidAIChatHandler");
        MaidBridgeClientPayloadDispatch.syncMaidBridgeAgentStateHandler =
                Objects.requireNonNull(syncMaidBridgeAgentStateHandler, "syncMaidBridgeAgentStateHandler");
        MaidBridgeClientPayloadDispatch.syncExternalEmojiHandler =
                Objects.requireNonNull(syncExternalEmojiHandler, "syncExternalEmojiHandler");
    }

    public static void handle(SyncMaidAIChatAttributionsPacket packet) {
        syncMaidAIChatAttributionsHandler.accept(packet);
    }

    public static void handle(OpenReadonlyMaidAIChatPacket packet) {
        openReadonlyMaidAIChatHandler.accept(packet);
    }

    public static void handle(SyncMaidBridgeAgentStatePacket packet) {
        syncMaidBridgeAgentStateHandler.accept(packet);
    }

    public static void handle(SyncExternalEmojiPacket packet) {
        syncExternalEmojiHandler.accept(packet);
    }

}
