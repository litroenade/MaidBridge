package com.github.litroenade.maidbridge.maid.ai.chat;

import java.util.List;

/**
 * 缓存聊天窗口展示用的外部 agent 状态；真正路由仍由传输层的活动会话判定。
 */
public final class MaidExternalAgentDisplayState {
    private static volatile String activeAgentId = "";
    private static volatile List<String> agentIds = List.of();

    private MaidExternalAgentDisplayState() {
    }

    public static void replaceAgents(List<String> agents, String activeAgentId) {
        MaidExternalAgentDisplayState.activeAgentId = normalize(activeAgentId);
        agentIds = agents == null ? List.of() : agents.stream()
                .map(MaidExternalAgentDisplayState::normalize)
                .filter(agent -> !agent.isBlank())
                .distinct()
                .toList();
    }

    public static void clearActiveAgentId() {
        activeAgentId = "";
        agentIds = List.of();
    }

    public static String activeAgentId() {
        return activeAgentId;
    }

    public static List<String> agentIds() {
        return List.copyOf(agentIds);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
