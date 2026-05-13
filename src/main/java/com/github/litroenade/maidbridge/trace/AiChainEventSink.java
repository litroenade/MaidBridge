package com.github.litroenade.maidbridge.trace;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 缓存女仆 AI 链路事件，并把事件推给当前桥接层。
 * <p>配置关闭某个桥接面时，这里会按事件前缀直接丢弃对应事件。
 */
public final class AiChainEventSink {
    private static final int DEFAULT_MAX_EVENTS = 512;
    private static final Object LOCK = new Object();
    private static final ArrayDeque<AiChainEvent> EVENTS = new ArrayDeque<>();
    private static int maxEvents = DEFAULT_MAX_EVENTS;
    private static Consumer<AiChainEvent> eventConsumer;

    private AiChainEventSink() {
    }

    public static void configure(int maxBufferedEvents) {
        synchronized (LOCK) {
            maxEvents = Math.max(1, maxBufferedEvents);
            trimToLimit();
        }
    }

    public static void setConsumer(Consumer<AiChainEvent> consumer) {
        synchronized (LOCK) {
            eventConsumer = consumer;
        }
    }

    public static void emitLifecycle(String type, String serverName) {
        emit(type, Map.of("server_name", safeString(serverName)));
    }

    public static void emit(String type, Map<String, ?> payload) {
        if (isDisabledByConfig(type)) {
            return;
        }
        /*
         * maid.ai.* 是诊断事件，只用于观察女仆本体原生链路。
         * 外部 agent 轮次会重新生成一份清洗后的状态和能力数据。
         */
        var event = new AiChainEvent(type, System.currentTimeMillis(), sanitize(payload));
        Consumer<AiChainEvent> consumer;
        synchronized (LOCK) {
            EVENTS.addLast(event);
            trimToLimit();
            consumer = eventConsumer;
        }
        if (Config.logCapturedEvents) {
            MaidBridge.LOGGER.debug("捕获 MaidBridge 事件 type={} payload={}", event.type(), event.payload());
        }
        if (consumer != null) {
            try {
                consumer.accept(event);
            } catch (RuntimeException exception) {
                MaidBridge.LOGGER.warn("MaidBridge 事件消费者处理失败 type={}", event.type(), exception);
            }
        }
    }

    public static List<AiChainEvent> snapshot() {
        synchronized (LOCK) {
            return List.copyOf(new ArrayList<>(EVENTS));
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            EVENTS.clear();
        }
    }

    private static boolean isDisabledByConfig(String type) {
        if (type.startsWith(BridgeProtocol.PREFIX_MAID_AI)) {
            return !Config.enableAiChainCapture;
        }
        if (type.startsWith(BridgeProtocol.PREFIX_MAID_MESSAGE)) {
            return !Config.enableMaidMessageBridge;
        }
        if (type.startsWith(BridgeProtocol.PREFIX_MAID_AGENT)) {
            return !Config.isExternalMaidAgentMode();
        }
        if (type.startsWith(BridgeProtocol.PREFIX_MAID_API_REGISTRY)) {
            return !Config.enableMaidApiExposure;
        }
        if (type.startsWith(BridgeProtocol.PREFIX_BRIDGE_GATEWAY)) {
            return !Config.enableGatewayChatCapture;
        }
        return false;
    }

    private static Map<String, Object> sanitize(Map<?, ?> payload) {
        var sanitized = new LinkedHashMap<String, Object>();
        payload.forEach((key, value) -> sanitized.put(safeString(key), sanitizeValue(value)));
        return Collections.unmodifiableMap(sanitized);
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return sanitize(map);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(AiChainEventSink::sanitizeValue).toList();
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value;
        }
        return safeString(value);
    }

    private static String safeString(Object value) {
        if (value == null) {
            return "";
        }
        var text = String.valueOf(value);
        return text.length() <= 32768 ? text : text.substring(0, 32768);
    }

    private static void trimToLimit() {
        while (EVENTS.size() > maxEvents) {
            EVENTS.removeFirst();
        }
    }
}
