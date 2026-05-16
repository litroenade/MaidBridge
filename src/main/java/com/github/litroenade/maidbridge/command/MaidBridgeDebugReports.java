package com.github.litroenade.maidbridge.command;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.maid.turn.MaidExternalTurnGuard;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.github.litroenade.maidbridge.trace.AiChainEvent;
import com.github.litroenade.maidbridge.trace.AiChainEventSink;
import com.github.litroenade.maidbridge.transport.BridgeTransport;
import com.github.litroenade.maidbridge.transport.BridgeTransportSnapshot;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// Minecraft 的 Level 由服务端生命周期管理，调试报表只读取状态，不能在这里关闭。
public final class MaidBridgeDebugReports {
    private static final int MAX_EVENT_LIMIT = 50;
    private static final int TEXT_LIMIT = 96;

    private MaidBridgeDebugReports() {
    }

    public static List<String> summaryLines(BridgeTransport transport) {
        var snapshot = transport.snapshot();
        var counters = snapshot.counters();
        var events = AiChainEventSink.snapshot();
        var lines = new ArrayList<String>();
        lines.add("MaidBridge 调试摘要");
        lines.add("WebSocket 启用=%s 运行=%s 端点=ws://%s:%d%s 客户端=%d 活动agent=%s".formatted(
                snapshot.webSocketEnabled(),
                snapshot.webSocketRunning(),
                snapshot.host(),
                snapshot.port(),
                snapshot.path(),
                snapshot.connectedClients(),
                activeAgentDisplay(snapshot)
        ));
        lines.add("模式=%s 消息桥接=%s 外部agent=%s API查询=%s API写入=%s pendingTTL毫秒=%d".formatted(
                maidBridgeMode(),
                Config.enableMaidMessageBridge,
                Config.isExternalMaidAgentMode(),
                Config.enableMaidApiExposure,
                Config.enableMaidApiActions,
                Config.maidExternalTurnTtlMs
        ));
        lines.add("传输 队列=%d 丢弃=%d 格式错误=%d 发送失败=%d drop=%d disconnect=%d deadline=%d".formatted(
                counters.queuedFrames(),
                counters.droppedFrames(),
                counters.malformedInboundFrames(),
                counters.outboundSendFailures(),
                counters.maidTurnDrops(),
                counters.maidTurnDisconnects(),
                counters.maidTurnDeadlines()
        ));
        lines.add("事件 缓冲=%d 最近调试=%s 最近轮次=%s 最近错误=%s".formatted(
                events.size(),
                eventLabel(lastEvent(events, MaidBridgeDebugReports::isDebugEvent)),
                eventLabel(lastEvent(events, MaidBridgeDebugReports::isTurnEvent)),
                eventLabel(lastEvent(events, MaidBridgeDebugReports::isErrorEvent))
        ));
        lines.add("配置=%s".formatted(commonConfigPath()));
        return List.copyOf(lines);
    }

    public static List<String> webSocketLines(BridgeTransport transport) {
        var snapshot = transport.snapshot();
        var counters = snapshot.counters();
        var lines = new ArrayList<String>();
        lines.add("MaidBridge WebSocket 调试");
        lines.add("服务端 启用=%s 运行=%s 端点=ws://%s:%d%s 活动agent=%s".formatted(
                snapshot.webSocketEnabled(),
                snapshot.webSocketRunning(),
                snapshot.host(),
                snapshot.port(),
                snapshot.path(),
                activeAgentDisplay(snapshot)
        ));
        lines.add("计数 队列=%d 丢弃=%d 网关=%d 重复网关=%d 格式错误=%d 发送失败=%d drop=%d disconnect=%d deadline=%d".formatted(
                counters.queuedFrames(),
                counters.droppedFrames(),
                counters.inboundGatewayMessageFrames(),
                counters.duplicateInboundGatewayFrames(),
                counters.malformedInboundFrames(),
                counters.outboundSendFailures(),
                counters.maidTurnDrops(),
                counters.maidTurnDisconnects(),
                counters.maidTurnDeadlines()
        ));
        lines.add(queuedFrameSummaryLine(snapshot.queuedFrames()));
        lines.addAll(clientLines(snapshot.clients()));
        return List.copyOf(lines);
    }

    private static String maidBridgeMode() {
        if (Config.isExternalMaidAgentMode()) {
            return "external_agent";
        }
        if (Config.enableMaidMessageBridge) {
            return "message_bridge";
        }
        return "off";
    }

