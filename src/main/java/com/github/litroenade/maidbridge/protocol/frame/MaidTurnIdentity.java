package com.github.litroenade.maidbridge.protocol.frame;

public record MaidTurnIdentity(String maidUuid, String turnId, String requestId) {
    public MaidTurnIdentity {
        maidUuid = safeString(maidUuid);
        turnId = safeString(turnId);
        requestId = safeString(requestId);
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
