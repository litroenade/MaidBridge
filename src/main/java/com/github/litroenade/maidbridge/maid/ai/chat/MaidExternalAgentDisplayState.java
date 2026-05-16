package com.github.litroenade.maidbridge.maid.ai.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存聊天窗口展示用的外部 agent 状态；真正路由仍由传输层的活动会话判定。
 */
public final class MaidExternalAgentDisplayState {
    private static final Map<UUID, String> ACTIVE_AGENT_BY_MAID = new ConcurrentHashMap<>();

    private MaidExternalAgentDisplayState() {
    }

    public static void replaceAgents(Map<String, String> activeAgentsByMaidUuid) {
        ACTIVE_AGENT_BY_MAID.clear();
        if (activeAgentsByMaidUuid == null) {
            return;
        }
        activeAgentsByMaidUuid.forEach((maidUuid, agentId) -> {
            var normalizedAgentId = normalize(agentId);
            var parsedMaidUuid = parseUuid(maidUuid);
            if (parsedMaidUuid != null && !normalizedAgentId.isBlank()) {
                ACTIVE_AGENT_BY_MAID.put(parsedMaidUuid, normalizedAgentId);
            }
        });
    }

    public static void clearActiveAgentId() {
        ACTIVE_AGENT_BY_MAID.clear();
    }

    public static boolean hasAgent(UUID maidUuid) {
        return maidUuid != null && ACTIVE_AGENT_BY_MAID.containsKey(maidUuid);
    }

    public static Map<UUID, String> activeAgentsByMaid() {
        return Map.copyOf(ACTIVE_AGENT_BY_MAID);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static UUID parseUuid(String value) {
        try {
            var normalized = normalize(value);
            return normalized.isBlank() ? null : UUID.fromString(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
