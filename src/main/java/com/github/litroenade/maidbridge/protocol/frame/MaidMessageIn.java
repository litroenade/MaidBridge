package com.github.litroenade.maidbridge.protocol.frame;

public record MaidMessageIn(
        String id,
        String traceId,
        String maidUuid,
        String text,
        MaidClientInfo clientInfo,
        String turnId
) {
}
