package com.github.litroenade.maidbridge;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

public final class Config {
    public static final String DEFAULT_SOURCE_ENDPOINT = "maidbridge-java";
    public static final String DEFAULT_TARGET_ENDPOINT = "external-agent";
    public static final int DEFAULT_BRIDGE_SERVER_PORT = 8765;
    public static final int DEFAULT_MAX_BRIDGE_MESSAGE_BYTES = 32 * 1024 * 1024;
    public static final int DEFAULT_BRIDGE_CONNECTION_LOST_TIMEOUT_SECONDS = 20;
    public static final int MAX_BRIDGE_MESSAGE_BYTES_LIMIT = 64 * 1024 * 1024;
    public static final String DEFAULT_BRIDGE_SERVER_HOST = "127.0.0.1";
    public static final String DEFAULT_BRIDGE_SERVER_PATH = "/maidbridge";
    public static final String DEFAULT_BRIDGE_SERVER_URL = "ws://127.0.0.1:8765/maidbridge";
    public static final String DEFAULT_SERVER_CHAT_ROOM_ID = "server:default";
    public static final String DEFAULT_SERVER_CHAT_ROOM_NAME = "Minecraft Server";
    public static final String DEFAULT_SERVER_CHAT_SYSTEM_BROADCAST_PREFIX = "[Bridge] ";
    public static final int DEFAULT_MAX_SERVER_CHAT_TEXT_CHARACTERS = 256;
    public static final String DEFAULT_MAID_INJECTION_POLICY = "owner_online";
    public static final int DEFAULT_EXTERNAL_EMOJI_CACHE_TTL_MS = 120000;
    public static final int DEFAULT_MAX_EXTERNAL_EMOJI_CACHE_ENTRIES = 128;
    public static final String MAID_CHAT_MODE_NATIVE = "native";
    public static final String MAID_CHAT_MODE_EXTERNAL_AGENT = "external_agent";
    public static final String MAID_CHAT_MODE_TLM_BRIDGE = "tlm_bridge";
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLE_AI_CHAIN_CAPTURE = BUILDER
            .comment("捕获女仆本体 AIChat 链路事件，供 MaidBridge 诊断使用。")
            .define("enableAiChainCapture", false);

    private static final ModConfigSpec.BooleanValue ENABLE_SERVER_CHAT_BRIDGE = BUILDER
            .comment("把 Minecraft 公共聊天作为服务器群聊房间发布给外部客户端。")
            .define("enableServerChatBridge", false);

    private static final ModConfigSpec.BooleanValue ENABLE_EXTERNAL_SERVER_CHAT_MESSAGES = BUILDER
            .comment("允许 MaiBot 或其他外部客户端把文字发到 Minecraft 聊天栏。")
            .define("enableExternalServerChatMessages", false);

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

    private static final ModConfigSpec.IntValue EXTERNAL_EMOJI_CACHE_TTL_MS = BUILDER
            .comment("外部 agent 表情纹理补发缓存的保留时间；玩家进入追踪范围时会在该时间内补发纹理。")
            .defineInRange("externalEmojiCacheTtlMs", DEFAULT_EXTERNAL_EMOJI_CACHE_TTL_MS, 1000, 1800000);

    private static final ModConfigSpec.IntValue MAX_EXTERNAL_EMOJI_CACHE_ENTRIES = BUILDER
            .comment("外部 agent 表情纹理补发缓存条目上限；超出后会淘汰最旧纹理。")
            .defineInRange("maxExternalEmojiCacheEntries", DEFAULT_MAX_EXTERNAL_EMOJI_CACHE_ENTRIES, 1, 4096);

    private static final ModConfigSpec.BooleanValue CAPTURE_RAW_LLM_REQUEST_BODIES = BUILDER
            .comment("在 maid.ai.llm.request 诊断事件中包含完整原始 OpenAI 兼容请求体；默认关闭以避免泄漏提示词。")
            .define("captureRawLlmRequestBodies", false);

    private static final ModConfigSpec.IntValue MAX_RAW_LLM_REQUEST_CHARACTERS = BUILDER
            .comment("启用 captureRawLlmRequestBodies 后，最多保留的原始 LLM 请求体字符数。")
            .defineInRange("maxRawLlmRequestCharacters", 4096, 0, 65536);

