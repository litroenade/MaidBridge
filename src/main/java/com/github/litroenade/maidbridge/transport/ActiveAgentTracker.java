package com.github.litroenade.maidbridge.transport;

import com.github.litroenade.maidbridge.protocol.frame.BridgeSessionInitialize;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class ActiveAgentTracker {
    private final Object lock = new Object();
    private final Map<String, String> activeSessionIdByMaidUuid = new HashMap<>();

    void clear() {
        synchronized (lock) {
            activeSessionIdByMaidUuid.clear();
        }
    }

    ClaimResult claim(
            WebSocketBridgeServer currentServer,
            WebSocketBridgeServer.Session session,
            BridgeSessionInitialize nextInitialize
    ) {
        if (session == null || nextInitialize == null) {
            return ClaimResult.rejected();
        }
        synchronized (lock) {
            var maidUuid = normalize(nextInitialize.maidUuid());
            if (maidUuid.isBlank()) {
                return ClaimResult.rejected();
            }
            var activeSessionId = activeSessionIdByMaidUuid.getOrDefault(maidUuid, "");
            var activeSession = currentServer == null ? null : currentServer.session(activeSessionId).orElse(null);
            var activeInitialize = activeSession == null ? null : activeSession.sessionInitialize();
            if (activeSessionId.isBlank()
                    || activeSession == null
                    || !BridgeRoutingRules.canReceiveAgentTurnRequests(activeInitialize)
                    || activeSessionId.equals(session.id())) {
                activeSessionIdByMaidUuid.put(maidUuid, session.id());
                return ClaimResult.accepted(null);
            }
            if (sameClient(activeInitialize, nextInitialize)) {
                activeSessionIdByMaidUuid.put(maidUuid, session.id());
                return ClaimResult.accepted(activeSession);
            }
            return ClaimResult.rejected();
        }
    }

    WebSocketBridgeServer.Session activeSession(WebSocketBridgeServer currentServer, String maidUuid) {
        if (currentServer == null) {
            return null;
        }
        String activeId;
        synchronized (lock) {
            activeId = activeSessionIdByMaidUuid.getOrDefault(normalize(maidUuid), "");
        }
        if (activeId.isBlank()) {
            return null;
        }
        var activeSession = currentServer.session(activeId).orElse(null);
        if (activeSession == null) {
            return null;
        }
        var activeInitialize = activeSession.sessionInitialize();
        return BridgeRoutingRules.canReceiveAgentTurnRequests(activeInitialize) ? activeSession : null;
    }

    Map<String, WebSocketBridgeServer.Session> activeSessions(WebSocketBridgeServer currentServer) {
        if (currentServer == null) {
            return Map.of();
        }
        var sessions = new HashMap<String, WebSocketBridgeServer.Session>();
        synchronized (lock) {
            activeSessionIdByMaidUuid.forEach((maidUuid, sessionId) -> {
                var activeSession = currentServer.session(sessionId).orElse(null);
                var activeInitialize = activeSession == null ? null : activeSession.sessionInitialize();
                if (BridgeRoutingRules.canReceiveAgentTurnRequests(activeInitialize)) {
                    sessions.put(maidUuid, activeSession);
                }
            });
        }
        return Map.copyOf(sessions);
    }

    Set<String> activeSessionIds() {
        synchronized (lock) {
            return Set.copyOf(activeSessionIdByMaidUuid.values());
        }
    }

    boolean owns(WebSocketBridgeServer.Session session, String maidUuid) {
        synchronized (lock) {
            var activeSessionId = activeSessionIdByMaidUuid.getOrDefault(normalize(maidUuid), "");
            return session != null && !activeSessionId.isBlank() && activeSessionId.equals(session.id());
        }
    }

    boolean releaseIfOwned(WebSocketBridgeServer.Session session) {
        synchronized (lock) {
            if (session == null || !activeSessionIdByMaidUuid.containsValue(session.id())) {
                return false;
            }
            activeSessionIdByMaidUuid.entrySet().removeIf(entry -> session.id().equals(entry.getValue()));
            return true;
        }
    }

    private static boolean sameClient(BridgeSessionInitialize current, BridgeSessionInitialize next) {
        if (current == null || next == null) {
            return false;
        }
        if (sameNonBlank(current.id(), next.id())) {
            return true;
        }
        return sameNonBlank(current.agentId(), next.agentId());
    }

    private static String normalize(String value) {
        return BridgeRoutingRules.normalizeMaidUuid(value);
    }

    private static boolean sameNonBlank(String current, String next) {
        return current != null && !current.isBlank() && current.equals(next);
    }

    record ClaimResult(boolean accepted, WebSocketBridgeServer.Session replacedSession) {
        static ClaimResult accepted(WebSocketBridgeServer.Session replacedSession) {
            return new ClaimResult(true, replacedSession);
        }

        static ClaimResult rejected() {
            return new ClaimResult(false, null);
        }
    }
}
