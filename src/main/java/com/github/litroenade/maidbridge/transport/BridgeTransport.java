package com.github.litroenade.maidbridge.transport;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.maid.api.MaidApiRequestDispatcher;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidExternalAgentDisplayState;
import com.github.litroenade.maidbridge.maid.message.MaidMessageDispatcher;
import com.github.litroenade.maidbridge.maid.turn.MaidAgentTurnCompleteDispatcher;
import com.github.litroenade.maidbridge.maid.turn.MaidExternalTurnGuard;
import com.github.litroenade.maidbridge.network.MaidBridgeNetwork;
import com.github.litroenade.maidbridge.network.SyncMaidBridgeAgentStatePacket;
import com.github.litroenade.maidbridge.protocol.BridgeFrameBuilder;
import com.github.litroenade.maidbridge.protocol.BridgeInboundParser;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.github.litroenade.maidbridge.protocol.frame.BridgeFrameIdentity;
import com.github.litroenade.maidbridge.protocol.frame.BridgeGatewayMessage;
import com.github.litroenade.maidbridge.protocol.frame.MaidTurnIdentity;
import com.github.litroenade.maidbridge.trace.AiChainEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 传输层入口。
 * <p>传输层只做会话、角色、路由和领域响应；女仆副作用由各业务分发器在服务端线程执行。</p>
 */
public final class BridgeTransport {
    private static final int MAX_PROCESSED_INBOUND_IDS = 512;

    private final OutboundQueue outboundQueue = new OutboundQueue();
    private final InboundGatewayTracker inboundGatewayTracker = new InboundGatewayTracker(MAX_PROCESSED_INBOUND_IDS);
    private final AtomicLong inboundGatewayMessageFrames = new AtomicLong();
    private final AtomicLong duplicateInboundGatewayFrames = new AtomicLong();
    private final AtomicLong malformedInboundFrames = new AtomicLong();
    private final AtomicLong outboundSendFailures = new AtomicLong();
    private final AtomicLong maidTurnDrops = new AtomicLong();
    private final AtomicLong maidTurnDisconnects = new AtomicLong();
    private final AtomicLong maidTurnDeadlines = new AtomicLong();
    private final ActiveAgentTracker activeAgentTracker = new ActiveAgentTracker();
    private WebSocketBridgeServer webSocketServer;
    private volatile MinecraftServer server;
    private volatile boolean started;

    public void configure(int maxOutboundFrames) {
        outboundQueue.configure(maxOutboundFrames);
    }

    public void start(MinecraftServer server) {
        this.server = server;
        WebSocketBridgeServer startingServer = null;
        try {
            if (Config.bridgeServerEnabled) {
                startingServer = new WebSocketBridgeServer(
                        Config.bridgeServerHost,
                        Config.bridgeServerPort,
                        Config.bridgeServerPath,
                        Config.bridgeAccessToken,
                        Config.maxBridgeMessageBytes,
                        this::handleInboundFrame,
                        this::handleSessionClosed
                );
                startingServer.start();
                webSocketServer = startingServer;
            }
            started = true;
            syncAgentStateToClients();
        } catch (RuntimeException exception) {
            started = false;
            this.server = null;
            activeAgentTracker.clear();
            MaidExternalAgentDisplayState.clearActiveAgentId();
            if (startingServer != null) {
                startingServer.stop();
            }
            webSocketServer = null;
            throw exception;
        }
    }

    public void stop() {
        started = false;
        var activeAgent = activeAgentTracker.activeSession(webSocketServer);
        if (activeAgent != null) {
            releaseDisconnectedTurns(activeAgent.id(), "bridge_transport_stopped");
        }
        activeAgentTracker.clear();
        syncAgentStateToClients();
        server = null;
        if (webSocketServer != null) {
            webSocketServer.stop();
            webSocketServer = null;
        }
        for (var frame : outboundQueue.clearWithDropHooks()) {
            frame.onDropped();
        }
        inboundGatewayTracker.clear();
    }

