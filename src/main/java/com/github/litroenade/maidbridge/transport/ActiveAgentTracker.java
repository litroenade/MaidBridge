package com.github.litroenade.maidbridge.transport;

import com.github.litroenade.maidbridge.protocol.frame.BridgeSessionInitialize;

final class ActiveAgentTracker {
    private final Object lock = new Object();
    private String activeSessionId = "";

    void clear() {
        synchronized (lock) {
            activeSessionId = "";
        }
    }

    ClaimResult claim(
            WebSocketBridgeServer currentServer,
            WebSocketBridgeServer.Session session,
            BridgeSessionInitialize nextInitialize
    ) {
        synchronized (lock) {
            var activeSession = currentServer == null ? null : currentServer.session(activeSessionId).orElse(null);
            if (activeSessionId.isBlank()
                    || activeSession == null
                    || !BridgeRoutingRules.canReceiveAgentTurnRequests(activeSession.sessionInitialize())
                    || activeSessionId.equals(session.id())) {
                activeSessionId = session.id();
                return ClaimResult.accepted(null);
            }
            if (sameClient(activeSession.sessionInitialize(), nextInitialize)) {
                activeSessionId = session.id();
                return ClaimResult.accepted(activeSession);
            }
            return ClaimResult.rejected();
        }
    }

    WebSocketBridgeServer.Session activeSession(WebSocketBridgeServer currentServer) {
        if (currentServer == null) {
            return null;
        }
        String activeId;
        synchronized (lock) {
            activeId = activeSessionId;
        }
        var activeSession = currentServer.session(activeId).orElse(null);
        return activeSession != null && BridgeRoutingRules.canReceiveAgentTurnRequests(activeSession.sessionInitialize()) ? activeSession : null;
    }

    boolean owns(WebSocketBridgeServer.Session session) {
        synchronized (lock) {
            return !activeSessionId.isBlank() && activeSessionId.equals(session.id());
        }
    }

    boolean releaseIfOwned(WebSocketBridgeServer.Session session) {
        synchronized (lock) {
            if (session == null || activeSessionId.isBlank() || !activeSessionId.equals(session.id())) {
                return false;
            }
            activeSessionId = "";
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

    private static boolean sameNonBlank(String current, String next) {
        return current != null && next != null && !current.isBlank() && current.equals(next);
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
