package com.github.litroenade.maidbridge.maid.ai.chat;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MaidAIChatServerAccessState {
    private static final Map<UUID, UUID> CHAT_ONLY_MAID_BY_PLAYER = new ConcurrentHashMap<>();

    private MaidAIChatServerAccessState() {
    }

    public static void setChatOnly(ServerPlayer player, UUID maidUuid) {
        CHAT_ONLY_MAID_BY_PLAYER.put(player.getUUID(), maidUuid);
    }

    public static void clearChatOnly(ServerPlayer player) {
        clearChatOnly(player.getUUID());
    }

    public static void clearChatOnly(UUID playerUuid) {
        CHAT_ONLY_MAID_BY_PLAYER.remove(playerUuid);
    }

    public static void clearAll() {
        CHAT_ONLY_MAID_BY_PLAYER.clear();
    }

    public static boolean isChatOnly(ServerPlayer player) {
        return CHAT_ONLY_MAID_BY_PLAYER.containsKey(player.getUUID());
    }

    public static boolean isChatOnly(ServerPlayer player, UUID maidUuid) {
        return maidUuid != null && maidUuid.equals(CHAT_ONLY_MAID_BY_PLAYER.get(player.getUUID()));
    }
}
