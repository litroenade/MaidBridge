package com.github.litroenade.maidbridge.maid.turn;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.maid.api.MaidApiReflection;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatAttributionContext;
import com.github.litroenade.maidbridge.maid.state.MaidCapabilityExporter;
import com.github.litroenade.maidbridge.maid.state.MaidStateExporter;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.github.litroenade.maidbridge.protocol.frame.MaidClientInfo;
import com.github.litroenade.maidbridge.trace.AiChainEventSink;
import com.github.litroenade.maidbridge.trace.ReflectiveAccess;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 从 TouhouLittleMaid 原生聊天入口生成外部 agent 轮次。
 * <p>只有 guard 成功接管后才写入 user history，避免 BUSY/drop 污染原生历史。</p>
 */
public final class MaidAgentTurnRequest {
    public static final String EVENT_TYPE = BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST;
    public static final String REJECTED_EVENT_TYPE = "maid.agent.turn.rejected";
    public static final String STATE_EXPORT_FAILED_EVENT_TYPE = "maid.agent.state.unavailable";
    public static final String CAPABILITY_EXPORT_FAILED_EVENT_TYPE = "maid.agent.capabilities.unavailable";

    private MaidAgentTurnRequest() {
    }

    public enum EmitStatus {
        DISABLED,
        EMITTED,
        BUSY
    }

    public record EmitResult(EmitStatus status, String maidUuid, String turnId, String reason) {
    }

    private record TurnIdentity(String maidUuid, String turnId, String requestId) {
    }

    public static EmitResult emit(Object chatManager, String message, Object clientInfo, Object sender) {
        if (!Config.isExternalMaidAgentMode()) {
            return new EmitResult(EmitStatus.DISABLED, "", "", "external_maid_agent_turns_disabled");
        }
        Object maid = ReflectiveAccess.invoke(chatManager, "getMaid");
        var payload = payload(maid, message, clientInfo, sender);
        var turnIdentity = turnIdentity(payload);
        if (MaidExternalTurnGuard.beginExternalTurn(
                turnIdentity.maidUuid(),
                turnIdentity.turnId(),
                turnIdentity.requestId(),
                safeString(message),
                mapPayload(payload.get("speaker"))
        )) {
            writeUserHistory(chatManager, message, sender, mapPayload(payload.get("speaker")));
            AiChainEventSink.emit(EVENT_TYPE, payload);
            return new EmitResult(EmitStatus.EMITTED, turnIdentity.maidUuid(), turnIdentity.turnId(), "");
        }
        var reason = "maid_external_turn_pending";
        emitRejected(payload, reason);
        return new EmitResult(EmitStatus.BUSY, turnIdentity.maidUuid(), turnIdentity.turnId(), reason);
    }

    public static void emitInjectedTurn(Object maid, String message, MaidClientInfo clientInfo, String turnId, String requestId) {
        if (!Config.isExternalMaidAgentMode()) {
            throw new IllegalArgumentException("外部女仆 agent 轮次未启用");
        }
        var payload = payloadFromMaid(maid, message, clientInfo, turnId, requestId);
        var turnIdentity = turnIdentity(payload);
        if (MaidExternalTurnGuard.beginExternalTurn(
                turnIdentity.maidUuid(),
                turnIdentity.turnId(),
                turnIdentity.requestId(),
                safeString(message),
                mapPayload(payload.get("speaker"))
        )) {
            writeUserHistory(MaidApiReflection.invoke(maid, "getAiChatManager"), message, null, mapPayload(payload.get("speaker")));
            AiChainEventSink.emit(EVENT_TYPE, payload);
            return;
        }
        throw new IllegalArgumentException("女仆已有待处理的外部 agent 轮次");
    }

    private static void emitRejected(Map<String, Object> originalPayload, String reason) {
        var rejected = new LinkedHashMap<String, Object>();
        rejected.put("reason", reason);
        rejected.put("turn_id", originalPayload.get("turn_id"));
        rejected.put("request_id", originalPayload.get("request_id"));
        rejected.put("maid", originalPayload.get("maid"));
        rejected.put("speaker", originalPayload.get("speaker"));
        rejected.put("message", originalPayload.get("message"));
        AiChainEventSink.emit(REJECTED_EVENT_TYPE, rejected);
    }

