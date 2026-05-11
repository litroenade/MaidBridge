package com.github.litroenade.maidbridge.transport;

final class ActiveAgentTracker {
    private final Object lock = new Object();
    private String activeSessionId = "";

    void clear() {
        synchronized (lock) {
            activeSessionId = "";
        }
    }

    boolean claim(WebSocketBridgeServer currentServer, WebSocketBridgeServer.Session session) {
        synchronized (lock) {
            var activeSession = currentServer == null ? null : currentServer.session(activeSessionId).orElse(null);
            if (activeSessionId.isBlank()
                    || activeSession == null
                    || !BridgeRoutingRules.canReceiveAgentTurnRequests(activeSession.sessionInitialize())
                    || activeSessionId.equals(session.id())) {
                activeSessionId = session.id();
                return true;
            }
            return false;
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
}
