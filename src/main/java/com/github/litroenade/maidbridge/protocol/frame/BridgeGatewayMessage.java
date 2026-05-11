package com.github.litroenade.maidbridge.protocol.frame;

import java.util.Map;

public record BridgeGatewayMessage(
        String id,
        String traceId,
        String endpointId,
        String routeScope,
        String plainText,
        String sourceEndpoint,
        String targetEndpoint,
        Map<String, Object> route,
        Map<String, Object> target,
        Map<String, Object> metadata
) {
}