    private static Map<String, Object> payload(Object maid, String message, Object clientInfo, Object sender) {
        var turnId = "maid-agent-turn-" + UUID.randomUUID();
        var requestId = "maid-agent-request-" + UUID.randomUUID();
        return basePayload(
                maid,
                message,
                speakerPayload(clientInfo, sender),
                turnId,
                requestId
        );
    }

    private static Map<String, Object> payloadFromMaid(Object maid, String message, MaidClientInfo clientInfo, String turnId, String requestId) {
        var normalizedTurnId = firstNonBlank(turnId, requestId, "maid-agent-turn-" + UUID.randomUUID());
        var normalizedRequestId = firstNonBlank(requestId, normalizedTurnId, "maid-agent-request-" + UUID.randomUUID());
        return basePayload(
                maid,
                message,
                speakerPayload(clientInfo),
                normalizedTurnId,
                normalizedRequestId
        );
    }

    private static Map<String, Object> basePayload(
            Object maid,
            String message,
            Map<String, Object> speaker,
            String turnId,
            String requestId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("turn_id", turnId);
        payload.put("request_id", requestId);
        payload.put("maid", MaidStateExporter.compactIdentity(maid));
        payload.put("speaker", speaker);
        payload.put("message", messagePayload(message));
        putTurnState(payload, maid);
        putAgentAffordances(payload, maid);
        return payload;
    }

    private static Map<String, Object> messagePayload(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", safeString(message));
        return payload;
    }

    private static void putTurnState(Map<String, Object> payload, Object maid) {
        try {
            payload.put("state", MaidStateExporter.compactTurnState(maid));
            payload.put("action_context", MaidStateExporter.actionContext(maid));
        } catch (RuntimeException exception) {
            // 状态上下文只辅助外部 agent 判断；导出失败不应打断已经接管的女仆回合。
            payload.put("state", Map.of());
            payload.put("action_context", Map.of());
            var diagnostic = new LinkedHashMap<String, Object>();
            diagnostic.put("maid", payload.get("maid"));
            diagnostic.put("error", firstNonBlank(exception.getMessage(), exception.getClass().getName()));
            AiChainEventSink.emit(STATE_EXPORT_FAILED_EVENT_TYPE, diagnostic);
            MaidBridge.LOGGER.warn("导出外部女仆 agent 状态失败，已使用空 state/action_context maidUuid={}", maidUuidFromPayload(payload), exception);
        }
    }

    private static void putAgentAffordances(Map<String, Object> payload, Object maid) {
        if (!(maid instanceof EntityMaid)) {
            // 非真实 EntityMaid 没有 TLM 工具上下文，直接给空能力。
            payload.put("actions", List.of());
            payload.put("tools", List.of());
            return;
        }
        try {
            var capabilities = MaidCapabilityExporter.compactTurnAffordances(maid);
            payload.put("actions", capabilityItems(capabilities, "action_schema"));
            payload.put("tools", capabilityItems(capabilities, "tools"));
        } catch (RuntimeException exception) {
            payload.put("actions", List.of());
            payload.put("tools", List.of());
            var diagnostic = new LinkedHashMap<String, Object>();
            diagnostic.put("maid", payload.get("maid"));
            diagnostic.put("error", firstNonBlank(exception.getMessage(), exception.getClass().getName()));
            AiChainEventSink.emit(CAPABILITY_EXPORT_FAILED_EVENT_TYPE, diagnostic);
            MaidBridge.LOGGER.warn("导出外部女仆 agent 能力失败，已使用空 actions/tools maidUuid={}", maidUuidFromPayload(payload), exception);
        }
    }

    private static String maidUuidFromPayload(Map<String, Object> payload) {
        if (payload.get("maid") instanceof Map<?, ?> maid) {
            return safeString(maid.get("uuid"));
        }
        return "";
    }

    private static List<?> capabilityItems(Map<String, Object> capabilities, String key) {
        Object section = capabilities.get(key);
        if (section instanceof Map<?, ?> sectionMap && sectionMap.get("items") instanceof List<?> items) {
            return List.copyOf(items);
        }
        return List.of();
    }