    public void restart(MinecraftServer server) {
        stop();
        Config.refreshFromSpec();
        configure(Config.maxOutboundFrames);
        start(server);
    }

    public boolean hasServerContext() {
        return server != null;
    }

    public void restartCurrentServer() {
        var currentServer = server;
        if (currentServer != null) {
            try {
                restart(currentServer);
            } catch (RuntimeException exception) {
                server = currentServer;
                throw exception;
            }
        }
    }

    public void publish(AiChainEvent event) {
        if (!started) {
            releaseDroppedTurnPayload(event, "传输层未启动");
            return;
        }
        if (BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST.equals(event.type()) && !Config.bridgeServerEnabled) {
            releaseDroppedTurnPayload(event, "桥接服务未启用");
            return;
        }
        try {
            var frame = BridgeFrameBuilder.eventFrame(event);
            if (BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST.equals(event.type())) {
                sendAgentTurnFrame(frame, requestId(event.payload()));
            } else {
                sendFrame(frame);
            }
        } catch (RuntimeException exception) {
            releaseDroppedTurnPayload(event, "入队或构造失败");
            MaidBridge.LOGGER.warn("MaidBridge 事件入队失败 type={}", event.type(), exception);
        }
    }

    public void sweepExpiredTurns() {
        for (var completedTurn : MaidExternalTurnGuard.sweepExpiredTurns()) {
            maidTurnDeadlines.incrementAndGet();
            PendingMaidTurnReleaser.logReleased(completedTurn);
            dropExpiredQueuedTurnRequest(completedTurn.turn());
        }
    }

    public BridgeTransportSnapshot snapshot() {
        var currentServer = webSocketServer;
        var webSocketRunning = currentServer != null && currentServer.isStarted();
        var activeAgent = activeAgentTracker.activeSession(currentServer);
        var activeAgentSessionId = activeAgent == null ? "" : activeAgent.id();
        var clients = currentServer == null ? List.<BridgeTransportSnapshot.Client>of() : currentServer.clientSnapshots(activeAgentSessionId);
        return new BridgeTransportSnapshot(
                started,
                Config.bridgeServerEnabled,
                webSocketRunning,
                Config.bridgeServerHost,
                Config.bridgeServerPort,
                Config.bridgeServerPath,
                clients.size(),
                activeAgentSessionId,
                clients,
                outboundQueue.summarizeByType().stream()
                        .map(summary -> new BridgeTransportSnapshot.QueuedFrame(summary.type(), summary.count()))
                        .toList(),
                new BridgeTransportSnapshot.Counters(
                        outboundQueue.size(),
                        outboundQueue.droppedFrames(),
                        inboundGatewayMessageFrames.get(),
                        duplicateInboundGatewayFrames.get(),
                        malformedInboundFrames.get(),
                        outboundSendFailures.get(),
                        maidTurnDrops.get(),
                        maidTurnDisconnects.get(),
                        maidTurnDeadlines.get()
                )
        );
    }

    private void releaseDroppedTurnPayload(AiChainEvent event, String reason) {
        if (!BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST.equals(event.type())) {
            return;
        }
        var released = PendingMaidTurnReleaser.releaseDroppedIdentity(
                PendingMaidTurnReleaser.fromPayload(event.payload()),
                reason
        );
        if (released != null) {
            maidTurnDrops.incrementAndGet();
        }
    }

    private void offerFrame(String frame) {
        offerFrame(outboundFrame(frame));
    }

    private void offerFrame(OutboundFrame frame) {
        try {
            outboundQueue.offer(frame);
        } catch (RuntimeException exception) {
            MaidBridge.LOGGER.warn("MaidBridge 帧入队失败 maxBridgeMessageBytes={}", Config.maxBridgeMessageBytes, exception);
        }
    }

