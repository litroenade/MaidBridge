package com.github.litroenade.maidbridge.transport;

import com.github.litroenade.maidbridge.protocol.frame.BridgeSessionInitialize;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;

import java.util.List;

/**
 * 根据客户端声明的 roles/subscriptions 判断一帧是否允许投递。
 */
final class BridgeRoutingRules {
    private BridgeRoutingRules() {
    }

    static boolean canReceive(WebSocketBridgeServer.Session session, OutboundFrame frame) {
        if (session == null || frame == null) {
            return false;
        }
        var sessionInitialize = session.sessionInitialize();
        if (sessionInitialize == null) {
            return false;
        }
        var roleAllowed = frame.targetRoles().isEmpty() || frame.targetRoles().stream().anyMatch(session::hasRole);
        if (!roleAllowed) {
            return false;
        }
        if (BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST.equals(frame.type())
                && !sameNonBlank(sessionInitialize.maidUuid(), frame.maidUuid())) {
            return false;
        }
        if (frame.subscriptions().isEmpty()) {
            return true;
        }
        var subscriptions = sessionInitialize.subscriptions();
        return frame.subscriptions().stream().anyMatch(type -> subscriptions.stream().anyMatch(subscription -> subscriptionMatches(subscription, type)));
    }

    static boolean canReceiveAgentTurnRequests(BridgeSessionInitialize sessionInitialize) {
        if (sessionInitialize == null) {
            return false;
        }
        var maidUuid = sessionInitialize.maidUuid();
        return sessionInitialize.roles().contains("agent")
                && maidUuid != null
                && !maidUuid.isBlank()
                && sessionInitialize.subscriptions().stream().anyMatch(subscription -> subscriptionMatches(subscription, BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST));
    }

    static List<String> maidApiRoles(String type) {
        if (type.startsWith(BridgeProtocol.PREFIX_MAID_API_QUERY)) {
            return List.of("maid_api_query");
        }
        if (type.startsWith(BridgeProtocol.PREFIX_MAID_API_CALL)) {
            return List.of("maid_api_call");
        }
        return List.of();
    }

    static List<String> targetRolesForType(String type) {
        if (type.startsWith(BridgeProtocol.PREFIX_MAID_AGENT)) {
            return List.of("agent");
        }
        if (type.startsWith(BridgeProtocol.PREFIX_SERVER_CHAT) || type.startsWith(BridgeProtocol.PREFIX_MAID_MESSAGE)) {
            return List.of("message");
        }
        if (type.startsWith(BridgeProtocol.PREFIX_MAID_API_QUERY)) {
            return List.of("maid_api_query");
        }
        if (type.startsWith(BridgeProtocol.PREFIX_MAID_API_CALL)) {
            return List.of("maid_api_call");
        }
        if (type.startsWith(BridgeProtocol.PREFIX_MAID_AI)
                || type.startsWith(BridgeProtocol.PREFIX_MAID_API_REGISTRY)
                || type.startsWith(BridgeProtocol.PREFIX_MAIDBRIDGE_SERVER)) {
            return List.of("debug");
        }
        return List.of();
    }

    static List<String> subscriptionsForType(String type) {
        return type.isBlank() ? List.of() : List.of(type);
    }

    private static boolean subscriptionMatches(String subscription, String type) {
        if (subscription == null || subscription.isBlank()) {
            return false;
        }
        if (subscription.equals(type)) {
            return true;
        }
        if (subscription.endsWith("*")) {
            return type.startsWith(subscription.substring(0, subscription.length() - 1));
        }
        return false;
    }

    private static boolean sameNonBlank(String left, String right) {
        return left != null && !left.isBlank() && left.equals(right);
    }
}