    private static Map<String, Object> clientPayload(Object clientInfo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("language", clean(ReflectiveAccess.invoke(clientInfo, "language")));
        payload.put("name", clean(ReflectiveAccess.invoke(clientInfo, "name")));
        Object description = ReflectiveAccess.invoke(clientInfo, "description");
        payload.put("description", unavailable(description) ? List.of() : description);
        return payload;
    }

    private static Map<String, Object> clientPayload(MaidClientInfo clientInfo) {
        var payload = clientInfo.toBridgePayload("en_us", "MaidBridge");
        putIfPresent(payload, "name", firstNonBlank(
                safeString(clientInfo.metadata().get("source_member_name")),
                safeString(clientInfo.metadata().get("nickname")),
                clientInfo.name()
        ));
        return payload;
    }

    private static Map<String, Object> speakerPayload(Object clientInfo, Object sender) {
        Map<String, Object> payload = clientPayload(clientInfo);
        putSenderFields(payload, sender);
        return payload;
    }

    private static Map<String, Object> speakerPayload(MaidClientInfo clientInfo) {
        return clientPayload(clientInfo);
    }

    private static Map<String, Object> mapPayload(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        map.forEach((key, item) -> payload.put(safeString(key), item));
        return payload;
    }

    private static void putSenderFields(Map<String, Object> payload, Object sender) {
        if (sender == null) {
            return;
        }
        if (sender instanceof ServerPlayer player) {
            putIfPresent(payload, "uuid", player.getUUID().toString());
            putIfPresent(payload, "name", player.getGameProfile().getName());
            return;
        }
        putIfPresent(payload, "uuid", clean(ReflectiveAccess.invoke(sender, "getUUID")));
        putIfPresent(payload, "name", clean(ReflectiveAccess.componentText(ReflectiveAccess.invoke(sender, "getName"))));
    }

    private static TurnIdentity turnIdentity(Map<String, Object> payload) {
        var maid = mapPayload(payload.get("maid"));
        return new TurnIdentity(
                safeString(maid.get("uuid")),
                safeString(payload.get("turn_id")),
                safeString(payload.get("request_id"))
        );
    }

    private static void writeUserHistory(Object chatManager, String message, Object sender, Map<String, Object> speaker) {
        try {
            if (sender instanceof ServerPlayer player) {
                Object maid = ReflectiveAccess.invoke(chatManager, "getMaid");
                if (maid instanceof EntityMaid entityMaid) {
                    MaidAIChatAttributionContext.runWith(
                            entityMaid,
                            player,
                            message,
                            () -> MaidApiReflection.invoke(chatManager, "addUserHistory", message)
                    );
                    return;
                }
            }
            UUID speakerUuid = speakerUuidFromPayload(speaker);
            String speakerName = speakerNameFromPayload(speaker);
            if (speakerUuid != null && !speakerName.isBlank()) {
                Object maid = ReflectiveAccess.invoke(chatManager, "getMaid");
                if (maid instanceof EntityMaid entityMaid) {
                    MaidAIChatAttributionContext.runWith(
                            entityMaid,
                            speakerUuid,
                            speakerName,
                            message,
                            () -> MaidApiReflection.invoke(chatManager, "addUserHistory", message)
                    );
                    return;
                }
            }
            MaidApiReflection.invoke(chatManager, "addUserHistory", message);
        } catch (RuntimeException exception) {
            MaidBridge.LOGGER.warn("外部接管写入 user history 失败 message={}", message, exception);
        }
    }

    private static UUID speakerUuidFromPayload(Map<String, Object> speaker) {
        String value = firstNonBlank(safeString(speaker.get("uuid")), safeString(speaker.get("source_member_id")));
        if (value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(("maidbridge:speaker:" + value).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String speakerNameFromPayload(Map<String, Object> speaker) {
        return firstNonBlank(
                safeString(speaker.get("name")),
                safeString(speaker.get("nickname")),
                safeString(speaker.get("source_member_name")),
                "MaidBridge"
        );
    }

    private static String clean(Object value) {
        if (unavailable(value)) {
            return "";
        }
        return safeString(value);
    }

    private static void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value.trim());
        }
    }

    private static boolean unavailable(Object value) {
        return value == null || isReflectError(value);
    }

    private static boolean isReflectError(Object value) {
        return value instanceof String text && text.startsWith("<reflect-error:");
    }

    private static String safeString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