    private void offerPriorityFrame(OutboundFrame frame) {
        try {
            outboundQueue.offerPriority(frame);
        } catch (RuntimeException exception) {
            MaidBridge.LOGGER.warn("MaidBridge 优先帧入队失败 maxBridgeMessageBytes={}", Config.maxBridgeMessageBytes, exception);
        }
    }

    private void sendFrame(String frame) {
        var currentServer = webSocketServer;
        if (currentServer == null || !currentServer.isStarted() || currentServer.connectedClients() == 0) {
            offerFrame(frame);
            return;
        }
        var outboundFrame = outboundFrame(frame);
        var delivered = 0;
        for (var session : currentServer.sessions()) {
            if (BridgeRoutingRules.canReceive(session, outboundFrame)) {
                if (sendToSession(currentServer, session, outboundFrame, "广播")) {
                    delivered++;
                }
            }
        }
        if (delivered == 0) {
            offerFrame(outboundFrame);
        }
    }

    private void sendAgentTurnFrame(String frame, String requestId) {
        var currentServer = webSocketServer;
        var outboundFrame = outboundFrame(frame);
        var activeAgent = currentServer == null ? null : activeAgentTracker.activeSession(currentServer);
        if (!BridgeRoutingRules.canReceive(activeAgent, outboundFrame)) {
            /*
             * 外部 agent 初始化前可能已有待处理轮次。
             * 这里先入队，重连后仍由同一个 turn complete 收口。
             */
            offerPriorityFrame(outboundFrame);
            MaidBridge.LOGGER.debug("外部女仆轮次请求已入队，等待活动 agent 连接 requestId={}", requestId);
            return;
        }
        if (!sendToSession(currentServer, activeAgent, outboundFrame, "agent轮次")) {
            offerPriorityFrame(outboundFrame);
            MaidBridge.LOGGER.debug("外部女仆轮次请求发送失败后重新入队 requestId={}", requestId);
        }
    }

    private void handleInboundFrame(WebSocketBridgeServer.Session session, String rawFrame) {
        try {
            var type = BridgeInboundParser.peekType(rawFrame, Config.maxBridgeMessageBytes);
            if (BridgeProtocol.isSessionInitializeType(type)) {
                handleSessionInitialize(session, rawFrame);
                return;
            }
            if (session.sessionInitialize() == null) {
                BridgeFrameIdentity identity = BridgeInboundParser.parseFrameIdentity(rawFrame, Config.maxBridgeMessageBytes);
                sendBridgeError(session, identity.id(), identity.traceId(), "数据帧之前必须先完成 bridge.session.initialize");
                return;
            }
            switch (type) {
                case BridgeProtocol.TYPE_GATEWAY_MESSAGE -> {
                    if (rejectUnlessRole(session, rawFrame, type, List.of("message"))) {
                        return;
                    }
                    handleGatewayFrame(session, rawFrame);
                }
                case BridgeProtocol.TYPE_MAID_MESSAGE_IN -> {
                    if (rejectUnlessRole(session, rawFrame, type, List.of("message"))) {
                        return;
                    }
                    handleMaidMessageFrame(session, rawFrame);
                }
                case BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE -> {
                    if (rejectUnlessRole(session, rawFrame, type, List.of("agent")) || rejectUnlessActiveAgentSession(session, rawFrame)) {
                        return;
                    }
                    handleMaidTurnCompleteFrame(session, rawFrame);
                }
                default -> {
                    if (BridgeProtocol.isSupportedMaidApiType(type)) {
                        if (rejectUnlessRole(session, rawFrame, type, BridgeRoutingRules.maidApiRoles(type))) {
                            return;
                        }
                        handleMaidApiFrame(session, rawFrame);
                        return;
                    }
                    BridgeFrameIdentity identity = BridgeInboundParser.parseFrameIdentity(rawFrame, Config.maxBridgeMessageBytes);
                    sendBridgeError(session, identity.id(), identity.traceId(), "不支持的 MaidBridge 入站帧类型：" + type);
                }
            }
        } catch (RuntimeException exception) {
            malformedInboundFrames.incrementAndGet();
            MaidBridge.LOGGER.warn("已忽略格式错误的 MaidBridge 入站帧", exception);
        }
    }