    private static final ModConfigSpec.IntValue MAX_INBOUND_BRIDGE_TEXT_CHARACTERS = BUILDER
            .comment("外部客户端发回女仆消息和女仆回合文本时允许的最大字符数。")
            .defineInRange("maxInboundBridgeTextCharacters", 1024, 1, 8192);

    private static final ModConfigSpec.ConfigValue<String> SERVER_CHAT_ROOM_ID = BUILDER
            .comment("Minecraft 服务器群聊房间 ID。")
            .define("serverChatRoomId", DEFAULT_SERVER_CHAT_ROOM_ID);

    private static final ModConfigSpec.ConfigValue<String> SERVER_CHAT_ROOM_NAME = BUILDER
            .comment("Minecraft 服务器群聊房间名称。")
            .define("serverChatRoomName", DEFAULT_SERVER_CHAT_ROOM_NAME);

    private static final ModConfigSpec.BooleanValue SERVER_CHAT_USE_RAW_TEXT = BUILDER
            .comment("使用 ServerChatEvent 原始文本作为 plain_text；关闭时使用渲染后的消息组件文本。")
            .define("serverChatUseRawText", false);

    private static final ModConfigSpec.ConfigValue<String> SERVER_CHAT_SYSTEM_BROADCAST_PREFIX = BUILDER
            .comment("外部系统播报发送给 Minecraft 玩家前追加的前缀；普通群聊成员发言不会使用它。")
            .define("serverChatSystemBroadcastPrefix", DEFAULT_SERVER_CHAT_SYSTEM_BROADCAST_PREFIX);

    private static final ModConfigSpec.BooleanValue ENABLE_SERVER_CHAT_MAID_PRESENTATION = BUILDER
            .comment("允许后续服务器群聊回复让已加载的女仆做动作；现在不影响聊天，也不会加载区块。")
            .define("enableServerChatMaidPresentation", false);

    private static final ModConfigSpec.IntValue MAX_SERVER_CHAT_TEXT_CHARACTERS = BUILDER
            .comment("单个服务器群聊入站文本字段允许的最大字符数。")
            .defineInRange("maxServerChatTextCharacters", DEFAULT_MAX_SERVER_CHAT_TEXT_CHARACTERS, 1, DEFAULT_MAX_SERVER_CHAT_TEXT_CHARACTERS);

    private static final ModConfigSpec.IntValue MAX_PENDING_SERVER_CHAT_MESSAGES = BUILDER
            .comment("等待 Minecraft 服务端线程分发的服务器群聊帧上限。")
            .defineInRange("maxPendingServerChatMessages", 64, 1, 1024);

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
            .defineInRange("maxBridgeMessageBytes", DEFAULT_MAX_BRIDGE_MESSAGE_BYTES, 1024, MAX_BRIDGE_MESSAGE_BYTES_LIMIT);

    private static final ModConfigSpec.IntValue BRIDGE_CONNECTION_LOST_TIMEOUT_SECONDS = BUILDER
            .comment("WebSocket 心跳丢失后的断开秒数；0 表示关闭底层连接丢失检测。")
            .defineInRange("bridgeConnectionLostTimeoutSeconds", DEFAULT_BRIDGE_CONNECTION_LOST_TIMEOUT_SECONDS, 0, 300);

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
    public static boolean enableServerChatBridge;
    public static boolean enableExternalServerChatMessages;
    public static boolean enableMaidMessageBridge;
    public static boolean enableMultiplayerMaidChat;
    public static int maidExternalTurnTtlMs = 120000;
    public static int maxPendingMaidAgentTurns = 256;
    public static String maidInjectionPolicy = DEFAULT_MAID_INJECTION_POLICY;
    public static boolean enableMaidApiExposure = true;
    public static boolean enableMaidApiActions = true;
    public static boolean enableExternalAgentEmoji;
    public static int externalEmojiCacheTtlMs = DEFAULT_EXTERNAL_EMOJI_CACHE_TTL_MS;
    public static int maxExternalEmojiCacheEntries = DEFAULT_MAX_EXTERNAL_EMOJI_CACHE_ENTRIES;
    public static boolean captureRawLlmRequestBodies;
    public static int maxRawLlmRequestCharacters = 4096;
    public static int maxInboundBridgeTextCharacters = 1024;
    public static String serverChatRoomId = DEFAULT_SERVER_CHAT_ROOM_ID;
    public static String serverChatRoomName = DEFAULT_SERVER_CHAT_ROOM_NAME;
    public static boolean serverChatUseRawText;
    public static String serverChatSystemBroadcastPrefix = DEFAULT_SERVER_CHAT_SYSTEM_BROADCAST_PREFIX;
    public static boolean enableServerChatMaidPresentation;
    public static int maxServerChatTextCharacters = DEFAULT_MAX_SERVER_CHAT_TEXT_CHARACTERS;
    public static int maxPendingServerChatMessages = 64;
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
    public static int maxBridgeMessageBytes = DEFAULT_MAX_BRIDGE_MESSAGE_BYTES;
    public static int bridgeConnectionLostTimeoutSeconds = DEFAULT_BRIDGE_CONNECTION_LOST_TIMEOUT_SECONDS;
    public static int maxOutboundFrames = 512;
    public static int maxBufferedEvents = 512;
    public static boolean logCapturedEvents;
    private static Runnable runtimeConfigChangedHandler = () -> {
    };