    public static List<String> restartWebSocketLines(BridgeTransport transport) {
        var snapshot = transport.snapshot();
        var lines = new ArrayList<String>();
        lines.add("MaidBridge WebSocket 已重启");
        lines.add("服务端 启用=%s 运行=%s 端点=ws://%s:%d%s 客户端=%d 活动agent=%s".formatted(
                snapshot.webSocketEnabled(),
                snapshot.webSocketRunning(),
                snapshot.host(),
                snapshot.port(),
                snapshot.path(),
                snapshot.connectedClients(),
                activeAgentDisplay(snapshot)
        ));
        lines.add(queuedFrameSummaryLine(snapshot.queuedFrames()));
        return List.copyOf(lines);
    }

    public static List<String> chatLines(int limit) {
        var recent = recentEvents(limit, MaidBridgeDebugReports::isChatPathEvent);
        var lines = new ArrayList<String>();
        lines.add("MaidBridge 聊天链路 最近=%d".formatted(recent.size()));
        for (AiChainEvent event : recent) {
            lines.add("- " + summarizeEvent(event));
        }
        return List.copyOf(lines);
    }

    public static List<String> turnsLines(int limit) {
        var pendingTurns = MaidExternalTurnGuard.snapshotExternalTurns();
        var recent = recentEvents(limit, MaidBridgeDebugReports::isTurnEvent);
        var lines = new ArrayList<String>();
        lines.add("MaidBridge 待处理轮次 数量=%d".formatted(pendingTurns.size()));
        for (MaidExternalTurnGuard.ActiveTurnSnapshot pendingTurn : pendingTurns) {
            lines.add("- 轮次=%s 请求ID=%s 女仆UUID=%s TTL毫秒=%d 已投递会话=%s 文本=%s".formatted(
                    display(pendingTurn.turnId()),
                    display(pendingTurn.requestId()),
                    display(pendingTurn.maidUuid()),
                    pendingTurn.remainingMs(),
                    display(pendingTurn.deliveredSessionId()),
                    displayValue(pendingTurn.userMessage())
            ));
        }
        lines.add("MaidBridge 轮次事件 数量=%d".formatted(recent.size()));
        for (AiChainEvent event : recent) {
            lines.add("- " + summarizeEvent(event));
        }
        return List.copyOf(lines);
    }

    private static String queuedFrameSummaryLine(List<BridgeTransportSnapshot.QueuedFrame> queuedFrames) {
        if (queuedFrames.isEmpty()) {
            return "排队帧=无";
        }
        var summary = queuedFrames.stream()
                .map(frame -> "%s:%d".formatted(display(frame.type()), frame.count()))
                .collect(Collectors.joining(", "));
        return "排队帧 " + summary;
    }

    private static List<String> clientLines(List<BridgeTransportSnapshot.Client> clients) {
        if (clients.isEmpty()) {
            return List.of("客户端 0");
        }
        var lines = new ArrayList<String>();
        lines.add("客户端 %d".formatted(clients.size()));
        for (BridgeTransportSnapshot.Client client : clients) {
            lines.add("- 会话=%s 名称=%s agent=%s 女仆=%s 角色=%s 订阅=%s 活动agent=%s 打开=%s".formatted(
                    client.sessionId(),
                    display(client.clientName()),
                    display(client.agentId()),
                    display(client.maidUuid()),
                    join(client.roles()),
                    join(client.subscriptions()),
                    client.activeAgent(),
                    client.open()
            ));
        }
        return List.copyOf(lines);
    }

    private static List<AiChainEvent> recentEvents(int limit, Predicate<AiChainEvent> predicate) {
        var events = AiChainEventSink.snapshot();
        var recent = new ArrayList<AiChainEvent>();
        for (int index = events.size() - 1; index >= 0 && recent.size() < Math.min(limit, MAX_EVENT_LIMIT); index--) {
            var event = events.get(index);
            if (predicate.test(event)) {
                recent.add(event);
            }
        }
        Collections.reverse(recent);
        return recent;
    }

    private static AiChainEvent lastEvent(List<AiChainEvent> events, Predicate<AiChainEvent> predicate) {
        for (int index = events.size() - 1; index >= 0; index--) {
            var event = events.get(index);
            if (predicate.test(event)) {
                return event;
            }
        }
        return null;
    }