    private void handleSessionInitialize(WebSocketBridgeServer.Session session, String rawFrame) {
        BridgeFrameIdentity identity = BridgeInboundParser.parseFrameIdentity(rawFrame, Config.maxBridgeMessageBytes);
        try {
            var sessionInitialize = BridgeInboundParser.parseSessionInitialize(rawFrame, Config.maxBridgeMessageBytes);
            if (BridgeRoutingRules.canReceiveAgentTurnRequests(sessionInitialize) && !activeAgentTracker.claim(webSocketServer, session)) {
                sendDomainFailure(session, BridgeProtocol.TYPE_SESSION_READY, sessionInitialize.id(), sessionInitialize.traceId(), "已有另一个活动 agent 客户端连接");
                return;
            }
            session.setSessionInitialize(sessionInitialize);
            if (BridgeRoutingRules.canReceiveAgentTurnRequests(sessionInitialize)) {
                syncAgentStateToClients();
            }
            var serverName = server == null ? "Minecraft 服务器" : server.getServerModName();
            sendDirect(session, BridgeFrameBuilder.sessionReadyFrame(serverName, sessionInitialize.id(), sessionInitialize.traceId()));
            flushQueuedToSession(session);
        } catch (RuntimeException exception) {
            sendDomainFailure(session, BridgeProtocol.TYPE_SESSION_READY, identity.id(), identity.traceId(), exception.getMessage());
        }
    }

    private void handleGatewayFrame(WebSocketBridgeServer.Session session, String rawFrame) {
        BridgeFrameIdentity identity = BridgeInboundParser.parseFrameIdentity(rawFrame, Config.maxBridgeMessageBytes);
        try {
            var message = BridgeInboundParser.parseGatewayMessage(
                    rawFrame,
                    Config.maxBridgeMessageBytes,
                    Config.maxInboundGatewayTextCharacters
            );
            routeGatewayMessage(session, message);
        } catch (RuntimeException exception) {
            sendDomainFailure(session, BridgeProtocol.TYPE_GATEWAY_RESPONSE, identity.id(), identity.traceId(), exception.getMessage());
        }
    }

    private void handleMaidApiFrame(WebSocketBridgeServer.Session session, String rawFrame) {
        BridgeFrameIdentity identity = BridgeInboundParser.parseFrameIdentity(rawFrame, Config.maxBridgeMessageBytes);
        try {
            var request = BridgeInboundParser.parseMaidApiRequest(rawFrame, Config.maxBridgeMessageBytes);
            MaidApiRequestDispatcher.schedule(
                    server,
                    request,
                    (replyTo, traceId, payload) -> sendDomainSuccess(session, BridgeProtocol.TYPE_MAID_API_RESPONSE, replyTo, traceId, payload),
                    (replyTo, traceId, error) -> sendDomainFailure(session, BridgeProtocol.TYPE_MAID_API_RESPONSE, replyTo, traceId, error)
            );
        } catch (RuntimeException exception) {
            sendDomainFailure(session, BridgeProtocol.TYPE_MAID_API_RESPONSE, identity.id(), identity.traceId(), exception.getMessage());
        }
    }

