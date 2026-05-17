package com.github.litroenade.maidbridge.protocol.frame;

import java.util.Map;

public record ServerChatMessage(
        String id,
        String traceId,
        String kind,
        String text,
        String speakerId,
        String speakerName,
        String roomId,
        String roomName,
        String sourceEndpoint,
        String targetEndpoint,
        Map<String, Object> metadata
) {
    public boolean isSystemBroadcast() {
        return "system".equals(kind);
    }
}
