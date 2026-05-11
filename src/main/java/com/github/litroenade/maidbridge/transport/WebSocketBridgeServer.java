package com.github.litroenade.maidbridge.transport;

import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.protocol.frame.BridgeSessionInitialize;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class WebSocketBridgeServer {
    private static final int CLOSE_NORMAL = 1000;
    private static final int CLOSE_POLICY_VIOLATION = 1008;
    private static final int CLOSE_TOO_BIG = 1009;
    private static final long STARTUP_TIMEOUT_MS = 2_000L;

    private final String host;
    private final int port;
    private final String path;
    private final String accessToken;
    private final int maxMessageBytes;
    private final BiConsumer<Session, String> inboundConsumer;
    private final Consumer<Session> closeConsumer;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<WebSocket, Session> sessionsByConnection = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final CountDownLatch startupLatch = new CountDownLatch(1);
    private volatile RuntimeException startupFailure;
    private Server server;

    WebSocketBridgeServer(
            String host,
            int port,
            String path,
            String accessToken,
            int maxMessageBytes,
            BiConsumer<Session, String> inboundConsumer,
            Consumer<Session> closeConsumer
    ) {
        this.host = host == null || host.isBlank() ? "127.0.0.1" : host.strip();
        this.port = Math.max(1, Math.min(65535, port));
        this.path = normalizePath(path);
        this.accessToken = accessToken == null ? "" : accessToken;
        this.maxMessageBytes = Math.max(1024, maxMessageBytes);
        this.inboundConsumer = inboundConsumer;
        this.closeConsumer = closeConsumer == null ? session -> {
        } : closeConsumer;
    }

    void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        server = new Server(new InetSocketAddress(host, port));
        try {
            server.start();
            if (!startupLatch.await(STARTUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                stop();
                throw new IllegalStateException("启动 MaidBridge WebSocket 服务超时");
            }
            if (startupFailure != null) {
                stop();
                throw startupFailure;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running.set(false);
            server = null;
            throw new IllegalStateException("启动 MaidBridge WebSocket 服务时被中断", exception);
        } catch (RuntimeException exception) {
            running.set(false);
            server = null;
            throw new IllegalStateException("启动 MaidBridge WebSocket 服务失败", exception);
        }
    }

    void stop() {
        running.set(false);
        for (Session session : List.copyOf(sessions.values())) {
            session.close();
        }
        sessions.clear();
        sessionsByConnection.clear();
        if (server != null) {
            try {
                server.stop(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                MaidBridge.LOGGER.debug("停止 MaidBridge WebSocket 服务失败", exception);
            }
            server = null;
        }
    }

    boolean isStarted() {
        return running.get() && server != null;
    }

    int connectedClients() {
        return (int) sessions.values().stream().filter(Session::isOpen).count();
    }

    List<Session> sessions() {
        return List.copyOf(sessions.values());
    }

    List<BridgeTransportSnapshot.Client> clientSnapshots(String activeAgentSessionId) {
        return sessions().stream()
                .map(session -> session.snapshot(session.id().equals(activeAgentSessionId)))
                .toList();
    }

    void send(Session session, String frame) {
        if (session != null) {
            session.sendText(frame);
        }
    }

    Optional<Session> session(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(sessionId));
    }

    private static String normalizePath(String path) {
        var normalized = path == null || path.isBlank() ? "/maidbridge" : path.strip();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private boolean isAuthorized(ClientHandshake handshake) {
        if (!path.equals(handshake.getResourceDescriptor())) {
            return false;
        }
        return isBearerAuthorized(handshake.getFieldValue("Authorization"), accessToken);
    }

    private static boolean isBearerAuthorized(String authorizationHeader, String configuredToken) {
        if (configuredToken == null || configuredToken.isBlank()) {
            return true;
        }
        if (authorizationHeader == null) {
            return false;
        }
        // 容忍 scheme 大小写与首尾空白；token 本身保持精确比对，避免误放宽鉴权。
        var trimmed = authorizationHeader.trim();
        var spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex <= 0) {
            return false;
        }
        var scheme = trimmed.substring(0, spaceIndex);
        var token = trimmed.substring(spaceIndex + 1).trim();
        return "bearer".equalsIgnoreCase(scheme) && configuredToken.equals(token);
    }

    private final class Server extends org.java_websocket.server.WebSocketServer {
        private Server(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(WebSocket connection, ClientHandshake handshake) {
            if (!isAuthorized(handshake)) {
                connection.close(CLOSE_POLICY_VIOLATION, "MaidBridge WebSocket 未授权");
                return;
            }
            var session = new Session(connection);
            sessions.put(session.id(), session);
            sessionsByConnection.put(connection, session);
        }

        @Override
        public void onClose(WebSocket connection, int code, String reason, boolean remote) {
            var session = sessionsByConnection.remove(connection);
            if (session != null) {
                sessions.remove(session.id());
                closeConsumer.accept(session);
            }
        }

        @Override
        public void onMessage(WebSocket connection, String message) {
            var session = sessionsByConnection.get(connection);
            if (session == null) {
                connection.close(CLOSE_POLICY_VIOLATION, "未知 MaidBridge 会话");
                return;
            }
            if (message.getBytes(StandardCharsets.UTF_8).length > maxMessageBytes) {
                MaidBridge.LOGGER.warn("拒绝过大的 MaidBridge 入站帧 sessionId={} maxBytes={}", session.id(), maxMessageBytes);
                connection.close(CLOSE_TOO_BIG, "MaidBridge 帧过大");
                return;
            }
            inboundConsumer.accept(session, message);
        }

        @Override
        public void onError(WebSocket connection, Exception exception) {
            if (startupLatch.getCount() > 0) {
                startupFailure = new IllegalStateException("启动 MaidBridge WebSocket 服务失败", exception);
                running.set(false);
                startupLatch.countDown();
            }
            MaidBridge.LOGGER.debug("MaidBridge WebSocket 会话异常", exception);
        }

        @Override
        public void onStart() {
            startupLatch.countDown();
            MaidBridge.LOGGER.info("MaidBridge WebSocket 服务已监听 ws://{}:{}{}", host, port, path);
        }
    }

    static final class Session {
        private final String id = UUID.randomUUID().toString();
        private final WebSocket connection;
        private volatile BridgeSessionInitialize sessionInitialize;

        private Session(WebSocket connection) {
            this.connection = connection;
        }

        String id() {
            return id;
        }

        BridgeSessionInitialize sessionInitialize() {
            return sessionInitialize;
        }

        void setSessionInitialize(BridgeSessionInitialize sessionInitialize) {
            this.sessionInitialize = sessionInitialize;
        }

        boolean hasRole(String role) {
            var currentSessionInitialize = sessionInitialize;
            return currentSessionInitialize != null && currentSessionInitialize.roles().contains(role);
        }

        boolean isOpen() {
            return connection != null && connection.isOpen();
        }

        BridgeTransportSnapshot.Client snapshot(boolean activeAgent) {
            var currentSessionInitialize = sessionInitialize;
            return new BridgeTransportSnapshot.Client(
                    id,
                    currentSessionInitialize == null ? "" : currentSessionInitialize.clientName(),
                    currentSessionInitialize == null ? List.of() : List.copyOf(currentSessionInitialize.roles()),
                    currentSessionInitialize == null ? List.of() : List.copyOf(currentSessionInitialize.subscriptions()),
                    activeAgent,
                    isOpen()
            );
        }

        private void sendText(String frame) {
            if (isOpen()) {
                connection.send(frame);
            }
        }

        private void close() {
            if (connection != null) {
                connection.close(CLOSE_NORMAL, "server stopping");
            }
        }
    }

}