    private void handleMaidMessageFrame(WebSocketBridgeServer.Session session, String rawFrame) {
        BridgeFrameIdentity identity = BridgeInboundParser.parseFrameIdentity(rawFrame, Config.maxBridgeMessageBytes);
        try {
            var message = BridgeInboundParser.parseMaidMessageIn(
                    rawFrame,
                    Config.maxBridgeMessageBytes,
                    Config.maxInboundGatewayTextCharacters
            );
            MaidMessageDispatcher.schedule(
                    server,
                    message,
                    (replyTo, traceId, payload) -> sendDomainSuccess(session, BridgeProtocol.TYPE_MAID_MESSAGE_RESPONSE, replyTo, traceId, payload),
                    (replyTo, traceId, error) -> sendDomainFailure(session, BridgeProtocol.TYPE_MAID_MESSAGE_RESPONSE, replyTo, traceId, error)
            );
        } catch (RuntimeException exception) {
            sendDomainFailure(session, BridgeProtocol.TYPE_MAID_MESSAGE_RESPONSE, identity.id(), identity.traceId(), exception.getMessage());
        }
    }

    private void handleMaidTurnCompleteFrame(WebSocketBridgeServer.Session session, String rawFrame) {
        BridgeFrameIdentity identity = BridgeInboundParser.parseFrameIdentity(rawFrame, Config.maxBridgeMessageBytes);
        try {
            var complete = BridgeInboundParser.parseMaidAgentTurnComplete(
                    rawFrame,
                    Config.maxBridgeMessageBytes,
                    Config.maxInboundGatewayTextCharacters
            );
            MaidBridge.LOGGER.info(
                    "收到 maid.agent.turn.complete 帧 sessionId={} type={} maidUuid={} turnId={} outcome={}",
                    session.id(),
                    BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE,
                    complete.maidUuid(),
                    complete.turnId(),
                    complete.outcome()
            );
            MaidAgentTurnCompleteDispatcher.schedule(
                    server,
                    complete,
                    (replyTo, traceId, payload) -> MaidBridge.LOGGER.debug(
                            "maid.agent.turn.complete 处理成功 replyTo={} traceId={} payload={}",
                            replyTo,
                            traceId,
                            summarizePayload(payload)
                    ),
                    (replyTo, traceId, error) -> sendBridgeError(session, replyTo, traceId, error)
            );
        } catch (RuntimeException exception) {
            var turnIdentity = BridgeInboundParser.parseMaidTurnIdentityLenient(rawFrame, Config.maxBridgeMessageBytes);
            MaidBridge.LOGGER.warn(
                    "解析 maid.agent.turn.complete 失败 sessionId={} type={} maidUuid={} turnId={}",
                    session.id(),
                    BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE,
                    turnIdentity.maidUuid(),
                    turnIdentity.turnId(),
                    exception
            );
            // 格式错误只返回诊断，不释放轮次；否则一次坏帧就会吞掉玩家本轮消息。
            sendBridgeError(session, identity.id(), identity.traceId(), exception.getMessage());
        }
    }

