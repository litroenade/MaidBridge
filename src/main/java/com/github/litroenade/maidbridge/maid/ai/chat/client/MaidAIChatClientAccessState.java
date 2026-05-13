package com.github.litroenade.maidbridge.maid.ai.chat.client;

import com.github.litroenade.maidbridge.Config;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MaidAIChatClientAccessState {
    private static final Map<UUID, Boolean> CHAT_ONLY_BY_MAID = new ConcurrentHashMap<>();
    private static volatile String chatMode = Config.MAID_CHAT_MODE_NATIVE;
    private static volatile String activeAgentId = "";
    private static volatile List<String> agentIds = List.of();

    private MaidAIChatClientAccessState() {
    }

    public static void setAccessState(UUID maidUuid, boolean chatOnly) {
        CHAT_ONLY_BY_MAID.put(maidUuid, chatOnly);
    }

    public static void setBridgeAgentState(String chatMode, String activeAgentId, List<String> agentIds) {
        MaidAIChatClientAccessState.chatMode = chatMode == null || chatMode.isBlank() ? Config.MAID_CHAT_MODE_NATIVE : chatMode.trim();
        MaidAIChatClientAccessState.activeAgentId = activeAgentId == null ? "" : activeAgentId.trim();
        MaidAIChatClientAccessState.agentIds = agentIds == null ? List.of() : agentIds.stream()
                .map(agent -> agent == null ? "" : agent.trim())
                .filter(agent -> !agent.isBlank())
                .distinct()
                .toList();
    }

    public static boolean isChatOnly(UUID maidUuid) {
        return CHAT_ONLY_BY_MAID.getOrDefault(maidUuid, false);
    }

    public static boolean isChatOnly(EntityMaid maid) {
        return isChatOnly(maid.getUUID());
    }

    public static boolean isExternalAgentMode() {
        return Config.MAID_CHAT_MODE_EXTERNAL_AGENT.equals(chatMode);
    }

    public static String chatMode() {
        return chatMode;
    }

    public static String activeAgentId() {
        return activeAgentId;
    }

    public static List<String> agentIds() {
        return List.copyOf(agentIds);
    }
}