    private static String summarizeEvent(AiChainEvent event) {
        var payload = event.payload();
        return "%s 类型=%s 轮次=%s 女仆=%s 文本=%s 错误=%s".formatted(
                Instant.ofEpochMilli(event.timestampMs()),
                event.type(),
                displayValue(firstDeep(payload, "turn_id", "request_id")),
                maidDisplay(payload),
                eventText(payload),
                displayValue(firstDeep(payload, "error"))
        );
    }

    private static String eventLabel(AiChainEvent event) {
        if (event == null) {
            return "-";
        }
        return event.type() + "@" + Instant.ofEpochMilli(event.timestampMs());
    }

    private static boolean isDebugEvent(AiChainEvent event) {
        String type = event.type();
        return type.startsWith("maid.")
                || type.startsWith("bridge.")
                || type.startsWith(BridgeProtocol.PREFIX_MAIDBRIDGE_SERVER);
    }

    private static boolean isTurnEvent(AiChainEvent event) {
        String type = event.type();
        return BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST.equals(type)
                || BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE.equals(type)
                || BridgeProtocol.TYPE_MAID_MESSAGE_IN.equals(type)
                || BridgeProtocol.TYPE_MAID_MESSAGE_OUT.equals(type)
                || "maid.ai.output.failure".equals(type);
    }

    private static boolean isChatPathEvent(AiChainEvent event) {
        String type = event.type();
        return isUserChatEvent(event)
                || "maid.ai.prompt.built".equals(type)
                || "maid.ai.llm.client.selected".equals(type)
                || "maid.ai.llm.request".equals(type)
                || isLlmCallbackEvent(event)
                || BridgeProtocol.TYPE_MAID_MESSAGE_OUT.equals(type)
                || BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST.equals(type)
                || BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE.equals(type);
    }

    private static boolean isUserChatEvent(AiChainEvent event) {
        String type = event.type();
        return "maid.ai.request.received".equals(type)
                || BridgeProtocol.TYPE_MAID_MESSAGE_IN.equals(type)
                || BridgeProtocol.TYPE_GATEWAY_MESSAGE.equals(type);
    }

    private static boolean isLlmCallbackEvent(AiChainEvent event) {
        String type = event.type();
        return "maid.ai.output.final".equals(type)
                || "maid.ai.output.failure".equals(type)
                || "maid.ai.tool_calls.proposed".equals(type)
                || "maid.ai.tool_call.decoded".equals(type)
                || "maid.ai.tool_result.added".equals(type);
    }

    private static boolean isErrorEvent(AiChainEvent event) {
        Object error = firstDeep(event.payload(), "error");
        return event.type().contains("failure") || !String.valueOf(error).isBlank();
    }

    private static Path commonConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve(MaidBridge.MODID + "-common.toml").toAbsolutePath().normalize();
    }

    private static String maidDisplay(Map<String, Object> payload) {
        var maid = objectMap(payload.get("maid"));
        if (maid.isEmpty()) {
            return "-";
        }
        return firstNonBlank(display(maid.get("name")), display(maid.get("uuid")));
    }

    private static String eventText(Map<String, Object> payload) {
        Object text = firstDeep(payload, "chat_text", "text", "message", "processed_plain_text");
        return displayValue(text);
    }

    private static Object firstDeep(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object direct = payload.get(key);
            if (direct != null && !String.valueOf(direct).isBlank()) {
                return direct;
            }
            for (Object value : payload.values()) {
                if (value instanceof Map<?, ?> map && map.containsKey(key)) {
                    Object nested = map.get(key);
                    if (nested != null && !String.valueOf(nested).isBlank()) {
                        return nested;
                    }
                }
            }
        }
        return "";
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        var map = new java.util.LinkedHashMap<String, Object>();
        source.forEach((key, item) -> map.put(String.valueOf(key), item));
        return Map.copyOf(map);
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? "-" : String.join(",", values);
    }

    private static String display(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return "-";
        }
        return String.valueOf(value);
    }

    private static String activeAgentDisplay(BridgeTransportSnapshot snapshot) {
        for (BridgeTransportSnapshot.Client client : snapshot.clients()) {
            if (client.activeAgent()) {
                return firstNonBlank(client.agentId(), client.clientName(), client.sessionId());
            }
        }
        return display(snapshot.activeAgentSessionId());
    }

    private static String displayValue(Object value) {
        var text = display(value);
        return text.length() <= TEXT_LIMIT ? text : text.substring(0, TEXT_LIMIT) + "...";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"-".equals(value)) {
                return value;
            }
        }
        return "-";
    }
}