    private void routeGatewayMessage(WebSocketBridgeServer.Session session, BridgeGatewayMessage message) {
        if (!Config.enableInboundGatewayMessages) {
            sendDomainFailure(session, BridgeProtocol.TYPE_GATEWAY_RESPONSE, message.id(), message.traceId(), "入站网关消息未启用");
            return;
        }

        var currentServer = server;
        if (currentServer == null) {
            sendDomainFailure(session, BridgeProtocol.TYPE_GATEWAY_RESPONSE, message.id(), message.traceId(), "Minecraft 服务器不可用");
            return;
        }

        var reservation = inboundGatewayTracker.reserve(message.id(), Config.maxPendingInboundGatewayMessages);
        switch (reservation.status()) {
            case QUEUE_FULL -> {
                sendDomainFailure(session, BridgeProtocol.TYPE_GATEWAY_RESPONSE, message.id(), message.traceId(), "入站网关消息队列已满");
                return;
            }
            case DUPLICATE_PENDING -> {
                sendDomainFailure(session, BridgeProtocol.TYPE_GATEWAY_RESPONSE, message.id(), message.traceId(), "入站网关消息已在处理中");
                return;
            }
            case DUPLICATE_SUCCEEDED -> {
                duplicateInboundGatewayFrames.incrementAndGet();
                sendDomainSuccess(session, BridgeProtocol.TYPE_GATEWAY_RESPONSE, message.id(), message.traceId(), Map.of(
                        "routed", "gateway_message",
                        "duplicate", true
                ));
                return;
            }
            case ACCEPTED -> {
            }
        }

        try {
            currentServer.execute(() -> {
                try {
                    var component = Component.literal(Config.inboundGatewayMessagePrefix + message.plainText());
                    currentServer.getPlayerList().broadcastSystemMessage(component, false);
                    inboundGatewayTracker.markSucceeded(message.id());
                    inboundGatewayMessageFrames.incrementAndGet();
                    sendDomainSuccess(session, BridgeProtocol.TYPE_GATEWAY_RESPONSE, message.id(), message.traceId(), Map.of(
                            "routed", "gateway_message",
                            "route_scope", message.routeScope(),
                            "route", message.route(),
                            "target", message.target(),
                            "metadata", message.metadata(),
                            "endpoint_id", message.endpointId(),
                            "source_endpoint", message.sourceEndpoint(),
                            "target_endpoint", message.targetEndpoint(),
                            "delivered_players", currentServer.getPlayerList().getPlayerCount()
                    ));
                } catch (RuntimeException exception) {
                    inboundGatewayTracker.markFailed(message.id());
                    sendDomainFailure(session, BridgeProtocol.TYPE_GATEWAY_RESPONSE, message.id(), message.traceId(), exception.getMessage());
                }
            });
        } catch (RuntimeException exception) {
            inboundGatewayTracker.markFailed(message.id());
            sendDomainFailure(session, BridgeProtocol.TYPE_GATEWAY_RESPONSE, message.id(), message.traceId(), exception.getMessage());
        }
    }

    private void sendDomainSuccess(WebSocketBridgeServer.Session session, String type, String replyTo, String traceId, Map<String, Object> payload) {
        if (!sendDirect(session, BridgeFrameBuilder.domainResponseFrame(type, replyTo, traceId, payload))) {
            MaidBridge.LOGGER.debug("MaidBridge 领域响应未送达 type={} replyTo={}", type, replyTo);
        }
    }

    private void sendDomainFailure(WebSocketBridgeServer.Session session, String type, String replyTo, String traceId, String error) {
        if (!sendDirect(session, BridgeFrameBuilder.domainErrorFrame(type, replyTo, traceId, error))) {
            MaidBridge.LOGGER.debug("MaidBridge 领域错误响应未送达 type={} replyTo={} error={}", type, replyTo, error);
        }
    }

    private void sendBridgeError(WebSocketBridgeServer.Session session, String replyTo, String traceId, String error) {
        if (!sendDirect(session, BridgeFrameBuilder.bridgeErrorFrame(replyTo, traceId, error))) {
            MaidBridge.LOGGER.debug("MaidBridge bridge.error 未送达 replyTo={} error={}", replyTo, error);
        }
    }

    private boolean sendDirect(WebSocketBridgeServer.Session session, String frame) {
        var outboundFrame = outboundFrame(frame);
        var currentServer = webSocketServer;
        if (currentServer == null || !currentServer.isStarted()) {
            return false;
        }
        return sendToSession(currentServer, session, outboundFrame, "直接发送");
    }

    private void flushQueuedToSession(WebSocketBridgeServer.Session session) {
        while (true) {
            var frames = outboundQueue.drainMatching(frame -> BridgeRoutingRules.canReceive(session, frame));
            if (frames.isEmpty()) {
                return;
            }
            for (var frame : frames) {
                if (!sendDirect(session, frame.rawFrame())) {
                    offerPriorityFrame(frame);
                    return;
                }
            }
        }
    }

