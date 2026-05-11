package com.github.litroenade.maidbridge.protocol.frame;

public record BridgeFrameRouting(String type, String requestId, String maidUuid, String turnId) {
    public BridgeFrameRouting {
        type = safeString(type);
        requestId = safeString(requestId);
        maidUuid = safeString(maidUuid);
        turnId = safeString(turnId);
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
