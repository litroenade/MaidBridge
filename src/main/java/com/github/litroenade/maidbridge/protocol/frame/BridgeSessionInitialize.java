package com.github.litroenade.maidbridge.protocol.frame;

import java.util.List;

public record BridgeSessionInitialize(
        String id,
        String traceId,
        String clientName,
        String agentId,
        String maidUuid,
        List<String> roles,
        List<String> subscriptions
) {
    public BridgeSessionInitialize {
        id = safeString(id);
        traceId = safeString(traceId);
        clientName = safeString(clientName);
        agentId = safeString(agentId);
        maidUuid = safeString(maidUuid);
        roles = roles == null ? List.of() : roles.stream().map(BridgeSessionInitialize::safeString).filter(value -> !value.isBlank()).toList();
        subscriptions = subscriptions == null ? List.of() : subscriptions.stream().map(BridgeSessionInitialize::safeString).filter(value -> !value.isBlank()).toList();
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