    private Config() {
    }

    public static boolean isExternalMaidAgentMode() {
        return MAID_CHAT_MODE_EXTERNAL_AGENT.equals(maidAgentTurnMode) || enableExternalMaidAgentTurns;
    }

    public static void setRuntimeConfigChangedHandler(Runnable handler) {
        runtimeConfigChangedHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void setEnableServerChatBridge(boolean value) {
        setAndSave(ENABLE_SERVER_CHAT_BRIDGE, value);
    }

    public static void setEnableAiChainCapture(boolean value) {
        setAndSave(ENABLE_AI_CHAIN_CAPTURE, value);
    }

    public static void setEnableExternalServerChatMessages(boolean value) {
        setAndSave(ENABLE_EXTERNAL_SERVER_CHAT_MESSAGES, value);
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

    public static void setExternalEmojiCacheTtlMs(int value) {
        setAndSave(EXTERNAL_EMOJI_CACHE_TTL_MS, value);
    }

    public static void setMaxExternalEmojiCacheEntries(int value) {
        setAndSave(MAX_EXTERNAL_EMOJI_CACHE_ENTRIES, value);
    }

    public static void setCaptureRawLlmRequestBodies(boolean value) {
        setAndSave(CAPTURE_RAW_LLM_REQUEST_BODIES, value);
    }

    public static void setMaxRawLlmRequestCharacters(int value) {
        setAndSave(MAX_RAW_LLM_REQUEST_CHARACTERS, value);
    }

    public static void setMaxInboundBridgeTextCharacters(int value) {
        setAndSave(MAX_INBOUND_BRIDGE_TEXT_CHARACTERS, value);
    }

    public static void setServerChatRoomId(String value) {
        setAndSave(SERVER_CHAT_ROOM_ID, value);
    }

    public static void setServerChatRoomName(String value) {
        setAndSave(SERVER_CHAT_ROOM_NAME, value);
    }

    public static void setServerChatUseRawText(boolean value) {
        setAndSave(SERVER_CHAT_USE_RAW_TEXT, value);
    }

    public static void setServerChatSystemBroadcastPrefix(String value) {
        setAndSave(SERVER_CHAT_SYSTEM_BROADCAST_PREFIX, value);
    }

    public static void setEnableServerChatMaidPresentation(boolean value) {
        setAndSave(ENABLE_SERVER_CHAT_MAID_PRESENTATION, value);
    }

    public static void setMaxServerChatTextCharacters(int value) {
        setAndSave(MAX_SERVER_CHAT_TEXT_CHARACTERS, value);
    }

    public static void setMaxPendingServerChatMessages(int value) {
        setAndSave(MAX_PENDING_SERVER_CHAT_MESSAGES, value);
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
        var externalTurns = MAID_CHAT_MODE_EXTERNAL_AGENT.equals(normalizedMode);
        if (Objects.equals(MAID_AGENT_TURN_MODE.get(), normalizedMode)
                && Objects.equals(ENABLE_EXTERNAL_MAID_AGENT_TURNS.get(), externalTurns)) {
            refreshFromSpec();
            notifyRuntimeConfigChanged();
            return;
        }
        MAID_AGENT_TURN_MODE.set(normalizedMode);
        ENABLE_EXTERNAL_MAID_AGENT_TURNS.set(externalTurns);
        MAID_AGENT_TURN_MODE.save();
        refreshFromSpec();
        notifyRuntimeConfigChanged();
    }

    public static void setBridgeServerEnabled(boolean value) {
        setAndSave(BRIDGE_SERVER_ENABLED, value);
    }

    public static void setBridgeServerUrl(String value) {
        var endpoint = parseBridgeServerUrl(value, currentBridgeServerEndpoint());
        BRIDGE_SERVER_URL.set(endpoint.url());
        BRIDGE_SERVER_URL.save();
        refreshFromSpec();
        notifyRuntimeConfigChanged();
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
        enableServerChatBridge = ENABLE_SERVER_CHAT_BRIDGE.get();
        enableExternalServerChatMessages = ENABLE_EXTERNAL_SERVER_CHAT_MESSAGES.get();
        enableMaidMessageBridge = ENABLE_MAID_MESSAGE_BRIDGE.get();
        enableMultiplayerMaidChat = ENABLE_MULTIPLAYER_MAID_CHAT.get();
        maidExternalTurnTtlMs = MAID_EXTERNAL_TURN_TTL_MS.get();
        maxPendingMaidAgentTurns = MAX_PENDING_MAID_AGENT_TURNS.get();
        maidInjectionPolicy = MAID_INJECTION_POLICY.get();
        enableMaidApiExposure = ENABLE_MAID_API_EXPOSURE.get();
        enableMaidApiActions = ENABLE_MAID_API_ACTIONS.get();
        enableExternalAgentEmoji = ENABLE_EXTERNAL_AGENT_EMOJI.get();
        externalEmojiCacheTtlMs = EXTERNAL_EMOJI_CACHE_TTL_MS.get();
        maxExternalEmojiCacheEntries = MAX_EXTERNAL_EMOJI_CACHE_ENTRIES.get();
        captureRawLlmRequestBodies = CAPTURE_RAW_LLM_REQUEST_BODIES.get();
        maxRawLlmRequestCharacters = MAX_RAW_LLM_REQUEST_CHARACTERS.get();
        maxInboundBridgeTextCharacters = MAX_INBOUND_BRIDGE_TEXT_CHARACTERS.get();
        serverChatRoomId = SERVER_CHAT_ROOM_ID.get();
        serverChatRoomName = SERVER_CHAT_ROOM_NAME.get();
        serverChatUseRawText = SERVER_CHAT_USE_RAW_TEXT.get();
        serverChatSystemBroadcastPrefix = normalizeServerChatSystemBroadcastPrefix(SERVER_CHAT_SYSTEM_BROADCAST_PREFIX.get());
        enableServerChatMaidPresentation = ENABLE_SERVER_CHAT_MAID_PRESENTATION.get();
        maxServerChatTextCharacters = MAX_SERVER_CHAT_TEXT_CHARACTERS.get();
        maxPendingServerChatMessages = MAX_PENDING_SERVER_CHAT_MESSAGES.get();
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
        bridgeConnectionLostTimeoutSeconds = BRIDGE_CONNECTION_LOST_TIMEOUT_SECONDS.get();
        maxOutboundFrames = MAX_OUTBOUND_FRAMES.get();
        maxBufferedEvents = MAX_BUFFERED_EVENTS.get();
        logCapturedEvents = LOG_CAPTURED_EVENTS.get();
    }

    private static <T> void setAndSave(ModConfigSpec.ConfigValue<T> configValue, T value) {
        if (Objects.equals(configValue.get(), value)) {
            refreshFromSpec();
            notifyRuntimeConfigChanged();
            return;
        }
        configValue.set(value);
        configValue.save();
        refreshFromSpec();
        notifyRuntimeConfigChanged();
    }

    private static void notifyRuntimeConfigChanged() {
        runtimeConfigChangedHandler.run();
    }

    private static String normalizeServerChatSystemBroadcastPrefix(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_SERVER_CHAT_SYSTEM_BROADCAST_PREFIX;
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
