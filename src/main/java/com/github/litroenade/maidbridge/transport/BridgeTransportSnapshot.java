package com.github.litroenade.maidbridge.transport;

import java.util.List;

public record BridgeTransportSnapshot(
        boolean started,
        boolean webSocketEnabled,
        boolean webSocketRunning,
        String host,
        int port,
        String path,
        int connectedClients,
        String activeAgentSessionId,
        List<Client> clients,
        List<QueuedFrame> queuedFrames,
        Counters counters
) {
    public record Client(
            String sessionId,
            String clientName,
            String agentId,
            String maidUuid,
            List<String> roles,
            List<String> subscriptions,
            boolean activeAgent,
            boolean open
    ) {
    }

    public record QueuedFrame(String type, int count) {
    }

    public record Counters(
            int queuedFrames,
            long droppedFrames,
            long inboundGatewayMessageFrames,
            long duplicateInboundGatewayFrames,
            long malformedInboundFrames,
            long outboundSendFailures,
            long maidTurnDrops,
            long maidTurnDisconnects,
            long maidTurnDeadlines
    ) {
    }
}