    private boolean sendToSession(
            WebSocketBridgeServer currentServer,
            WebSocketBridgeServer.Session session,
            OutboundFrame frame,
            String operation
    ) {
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            currentServer.send(session, frame.rawFrame());
            markTurnDelivered(session, frame);
            return true;
        } catch (RuntimeException exception) {
            recordOutboundSendFailure(operation, session, frame, exception);
            return false;
        }
    }

    private void markTurnDelivered(WebSocketBridgeServer.Session session, OutboundFrame frame) {
        if (!BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST.equals(frame.type())) {
            return;
        }
        MaidExternalTurnGuard.markDelivered(
                new MaidTurnIdentity(frame.maidUuid(), frame.turnId(), frame.requestId()),
                session.id(),
                agentDisplayName(session)
        );
    }

    private void recordOutboundSendFailure(
            String operation,
            WebSocketBridgeServer.Session session,
            OutboundFrame frame,
            RuntimeException exception
    ) {
        outboundSendFailures.incrementAndGet();
        MaidBridge.LOGGER.warn(
                "发送 MaidBridge 帧失败 operation={} session={} type={} requestId={}",
                operation,
                session == null ? "-" : session.id(),
                frame.type(),
                frame.requestId(),
                exception
        );
    }

    private OutboundFrame outboundFrame(String frame) {
        return BridgeOutboundFrames.fromJson(frame, (maidUuid, turnId, requestId) -> {
            var released = PendingMaidTurnReleaser.releaseDroppedIdentity(
                    new MaidTurnIdentity(maidUuid, turnId, requestId),
                    "丢弃"
            );
            if (released != null) {
                maidTurnDrops.incrementAndGet();
            }
        });
    }

    private boolean rejectUnlessRole(WebSocketBridgeServer.Session session, String rawFrame, String type, List<String> roles) {
        if (roles.stream().anyMatch(session::hasRole)) {
            return false;
        }
        BridgeFrameIdentity identity = BridgeInboundParser.parseFrameIdentity(rawFrame, Config.maxBridgeMessageBytes);
        if (BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE.equals(type)) {
            var turnIdentity = BridgeInboundParser.parseMaidTurnIdentityLenient(rawFrame, Config.maxBridgeMessageBytes);
            var sessionInitialize = session.sessionInitialize();
            MaidBridge.LOGGER.warn(
                    "拒绝 maid.agent.turn.complete：client 角色不允许 sessionId={} type={} maidUuid={} turnId={} roles={}",
                    session.id(),
                    type,
                    turnIdentity.maidUuid(),
                    turnIdentity.turnId(),
                    sessionInitialize == null ? List.of() : sessionInitialize.roles()
            );
        }
        sendRoleError(session, type, identity.id(), identity.traceId());
        return true;
    }

    private boolean rejectUnlessActiveAgentSession(WebSocketBridgeServer.Session session, String rawFrame) {
        if (activeAgentTracker.owns(session)) {
            return false;
        }
        BridgeFrameIdentity identity = BridgeInboundParser.parseFrameIdentity(rawFrame, Config.maxBridgeMessageBytes);
        var turnIdentity = BridgeInboundParser.parseMaidTurnIdentityLenient(rawFrame, Config.maxBridgeMessageBytes);
        var activeAgent = activeAgentTracker.activeSession(webSocketServer);
        MaidBridge.LOGGER.warn(
                "拒绝 maid.agent.turn.complete：不是当前活动 agent 会话 sessionId={} activeSessionId={} type={} maidUuid={} turnId={}",
                session.id(),
                activeAgent == null ? "" : activeAgent.id(),
                BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE,
                turnIdentity.maidUuid(),
                turnIdentity.turnId()
        );
        sendBridgeError(session, identity.id(), identity.traceId(), BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE + " 需要当前活动 agent 会话");
        return true;
    }

    private void sendRoleError(WebSocketBridgeServer.Session session, String inboundType, String replyTo, String traceId) {
        var error = "client 角色不允许发送此 MaidBridge 帧";
        var responseType = responseTypeForInbound(inboundType);
        if (BridgeProtocol.TYPE_BRIDGE_ERROR.equals(responseType)) {
            sendBridgeError(session, replyTo, traceId, error);
        } else {
            sendDomainFailure(session, responseType, replyTo, traceId, error);
        }
    }

    private String responseTypeForInbound(String inboundType) {
        if (BridgeProtocol.TYPE_GATEWAY_MESSAGE.equals(inboundType)) {
            return BridgeProtocol.TYPE_GATEWAY_RESPONSE;
        }
        if (BridgeProtocol.TYPE_MAID_MESSAGE_IN.equals(inboundType)) {
            return BridgeProtocol.TYPE_MAID_MESSAGE_RESPONSE;
        }
        if (BridgeProtocol.isSupportedMaidApiType(inboundType)) {
            return BridgeProtocol.TYPE_MAID_API_RESPONSE;
        }
        if (BridgeProtocol.isSessionInitializeType(inboundType)) {
            return BridgeProtocol.TYPE_SESSION_READY;
        }
        return BridgeProtocol.TYPE_BRIDGE_ERROR;
    }

    private static String summarizePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        var summary = String.valueOf(payload);
        return summary.length() <= 512 ? summary : summary.substring(0, 512) + "...";
    }

    private void handleSessionClosed(WebSocketBridgeServer.Session session) {
        if (!activeAgentTracker.releaseIfOwned(session)) {
            return;
        }
        syncAgentStateToClients();
        releaseDisconnectedTurns(session.id(), "active_agent_disconnected");
    }

    private void releaseDisconnectedTurns(String sessionId, String reason) {
        var releasedTurns = MaidExternalTurnGuard.releaseDeliveredToSession(
                sessionId,
                PendingMaidTurnReleaser.OUTCOME_DISCONNECT,
                reason
        );
        maidTurnDisconnects.addAndGet(releasedTurns.size());
        for (var releasedTurn : releasedTurns) {
            PendingMaidTurnReleaser.logReleased(releasedTurn);
        }
    }

    private void dropExpiredQueuedTurnRequest(MaidExternalTurnGuard.ActiveTurn turn) {
        var dropped = outboundQueue.dropMatching(frame -> BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST.equals(frame.type())
                && frame.maidUuid().equals(turn.maidUuid())
                && frame.turnId().equals(turn.turnId())
                && frame.requestId().equals(turn.requestId()));
        if (!dropped.isEmpty()) {
            MaidBridge.LOGGER.debug(
                    "已移除过期轮次的排队女仆请求 requestId={} turnId={} count={}",
                    turn.requestId(),
                    turn.turnId(),
                    dropped.size()
            );
        }
    }

    private static String requestId(Map<String, Object> payload) {
        var direct = payload.get("request_id");
        if (direct != null && !String.valueOf(direct).isBlank()) {
            return String.valueOf(direct);
        }
        var turnId = payload.get("turn_id");
        if (turnId != null && !String.valueOf(turnId).isBlank()) {
            return String.valueOf(turnId);
        }
        return "";
    }

    private void syncAgentStateToClients() {
        var activeAgent = activeAgentTracker.activeSession(webSocketServer);
        var activeAgentId = agentDisplayName(activeAgent);
        var agentIds = activeAgentId.isBlank() ? List.<String>of() : List.of(activeAgentId);
        MaidExternalAgentDisplayState.replaceAgents(agentIds, activeAgentId);
        var packet = SyncMaidBridgeAgentStatePacket.current();
        var currentServer = server;
        if (currentServer != null) {
            currentServer.execute(() -> MaidBridgeNetwork.sendToAllPlayers(packet));
        }
    }

    private static String agentDisplayName(WebSocketBridgeServer.Session session) {
        if (session == null || session.sessionInitialize() == null) {
            return "";
        }
        var sessionInitialize = session.sessionInitialize();
        return firstNonBlank(sessionInitialize.agentId(), sessionInitialize.clientName(), session.id());
    }

    private static String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
