package com.github.litroenade.maidbridge.maid.ai.chat.client;

import com.github.litroenade.maidbridge.Config;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MaidAIChatClientAccessState {
    private static final Map<UUID, Boolean> CHAT_ONLY_BY_MAID = new ConcurrentHashMap<>();
    private static final Map<UUID, String> ACTIVE_AGENT_BY_MAID = new ConcurrentHashMap<>();
    private static volatile String chatMode = Config.MAID_CHAT_MODE_NATIVE;

    private MaidAIChatClientAccessState() {
    }

    public static void setAccessState(UUID maidUuid, boolean chatOnly) {
        CHAT_ONLY_BY_MAID.put(maidUuid, chatOnly);
    }

    public static void setBridgeAgentState(String chatMode, Map<UUID, String> activeAgentIds) {
        MaidAIChatClientAccessState.chatMode = chatMode == null || chatMode.isBlank() ? Config.MAID_CHAT_MODE_NATIVE : chatMode.trim();
        ACTIVE_AGENT_BY_MAID.clear();
        if (activeAgentIds == null) {
            return;
        }
        activeAgentIds.forEach((maidUuid, agentId) -> {
            var normalizedAgentId = agentId == null ? "" : agentId.trim();
            if (maidUuid != null && !normalizedAgentId.isBlank()) {
                ACTIVE_AGENT_BY_MAID.put(maidUuid, normalizedAgentId);
            }
        });
    }

    public static boolean isChatOnly(UUID maidUuid) {
        return CHAT_ONLY_BY_MAID.getOrDefault(maidUuid, false);
    }

    public static boolean isChatOnly(EntityMaid maid) {
        return isChatOnly(maid.getUUID());
    }

    public static boolean isExternalAgentMode(EntityMaid maid) {
        return Config.MAID_CHAT_MODE_EXTERNAL_AGENT.equals(chatMode);
    }

    public static String chatMode(EntityMaid maid) {
        return chatMode;
    }

    public static String activeAgentId(EntityMaid maid) {
        return maid == null ? "" : ACTIVE_AGENT_BY_MAID.getOrDefault(maid.getUUID(), "");
    }

    public static List<String> agentIds(EntityMaid maid) {
        var activeAgentId = activeAgentId(maid);
        return activeAgentId.isBlank() ? List.of() : List.of(activeAgentId);
    }
}
