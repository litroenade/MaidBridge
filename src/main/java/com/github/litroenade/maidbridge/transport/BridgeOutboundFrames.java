package com.github.litroenade.maidbridge.transport;

import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.github.litroenade.maidbridge.protocol.BridgeInboundParser;

final class BridgeOutboundFrames {
    private BridgeOutboundFrames() {
    }

    static OutboundFrame fromJson(String frame, TurnDropHandler turnDropHandler) {
        var routing = BridgeInboundParser.parseFrameRoutingLenient(frame, Integer.MAX_VALUE);
        if (routing.type().isBlank()) {
            return OutboundFrame.broadcast(frame);
        }
        Runnable dropHook = BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST.equals(routing.type()) ? () -> turnDropHandler.accept(routing.maidUuid(), routing.turnId(), routing.requestId()) : () -> {
        };
        return new OutboundFrame(
                frame,
                routing.type(),
                routing.requestId(),
                routing.maidUuid(),
                routing.turnId(),
                BridgeRoutingRules.targetRolesForType(routing.type()),
                BridgeRoutingRules.subscriptionsForType(routing.type()),
                dropHook
        );
    }

    @FunctionalInterface
    interface TurnDropHandler {
        void accept(String maidUuid, String turnId, String requestId);
    }
}
