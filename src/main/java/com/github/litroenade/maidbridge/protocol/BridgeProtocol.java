package com.github.litroenade.maidbridge.protocol;

import java.util.Map;

/**
 * MaidBridge 网络协议常量。
 * <p>
 * Java mod、WebSocket 客户端和 Python 适配器都依赖这些 type、direction 和前缀；统一维护可以减少协议字符串漂移。
 */
public final class BridgeProtocol {
    public static final String PROTOCOL = "maidbridge.maid";

    public static final String TYPE_SESSION_INITIALIZE = "bridge.session.initialize";
    public static final String TYPE_SESSION_READY = "bridge.session.ready";
    public static final String TYPE_BRIDGE_ERROR = "bridge.error";
    public static final String TYPE_GATEWAY_MESSAGE = "bridge.gateway.message";
    public static final String TYPE_GATEWAY_RESPONSE = "bridge.gateway.response";
    public static final String TYPE_MAID_MESSAGE_IN = "maid.message.in";
    public static final String TYPE_MAID_MESSAGE_OUT = "maid.message.out";
    public static final String TYPE_MAID_MESSAGE_RESPONSE = "maid.message.response";
    public static final String TYPE_MAID_AGENT_TURN_REQUEST = "maid.agent.turn.request";
    public static final String TYPE_MAID_AGENT_TURN_COMPLETE = "maid.agent.turn.complete";
    public static final String TYPE_MAID_AGENT_TURN_OUTCOME_REPLY = "reply";
    public static final String TYPE_MAID_AGENT_TURN_OUTCOME_NO_REPLY = "no_reply";
    public static final String TYPE_MAID_AGENT_TURN_OUTCOME_DROP = "drop";
    public static final String TYPE_MAID_AGENT_TURN_OUTCOME_DISCONNECT = "disconnect";
    public static final String TYPE_MAID_AGENT_TURN_OUTCOME_DEADLINE = "deadline";
    public static final String TYPE_MAID_API_QUERY_MAIDS = "maid.api.query.maids";
    public static final String TYPE_MAID_API_QUERY_MAID = "maid.api.query.maid";
    public static final String TYPE_MAID_API_QUERY_REGISTRY = "maid.api.query.registry";
    public static final String TYPE_MAID_API_CALL_MAID_ACTION = "maid.api.call.maid_action";
    public static final String TYPE_MAID_API_QUERY_MAID_TOOL_SCHEMA = "maid.api.query.maid_tool_schema";
    public static final String TYPE_MAID_API_QUERY_MAID_CONTEXT = "maid.api.query.maid_context";
    public static final String TYPE_MAID_API_CALL_MAID_TOOL = "maid.api.call.maid_tool";
    public static final String TYPE_MAID_API_QUERY_SKILLS = "maid.api.query.skills";
    public static final String TYPE_MAID_API_QUERY_SKILL = "maid.api.query.skill";
    public static final String TYPE_MAID_API_RESPONSE = "maid.api.response";

    public static final String DIRECTION_CLIENT_TO_JAVA = "client_to_java";
    public static final String DIRECTION_JAVA_TO_CLIENT = "java_to_client";

    public static final String PREFIX_MAID_AI = "maid.ai.";
    public static final String PREFIX_MAID_MESSAGE = "maid.message.";
    public static final String PREFIX_MAID_AGENT = "maid.agent.";
    public static final String PREFIX_MAID_API = "maid.api.";
    public static final String PREFIX_MAID_API_QUERY = "maid.api.query.";
    public static final String PREFIX_MAID_API_CALL = "maid.api.call.";
    public static final String PREFIX_MAID_API_REGISTRY = "maid.api.registry.";
    public static final String PREFIX_BRIDGE_GATEWAY = "bridge.gateway.";
    public static final String PREFIX_MAIDBRIDGE_SERVER = "maidbridge.server.";

    public static final String ERROR_MISSING_MAID_UUID = "payload.maid.uuid 必须是非空字符串";

    private BridgeProtocol() {
    }

    public static boolean isSessionInitializeType(String type) {
        return TYPE_SESSION_INITIALIZE.equals(type);
    }

    public static boolean isSupportedMaidApiType(String type) {
        return TYPE_MAID_API_QUERY_MAIDS.equals(type)
                || TYPE_MAID_API_QUERY_MAID.equals(type)
                || TYPE_MAID_API_QUERY_REGISTRY.equals(type)
                || TYPE_MAID_API_CALL_MAID_ACTION.equals(type)
                || TYPE_MAID_API_QUERY_MAID_TOOL_SCHEMA.equals(type)
                || TYPE_MAID_API_QUERY_MAID_CONTEXT.equals(type)
                || TYPE_MAID_API_CALL_MAID_TOOL.equals(type)
                || TYPE_MAID_API_QUERY_SKILLS.equals(type)
                || TYPE_MAID_API_QUERY_SKILL.equals(type);
    }

    public static Map<String, Object> serverCapabilities(
            boolean aiChainCapture,
            boolean rawAiTrace,
            boolean gatewayChat,
            boolean maidMessageBridge,
            boolean maidApiExposure,
            boolean maidApiCall,
            boolean externalMaidAgentTurns,
            boolean nativeAiChatIntercept,
            boolean bridgeServer
    ) {
        return Map.ofEntries(
                Map.entry("ai_chain_capture", aiChainCapture),
                Map.entry("raw_ai_trace", rawAiTrace),
                Map.entry("gateway_chat", gatewayChat),
                Map.entry("maid_message_bridge", maidMessageBridge),
                Map.entry("maid_api_exposure", maidApiExposure),
                Map.entry("direct_llm_override", false),
                Map.entry("maid_api_call", maidApiCall),
                Map.entry("maid_agent_turn_request", externalMaidAgentTurns),
                Map.entry("maid_agent_turn_complete", externalMaidAgentTurns),
                Map.entry("domain_response", true),
                Map.entry("native_ai_chat_intercept", nativeAiChatIntercept),
                Map.entry("bridge_server", bridgeServer)
        );
    }
}
