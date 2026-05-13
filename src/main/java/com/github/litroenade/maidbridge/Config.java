package com.github.litroenade.maidbridge;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class Config {
    public static final String DEFAULT_SOURCE_ENDPOINT = "maidbridge-java";
    public static final String DEFAULT_TARGET_ENDPOINT = "external-agent";
    public static final int DEFAULT_BRIDGE_SERVER_PORT = 8765;
    public static final String DEFAULT_BRIDGE_SERVER_HOST = "127.0.0.1";
    public static final String DEFAULT_BRIDGE_SERVER_PATH = "/maidbridge";
    public static final String DEFAULT_BRIDGE_SERVER_URL = "ws://127.0.0.1:8765/maidbridge";
    public static final String DEFAULT_GATEWAY_CHAT_ROOM_ID = "server:default";
    public static final String DEFAULT_GATEWAY_CHAT_ROOM_NAME = "Minecraft Server";
    public static final String DEFAULT_INBOUND_GATEWAY_MESSAGE_PREFIX = "[Bridge] ";
    public static final String DEFAULT_MAID_INJECTION_POLICY = "owner_online";
    public static final String MAID_CHAT_MODE_NATIVE = "native";
    public static final String MAID_CHAT_MODE_EXTERNAL_AGENT = "external_agent";
    public static final String MAID_CHAT_MODE_TLM_BRIDGE = "tlm_bridge";
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLE_AI_CHAIN_CAPTURE = BUILDER
            .comment("捕获女仆本体 AIChat 链路事件，供 MaidBridge 诊断使用。")
            .define("enableAiChainCapture", false);

    private static final ModConfigSpec.BooleanValue ENABLE_GATEWAY_CHAT_CAPTURE = BUILDER
            .comment("把普通 Minecraft 服务器聊天捕获为 bridge.gateway.message 事件。")
            .define("enableGatewayChatCapture", false);

    private static final ModConfigSpec.BooleanValue ENABLE_INBOUND_GATEWAY_MESSAGES = BUILDER
            .comment("允许外部 bridge.gateway.message 帧发送给在线 Minecraft 玩家。")
            .define("enableInboundGatewayMessages", false);

    private static final ModConfigSpec.BooleanValue ENABLE_MAID_MESSAGE_BRIDGE = BUILDER
            .comment("启用女仆消息事件，以及外部 maid.message.in 注入。")
            .define("enableMaidMessageBridge", false);

    private static final ModConfigSpec.BooleanValue ENABLE_MULTIPLAYER_MAID_CHAT = BUILDER
            .comment("允许非 owner 在多人服务器打开并发送女仆聊天；默认关闭以保留原版权限边界。")
            .define("enableMultiplayerMaidChat", false);

    private static final ModConfigSpec.IntValue MAID_EXTERNAL_TURN_TTL_MS = BUILDER
            .comment("外部女仆轮次的保留时间；超时后新的外部轮次才可替换它。")
            .defineInRange("maidExternalTurnTtlMs", 120000, 1000, 1800000);

    private static final ModConfigSpec.IntValue MAX_PENDING_MAID_AGENT_TURNS = BUILDER
            .comment("外部女仆 agent 待处理轮次的全局上限；超过后拒绝新的接管请求，避免外部客户端异常时无限堆积。")
            .defineInRange("maxPendingMaidAgentTurns", 256, 1, 4096);

    private static final ModConfigSpec.ConfigValue<String> MAID_INJECTION_POLICY = BUILDER
            .comment("maid.message.in 注入策略；当前实现支持 owner_online。")
            .define("maidInjectionPolicy", DEFAULT_MAID_INJECTION_POLICY);

    private static final ModConfigSpec.BooleanValue ENABLE_MAID_API_EXPOSURE = BUILDER
            .comment("允许外部客户端查询女仆状态，以及 TLM 的工具、技能、任务等只读信息。")
            .define("enableMaidApiExposure", true);

    private static final ModConfigSpec.BooleanValue ENABLE_MAID_API_ACTIONS = BUILDER
            .comment("允许外部客户端通过 maid.api.call.* 操作女仆；如果 WebSocket 暴露到公网，建议关闭或设置 Token。")
            .define("enableMaidApiActions", true);

    private static final ModConfigSpec.BooleanValue ENABLE_EXTERNAL_AGENT_EMOJI = BUILDER
            .comment("允许外部女仆 agent 在当前回合附加表情包或颜文字气泡。")
            .define("enableExternalAgentEmoji", false);

    private static final ModConfigSpec.BooleanValue CAPTURE_RAW_LLM_REQUEST_BODIES = BUILDER
            .comment("在 maid.ai.llm.request 诊断事件中包含完整原始 OpenAI 兼容请求体；默认关闭以避免泄漏提示词。")
            .define("captureRawLlmRequestBodies", false);

    private static final ModConfigSpec.IntValue MAX_RAW_LLM_REQUEST_CHARACTERS = BUILDER
            .comment("启用 captureRawLlmRequestBodies 后，最多保留的原始 LLM 请求体字符数。")
            .defineInRange("maxRawLlmRequestCharacters", 4096, 0, 65536);

    private static final ModConfigSpec.ConfigValue<String> GATEWAY_CHAT_ROOM_ID = BUILDER
            .comment("普通 Minecraft 服务器聊天被桥接时使用的房间 ID。")
            .define("gatewayChatRoomId", DEFAULT_GATEWAY_CHAT_ROOM_ID);

    private static final ModConfigSpec.ConfigValue<String> GATEWAY_CHAT_ROOM_NAME = BUILDER
            .comment("普通 Minecraft 服务器聊天被桥接时使用的房间名称。")
            .define("gatewayChatRoomName", DEFAULT_GATEWAY_CHAT_ROOM_NAME);

    private static final ModConfigSpec.BooleanValue GATEWAY_CHAT_USE_RAW_TEXT = BUILDER
            .comment("使用 ServerChatEvent 原始文本作为 plain_text；关闭时使用渲染后的消息组件文本。")
            .define("gatewayChatUseRawText", false);

    private static final ModConfigSpec.ConfigValue<String> INBOUND_GATEWAY_MESSAGE_PREFIX = BUILDER
            .comment("外部 bridge.gateway.message 文本发送给 Minecraft 玩家前追加的前缀。")
            .define("inboundGatewayMessagePrefix", DEFAULT_INBOUND_GATEWAY_MESSAGE_PREFIX);

    private static final ModConfigSpec.IntValue MAX_INBOUND_GATEWAY_TEXT_CHARACTERS = BUILDER
            .comment("单个外部 bridge.gateway.message 文本字段允许的最大字符数。")
            .defineInRange("maxInboundGatewayTextCharacters", 1024, 1, 8192);

    private static final ModConfigSpec.IntValue MAX_PENDING_INBOUND_GATEWAY_MESSAGES = BUILDER
            .comment("等待 Minecraft 服务端线程分发的外部 bridge.gateway.message 帧上限。")
            .defineInRange("maxPendingInboundGatewayMessages", 64, 1, 1024);

    private static final ModConfigSpec.IntValue MAX_PENDING_MAID_OPERATIONS_PER_KEY = BUILDER
            .comment("同一只女仆允许排队的写操作上限；超过后拒绝新的 maid.api 调用。")
            .defineInRange("maxPendingMaidOperationsPerKey", 64, 1, 1024);

    private static final ModConfigSpec.ConfigValue<String> SOURCE_ENDPOINT = BUILDER
            .comment("当前 NeoForge mod 对外声明的端点 ID。")
            .define("sourceEndpoint", DEFAULT_SOURCE_ENDPOINT);

    private static final ModConfigSpec.ConfigValue<String> TARGET_ENDPOINT = BUILDER
            .comment("MaidBridge 客户端默认连接的远端端点 ID。")
            .define("targetEndpoint", DEFAULT_TARGET_ENDPOINT);

    private static final ModConfigSpec.BooleanValue ENABLE_EXTERNAL_MAID_AGENT_TURNS = BUILDER
            .comment("让 MaidBridge 把 MaidAIChatManager.chat 入口交给外部女仆 agent，而不是走女仆本体原生 AIChat。")
            .define("enableExternalMaidAgentTurns", false);

    private static final ModConfigSpec.ConfigValue<String> MAID_AGENT_TURN_MODE = BUILDER
            .comment("女仆聊天接管模式：native 使用 TLM 原生，external_agent 强制外部 agent，tlm_bridge 预留 TLM+Bridge 模式，当前按原生链路处理。")
            .define("maidAgentTurnMode", MAID_CHAT_MODE_NATIVE);

    private static final ModConfigSpec.BooleanValue BRIDGE_SERVER_ENABLED = BUILDER
            .comment("把 MaidBridge 作为 WebSocket 服务端运行，接受外部 maidbridge.maid 客户端。")
            .define("bridgeServerEnabled", false);

    private static final ModConfigSpec.ConfigValue<String> BRIDGE_SERVER_URL = BUILDER
            .comment("MaidBridge WebSocket 地址，例如 ws://127.0.0.1:8765/maidbridge。")
            .define("bridgeServerUrl", DEFAULT_BRIDGE_SERVER_URL);

    private static final ModConfigSpec.ConfigValue<String> BRIDGE_ACCESS_TOKEN = BUILDER
            .comment("MaidBridge WebSocket 客户端可选的 Bearer Token 校验值。")
            .define("bridgeAccessToken", "");

    private static final ModConfigSpec.IntValue MAX_BRIDGE_MESSAGE_BYTES = BUILDER
            .comment("单个序列化桥接帧允许的最大 UTF-8 字节数。")
            .defineInRange("maxBridgeMessageBytes", 32768, 1024, 1048576);

    private static final ModConfigSpec.IntValue MAX_OUTBOUND_FRAMES = BUILDER
            .comment("WebSocket 发送端接入前允许排队的序列化桥接帧上限。")
            .defineInRange("maxOutboundFrames", 512, 32, 8192);

    private static final ModConfigSpec.IntValue MAX_BUFFERED_EVENTS = BUILDER
            .comment("WebSocket 传输层接入前，内存中保留的 AI 链路事件上限。")
            .defineInRange("maxBufferedEvents", 512, 32, 8192);

    private static final ModConfigSpec.BooleanValue LOG_CAPTURED_EVENTS = BUILDER
            .comment("用 debug 级别输出捕获到的 AI 链路事件。")
            .define("logCapturedEvents", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean enableAiChainCapture;
    public static boolean enableGatewayChatCapture;
    public static boolean enableInboundGatewayMessages;
    public static boolean enableMaidMessageBridge;
    public static boolean enableMultiplayerMaidChat;
    public static int maidExternalTurnTtlMs = 120000;
    public static int maxPendingMaidAgentTurns = 256;
    public static String maidInjectionPolicy = DEFAULT_MAID_INJECTION_POLICY;
    public static boolean enableMaidApiExposure = true;
    public static boolean enableMaidApiActions = true;
    public static boolean enableExternalAgentEmoji;
    public static boolean captureRawLlmRequestBodies;
    public static int maxRawLlmRequestCharacters = 4096;
    public static String gatewayChatRoomId = DEFAULT_GATEWAY_CHAT_ROOM_ID;
    public static String gatewayChatRoomName = DEFAULT_GATEWAY_CHAT_ROOM_NAME;
    public static boolean gatewayChatUseRawText;
    public static String inboundGatewayMessagePrefix = DEFAULT_INBOUND_GATEWAY_MESSAGE_PREFIX;
    public static int maxInboundGatewayTextCharacters = 1024;
    public static int maxPendingInboundGatewayMessages = 64;
    public static int maxPendingMaidOperationsPerKey = 64;
    public static String sourceEndpoint = DEFAULT_SOURCE_ENDPOINT;
    public static String targetEndpoint = DEFAULT_TARGET_ENDPOINT;
    public static String maidAgentTurnMode = MAID_CHAT_MODE_NATIVE;
    public static boolean enableExternalMaidAgentTurns;
    public static boolean bridgeServerEnabled;
    public static String bridgeServerUrl = DEFAULT_BRIDGE_SERVER_URL;
    public static String bridgeServerHost = DEFAULT_BRIDGE_SERVER_HOST;
    public static int bridgeServerPort = DEFAULT_BRIDGE_SERVER_PORT;
    public static String bridgeServerPath = DEFAULT_BRIDGE_SERVER_PATH;
    public static String bridgeAccessToken = "";
    public static int maxBridgeMessageBytes = 32768;
    public static int maxOutboundFrames = 512;
    public static int maxBufferedEvents = 512;
    public static boolean logCapturedEvents;

    private Config() {
    }

    public static boolean isExternalMaidAgentMode() {
        return MAID_CHAT_MODE_EXTERNAL_AGENT.equals(maidAgentTurnMode) || enableExternalMaidAgentTurns;
    }

    public static void setEnableGatewayChatCapture(boolean value) {
        setAndSave(ENABLE_GATEWAY_CHAT_CAPTURE, value);
    }

    public static void setEnableAiChainCapture(boolean value) {
        setAndSave(ENABLE_AI_CHAIN_CAPTURE, value);
    }

    public static void setEnableInboundGatewayMessages(boolean value) {
        setAndSave(ENABLE_INBOUND_GATEWAY_MESSAGES, value);
    }

    public static void setEnableMultiplayerMaidChat(boolean value) {
        setAndSave(ENABLE_MULTIPLAYER_MAID_CHAT, value);
    }

    public static void setMaidExternalTurnTtlMs(int value) {
        setAndSave(MAID_EXTERNAL_TURN_TTL_MS, value);
    }

    public static void setMaxPendingMaidAgentTurns(int value) {
        setAndSave(MAX_PENDING_MAID_AGENT_TURNS, value);
    }

    public static void setMaidInjectionPolicy(String value) {
        setAndSave(MAID_INJECTION_POLICY, value);
    }

    public static void setEnableMaidApiExposure(boolean value) {
        setAndSave(ENABLE_MAID_API_EXPOSURE, value);
    }

    public static void setEnableMaidApiActions(boolean value) {
        setAndSave(ENABLE_MAID_API_ACTIONS, value);
    }

    public static void setEnableExternalAgentEmoji(boolean value) {
        setAndSave(ENABLE_EXTERNAL_AGENT_EMOJI, value);
    }

    public static void setCaptureRawLlmRequestBodies(boolean value) {
        setAndSave(CAPTURE_RAW_LLM_REQUEST_BODIES, value);
    }

    public static void setMaxRawLlmRequestCharacters(int value) {
        setAndSave(MAX_RAW_LLM_REQUEST_CHARACTERS, value);
    }

    public static void setGatewayChatRoomId(String value) {
        setAndSave(GATEWAY_CHAT_ROOM_ID, value);
    }

    public static void setGatewayChatRoomName(String value) {
        setAndSave(GATEWAY_CHAT_ROOM_NAME, value);
    }

    public static void setGatewayChatUseRawText(boolean value) {
        setAndSave(GATEWAY_CHAT_USE_RAW_TEXT, value);
    }

    public static void setInboundGatewayMessagePrefix(String value) {
        setAndSave(INBOUND_GATEWAY_MESSAGE_PREFIX, value);
    }

    public static void setMaxInboundGatewayTextCharacters(int value) {
        setAndSave(MAX_INBOUND_GATEWAY_TEXT_CHARACTERS, value);
    }

    public static void setMaxPendingInboundGatewayMessages(int value) {
        setAndSave(MAX_PENDING_INBOUND_GATEWAY_MESSAGES, value);
    }

    public static void setMaxPendingMaidOperationsPerKey(int value) {
        setAndSave(MAX_PENDING_MAID_OPERATIONS_PER_KEY, value);
    }

    public static void setSourceEndpoint(String value) {
        setAndSave(SOURCE_ENDPOINT, value);
    }

    public static void setTargetEndpoint(String value) {
        setAndSave(TARGET_ENDPOINT, value);
    }

    public static void setMaidAgentTurnMode(String value) {
        var normalizedMode = normalizeMaidAgentTurnMode(value);
        MAID_AGENT_TURN_MODE.set(normalizedMode);
        ENABLE_EXTERNAL_MAID_AGENT_TURNS.set(MAID_CHAT_MODE_EXTERNAL_AGENT.equals(normalizedMode));
        MAID_AGENT_TURN_MODE.save();
        ENABLE_EXTERNAL_MAID_AGENT_TURNS.save();
        refreshFromSpec();
    }

    public static void setBridgeServerEnabled(boolean value) {
        setAndSave(BRIDGE_SERVER_ENABLED, value);
    }

    public static void setBridgeServerUrl(String value) {
        var endpoint = parseBridgeServerUrl(value, currentBridgeServerEndpoint());
        BRIDGE_SERVER_URL.set(endpoint.url());
        BRIDGE_SERVER_URL.save();
        refreshFromSpec();
    }

    public static void setBridgeAccessToken(String value) {
        setAndSave(BRIDGE_ACCESS_TOKEN, value);
    }

    public static void setMaxBridgeMessageBytes(int value) {
        setAndSave(MAX_BRIDGE_MESSAGE_BYTES, value);
    }

    public static void setMaxOutboundFrames(int value) {
        setAndSave(MAX_OUTBOUND_FRAMES, value);
    }

    public static void setMaxBufferedEvents(int value) {
        setAndSave(MAX_BUFFERED_EVENTS, value);
    }

    public static void setLogCapturedEvents(boolean value) {
        setAndSave(LOG_CAPTURED_EVENTS, value);
    }

    public static void refreshFromSpec() {
        enableAiChainCapture = ENABLE_AI_CHAIN_CAPTURE.get();
        enableGatewayChatCapture = ENABLE_GATEWAY_CHAT_CAPTURE.get();
        enableInboundGatewayMessages = ENABLE_INBOUND_GATEWAY_MESSAGES.get();
        enableMaidMessageBridge = ENABLE_MAID_MESSAGE_BRIDGE.get();
        enableMultiplayerMaidChat = ENABLE_MULTIPLAYER_MAID_CHAT.get();
        maidExternalTurnTtlMs = MAID_EXTERNAL_TURN_TTL_MS.get();
        maxPendingMaidAgentTurns = MAX_PENDING_MAID_AGENT_TURNS.get();
        maidInjectionPolicy = MAID_INJECTION_POLICY.get();
        enableMaidApiExposure = ENABLE_MAID_API_EXPOSURE.get();
        enableMaidApiActions = ENABLE_MAID_API_ACTIONS.get();
        enableExternalAgentEmoji = ENABLE_EXTERNAL_AGENT_EMOJI.get();
        captureRawLlmRequestBodies = CAPTURE_RAW_LLM_REQUEST_BODIES.get();
        maxRawLlmRequestCharacters = MAX_RAW_LLM_REQUEST_CHARACTERS.get();
        gatewayChatRoomId = GATEWAY_CHAT_ROOM_ID.get();
        gatewayChatRoomName = GATEWAY_CHAT_ROOM_NAME.get();
        gatewayChatUseRawText = GATEWAY_CHAT_USE_RAW_TEXT.get();
        inboundGatewayMessagePrefix = normalizeGatewayPrefix(INBOUND_GATEWAY_MESSAGE_PREFIX.get());
        maxInboundGatewayTextCharacters = MAX_INBOUND_GATEWAY_TEXT_CHARACTERS.get();
        maxPendingInboundGatewayMessages = MAX_PENDING_INBOUND_GATEWAY_MESSAGES.get();
        maxPendingMaidOperationsPerKey = MAX_PENDING_MAID_OPERATIONS_PER_KEY.get();
        sourceEndpoint = SOURCE_ENDPOINT.get();
        targetEndpoint = normalizeTargetEndpoint(TARGET_ENDPOINT.get());
        maidAgentTurnMode = normalizeMaidAgentTurnMode(MAID_AGENT_TURN_MODE.get());
        if (MAID_CHAT_MODE_NATIVE.equals(maidAgentTurnMode) && ENABLE_EXTERNAL_MAID_AGENT_TURNS.get()) {
            maidAgentTurnMode = MAID_CHAT_MODE_EXTERNAL_AGENT;
        }
        enableExternalMaidAgentTurns = MAID_CHAT_MODE_EXTERNAL_AGENT.equals(maidAgentTurnMode);
        bridgeServerEnabled = BRIDGE_SERVER_ENABLED.get();
        var endpoint = bridgeServerEndpoint();
        bridgeServerUrl = endpoint.url();
        bridgeServerHost = endpoint.host();
        bridgeServerPort = endpoint.port();
        bridgeServerPath = endpoint.path();
        bridgeAccessToken = BRIDGE_ACCESS_TOKEN.get();
        maxBridgeMessageBytes = MAX_BRIDGE_MESSAGE_BYTES.get();
        maxOutboundFrames = MAX_OUTBOUND_FRAMES.get();
        maxBufferedEvents = MAX_BUFFERED_EVENTS.get();
        logCapturedEvents = LOG_CAPTURED_EVENTS.get();
    }

    private static <T> void setAndSave(ModConfigSpec.ConfigValue<T> configValue, T value) {
        configValue.set(value);
        configValue.save();
        refreshFromSpec();
    }

    private static String normalizeGatewayPrefix(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_INBOUND_GATEWAY_MESSAGE_PREFIX;
        }
        return value;
    }

    private static String normalizeTargetEndpoint(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_TARGET_ENDPOINT;
        }
        return value;
    }

    private static String normalizeMaidAgentTurnMode(String value) {
        var normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case MAID_CHAT_MODE_EXTERNAL_AGENT, MAID_CHAT_MODE_TLM_BRIDGE -> normalized;
            default -> MAID_CHAT_MODE_NATIVE;
        };
    }

    private static BridgeServerEndpoint bridgeServerEndpoint() {
        return parseBridgeServerUrl(BRIDGE_SERVER_URL.get(), defaultBridgeServerEndpoint());
    }

    private static BridgeServerEndpoint currentBridgeServerEndpoint() {
        return new BridgeServerEndpoint(bridgeServerHost, bridgeServerPort, bridgeServerPath);
    }

    private static BridgeServerEndpoint defaultBridgeServerEndpoint() {
        return new BridgeServerEndpoint(DEFAULT_BRIDGE_SERVER_HOST, DEFAULT_BRIDGE_SERVER_PORT, DEFAULT_BRIDGE_SERVER_PATH);
    }

    private static BridgeServerEndpoint parseBridgeServerUrl(String value, BridgeServerEndpoint fallback) {
        var normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return fallback;
        }
        if (!normalized.contains("://")) {
            normalized = "ws://" + normalized;
        }
        try {
            var uri = new URI(normalized);
            if (!"ws".equalsIgnoreCase(uri.getScheme())) {
                return fallback;
            }
            var host = normalizeBridgeHost(uri.getHost());
            var port = uri.getPort() > 0 ? uri.getPort() : DEFAULT_BRIDGE_SERVER_PORT;
            var path = normalizeBridgePath(uri.getRawPath());
            return new BridgeServerEndpoint(host, port, path);
        } catch (URISyntaxException exception) {
            return fallback;
        }
    }

    private static String normalizeBridgeHost(String value) {
        return value == null || value.isBlank() ? DEFAULT_BRIDGE_SERVER_HOST : value.trim();
    }

    private static String normalizeBridgePath(String value) {
        var path = value == null || value.isBlank() ? DEFAULT_BRIDGE_SERVER_PATH : value.trim();
        return path.startsWith("/") ? path : "/" + path;
    }

    private record BridgeServerEndpoint(String host, int port, String path) {
        String url() {
            return "ws://%s:%d%s".formatted(urlHost(), port, path);
        }

        private String urlHost() {
            return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        }
    }

    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            refreshFromSpec();
        }
    }

}
