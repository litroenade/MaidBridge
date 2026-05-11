package com.github.litroenade.maidbridge.protocol.frame;

import java.util.List;

public record BridgeSessionInitialize(
        String id,
        String traceId,
        String clientName,
        List<String> roles,
        List<String> subscriptions
) {
}
