package com.github.litroenade.maidbridge.protocol;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.trace.AiChainEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.litroenade.maidbridge.protocol.BridgeJson.firstNonBlank;

/**
 * 构造发往外部客户端的 MaidBridge 帧。
 * <p>协议层只负责稳定外壳；女仆、玩家和行动语义统一放在 payload 里。</p>
 */
public final class BridgeFrameBuilder {
    public static final List<String> SUPPORTED_EXTERNAL_AGENT_EMOJI_FORMATS = List.of("png", "gif");

    private BridgeFrameBuilder() {
    }

    public static String sessionReadyFrame(String serverName, String replyTo, String traceId) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("server_name", safeString(serverName));
        payload.put("mod_id", MaidBridge.MODID);
        payload.put("roles", List.of("agent", "message", "maid_api_query", "maid_api_call", "debug"));
        payload.put("subscriptions", List.of(
                BridgeProtocol.PREFIX_MAID_MESSAGE + "*",
                BridgeProtocol.PREFIX_MAID_AGENT + "*",
                BridgeProtocol.PREFIX_MAID_API + "*",
                BridgeProtocol.PREFIX_MAID_AI + "*",
                BridgeProtocol.PREFIX_MAIDBRIDGE_SERVER + "*"
        ));
        payload.put("ai_chain_capture", Config.enableAiChainCapture);
        payload.put("gateway_chat_capture", Config.enableGatewayChatCapture);
        payload.put("maid_message_bridge", Config.enableMaidMessageBridge);
        payload.put("maid_api_exposure", Config.enableMaidApiExposure);
        payload.put("external_maid_agent_turns", Config.isExternalMaidAgentMode());
        payload.put("maid_agent_turn_mode", Config.maidAgentTurnMode);
        payload.put("external_agent_emoji", Config.enableExternalAgentEmoji);
        payload.put("external_agent_emoji_formats", SUPPORTED_EXTERNAL_AGENT_EMOJI_FORMATS);
        putServerStatus(payload);

        var frame = baseFrame(BridgeProtocol.TYPE_SESSION_READY, payload);
        putReplyRouting(frame, replyTo, traceId);
        return BridgeJson.toJson(frame, Config.maxBridgeMessageBytes);
    }

    public static String domainResponseFrame(String type, String replyTo, String traceId, Map<String, Object> payload) {
        return responseFrame(type, replyTo, traceId, payload);
    }

    public static String domainErrorFrame(String type, String replyTo, String traceId, String error) {
        return errorFrame(type, replyTo, traceId, error);
    }

    public static String bridgeErrorFrame(String replyTo, String traceId, String error) {
        return errorFrame("", replyTo, traceId, error);
    }

    public static String eventFrame(AiChainEvent event) {
        return BridgeJson.toJson(eventFrameMap(event), Config.maxBridgeMessageBytes);
    }

    public static Map<String, Object> eventFrameMap(AiChainEvent event) {
        var payload = event.payload();
        if (BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST.equals(event.type())) {
            return agentTurnRequestFrame(payload);
        }
        var frame = baseFrame(event.type(), payload);
        var payloadRequestId = stringValue(payload.get("request_id"));
        if (!payloadRequestId.isBlank()) {
            frame.put("request_id", payloadRequestId);
        }
        var payloadTraceId = stringValue(payload.get("trace_id"));
        if (!payloadTraceId.isBlank()) {
            frame.put("trace_id", payloadTraceId);
        }
        return frame;
    }

    private static String responseFrame(
            String type,
            String replyTo,
            String traceId,
            Map<String, Object> extraPayload
    ) {
        LinkedHashMap<String, Object> payload = extraPayload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraPayload);
        var frame = baseFrame(type, payload);
        putReplyRouting(frame, replyTo, traceId);
        return BridgeJson.toJson(frame, Config.maxBridgeMessageBytes);
    }

    private static String errorFrame(String failedType, String replyTo, String traceId, String error) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", firstNonBlank(error, "未知错误"));
        var normalizedFailedType = firstNonBlank(failedType, "");
        if (!normalizedFailedType.isBlank()) {
            payload.put("failed_type", normalizedFailedType);
        }
        var frame = baseFrame(BridgeProtocol.TYPE_BRIDGE_ERROR, payload);
        putReplyRouting(frame, replyTo, traceId);
        return BridgeJson.toJson(frame, Config.maxBridgeMessageBytes);
    }

    private static Map<String, Object> baseFrame(String type, Map<String, Object> payload) {
        var id = UUID.randomUUID().toString();
        var traceId = "trace-" + UUID.randomUUID();
        LinkedHashMap<String, Object> frame = new LinkedHashMap<>();
        frame.put("protocol", BridgeProtocol.PROTOCOL);
        frame.put("type", type);
        frame.put("id", id);
        frame.put("trace_id", traceId);
        frame.put("direction", BridgeProtocol.DIRECTION_JAVA_TO_CLIENT);
        frame.put("source_endpoint", firstNonBlank(Config.sourceEndpoint, Config.DEFAULT_SOURCE_ENDPOINT));
        frame.put("target_endpoint", firstNonBlank(Config.targetEndpoint, Config.DEFAULT_TARGET_ENDPOINT));
        frame.put("payload", payload == null ? Map.of() : payload);
        return frame;
    }

    private static Map<String, Object> agentTurnRequestFrame(Map<String, Object> payload) {
        var frame = baseFrame(BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST, payload);
        var requestId = firstNonBlank(stringValue(payload.get("request_id")), stringValue(payload.get("turn_id")), stringValue(frame.get("id")));
        frame.put("request_id", requestId);
        return frame;
    }

    private static void putReplyRouting(Map<String, Object> frame, String replyTo, String traceId) {
        var normalizedReplyTo = firstNonBlank(replyTo, "");
        if (!normalizedReplyTo.isBlank()) {
            frame.put("reply_to", normalizedReplyTo);
        }
        var normalizedTraceId = firstNonBlank(traceId, "");
        if (!normalizedTraceId.isBlank()) {
            frame.put("trace_id", normalizedTraceId);
        }
    }

    private static void putServerStatus(Map<String, Object> payload) {
        payload.put("features", Map.of(
                "ai_chain_capture", Config.enableAiChainCapture,
                "gateway_chat_capture", Config.enableGatewayChatCapture,
                "maid_message_bridge", Config.enableMaidMessageBridge,
                "maid_api_exposure", Config.enableMaidApiExposure,
                "external_maid_agent_turns", Config.isExternalMaidAgentMode(),
                "maid_agent_turn_mode", Config.maidAgentTurnMode,
                "external_agent_emoji", Config.enableExternalAgentEmoji,
                "external_agent_emoji_formats", SUPPORTED_EXTERNAL_AGENT_EMOJI_FORMATS
        ));
        payload.put("capabilities", BridgeProtocol.serverCapabilities(
                Config.enableAiChainCapture,
                Config.captureRawLlmRequestBodies,
                Config.enableGatewayChatCapture,
                Config.enableMaidMessageBridge,
                Config.enableMaidApiExposure,
                Config.enableMaidApiActions,
                Config.isExternalMaidAgentMode(),
                Config.isExternalMaidAgentMode(),
                Config.bridgeServerEnabled,
                Config.enableExternalAgentEmoji,
                SUPPORTED_EXTERNAL_AGENT_EMOJI_FORMATS
        ));
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        return safeString(value);
    }

    private static String safeString(Object value) {
        return String.valueOf(value).trim();
    }
}
