package com.github.litroenade.maidbridge.transport;

import java.util.List;

record OutboundFrame(
        String rawFrame,
        String type,
        String requestId,
        String maidUuid,
        String turnId,
        List<String> targetRoles,
        List<String> subscriptions,
        Runnable dropHook
) {
    OutboundFrame {
        if (rawFrame == null || rawFrame.isBlank()) {
            throw new IllegalArgumentException("rawFrame 不能为空");
        }
        type = safeString(type);
        requestId = safeString(requestId);
        maidUuid = safeString(maidUuid);
        turnId = safeString(turnId);
        targetRoles = List.copyOf(targetRoles == null ? List.of() : targetRoles);
        subscriptions = List.copyOf(subscriptions == null ? List.of() : subscriptions);
        dropHook = dropHook == null ? () -> {
        } : dropHook;
    }

    static OutboundFrame broadcast(String rawFrame) {
        return new OutboundFrame(rawFrame, "", "", "", "", List.of(), List.of(), () -> {
        });
    }

    void onDropped() {
        dropHook.run();
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
