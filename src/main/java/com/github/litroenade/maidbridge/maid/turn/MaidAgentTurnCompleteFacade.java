package com.github.litroenade.maidbridge.maid.turn;

import com.github.litroenade.maidbridge.trace.AiChainEventSink;
import com.github.litroenade.maidbridge.maid.api.MaidActionExecutor;
import com.github.litroenade.maidbridge.maid.api.MaidApiReflection;
import com.github.litroenade.maidbridge.maid.api.MaidEntityLookup;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.github.litroenade.maidbridge.protocol.frame.MaidAgentTurnComplete;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * 应用外部 agent 返回的 maid.agent.turn.complete。
 * <p>流程：校验 pending turn，派发 TTS/气泡，只追加 assistant 历史，再发出 maid.message.out。
 * user 句的历史与归因由 emit 入口写入，本类不再重复处理。
 * actions 只在命中 pending turn 时按本轮授权回写状态，不等于开放全局 maid.api.call.* 写接口。
 */
public final class MaidAgentTurnCompleteFacade {
    private static final String TOUHOU_LITTLE_MAID_MOD_ID = "touhou_little_maid";
    private static final String TTS_SITE = "com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSSite";
    private static final String HISTORY_APPEND = "append";
    private static final String HISTORY_NONE = "none";
    private static final String HISTORY_SKIP = "skip";

    private MaidAgentTurnCompleteFacade() {
    }

    public static Map<String, Object> applyReply(MinecraftServer server, MaidAgentTurnComplete result) {
        var pendingTurn = MaidExternalTurnGuard.completeExternalTurn(result.maidUuid(), result.turnId());
        if (pendingTurn == null) {
            throw new IllegalArgumentException("没有待处理的外部女仆轮次 turn_id=" + result.turnId());
        }
        try {
            var actionsError = actionsValidationError(result);
            if (!actionsError.isBlank()) {
                throw new IllegalArgumentException(actionsError);
            }
            ensureMaidModLoaded();
            var historyPolicy = normalizedHistoryPolicy(result.historyPolicy());
            var maid = findMaid(server, result, pendingTurn);
            if (maid == null) {
                throw new IllegalArgumentException("未找到女仆");
            }
            if (!maid.isAlive()) {
                throw new IllegalArgumentException("女仆不处于存活状态");
            }

            var ttsDispatch = dispatchTtsIfRequested(maid, result.chatText(), result.ttsText());
            var actionResults = MaidActionExecutor.applyAll(maid, result.actions());
            var ownerDelivered = ttsDispatch.dispatched() ? ownerOnline(maid) : deliverChatText(maid, result.chatText());
            var historyAppended = appendHistoryIfRequested(maid, result.chatText(), historyPolicy);

            emitMaidMessageOut(result, pendingTurn, maid, actionResults, ttsDispatch);
            return responsePayload(result, pendingTurn, maid, ownerDelivered, historyAppended, actionResults, ttsDispatch, System::currentTimeMillis);
        } catch (RuntimeException exception) {
            throw turnFailure(result, exception);
        }
    }

    public static String actionsValidationError(MaidAgentTurnComplete result) {
        if (result.actions().isEmpty()) {
            return "";
        }
        try {
            MaidActionExecutor.validateAll(result.actions());
            return "";
        } catch (RuntimeException exception) {
            return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
    }

    private static IllegalArgumentException turnFailure(MaidAgentTurnComplete result, RuntimeException exception) {
        var detail = exception.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = exception.getClass().getSimpleName();
        }
        if (detail.contains("turn_id=" + result.turnId())) {
            return exception instanceof IllegalArgumentException illegalArgumentException
                    ? illegalArgumentException
                    : new IllegalArgumentException(detail, exception);
        }
        return new IllegalArgumentException(BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE + " 处理失败 turn_id=" + result.turnId() + "：" + detail, exception);
    }

    static Map<String, Object> responsePayload(
            MaidAgentTurnComplete result,
            MaidExternalTurnGuard.ActiveTurn pendingTurn,
            Entity maid,
            boolean ownerDelivered,
            boolean historyAppended,
            List<Map<String, Object>> actionResults,
            TtsDispatch ttsDispatch,
            LongSupplier clock
    ) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("routed", BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE);
        putMaidTurnIdentity(payload, result, maid);
        payload.put("request_id", pendingTurn.requestId());
        payload.put("accepted_at_ms", clock.getAsLong());
        payload.put("chat_text_delivered", true);
        payload.put("owner_message_delivered", ownerDelivered);
        payload.put("history_appended", historyAppended);
        payload.put("actions_applied", actionResults.size());
        payload.put("actions", actionResults);
        putClientMetadata(payload, pendingTurn.clientMetadata());
        putTtsDispatch(payload, ttsDispatch);
        return payload;
    }

    private static void emitMaidMessageOut(MaidAgentTurnComplete result, MaidExternalTurnGuard.ActiveTurn completedTurn, Entity maid, List<Map<String, Object>> actionResults, TtsDispatch ttsDispatch) {
        var payload = new LinkedHashMap<String, Object>();
        putMaidTurnIdentity(payload, result, maid);
        payload.put("chat_text", result.chatText());
        payload.put("message_kind", "external_agent_final");
        payload.put("actions_applied", actionResults.size());
        payload.put("actions", actionResults);
        putClientMetadata(payload, completedTurn.clientMetadata());
        putTtsDispatch(payload, ttsDispatch);
        AiChainEventSink.emit(BridgeProtocol.TYPE_MAID_MESSAGE_OUT, payload);
    }

    private static void putMaidTurnIdentity(Map<String, Object> payload, MaidAgentTurnComplete result, Entity maid) {
        var maidPayload = new LinkedHashMap<String, Object>();
        maidPayload.put("uuid", maid.getUUID().toString());
        maidPayload.put("name", MaidEntityLookup.entityName(maid));
        payload.put("maid", maidPayload);
        payload.put("turn_id", result.turnId());
    }

    private static void putClientMetadata(Map<String, Object> payload, Map<String, Object> clientMetadata) {
        if (clientMetadata == null || clientMetadata.isEmpty()) {
            return;
        }
        payload.put("client", new LinkedHashMap<>(clientMetadata));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static void ensureMaidModLoaded() {
        if (!ModList.get().isLoaded(TOUHOU_LITTLE_MAID_MOD_ID)) {
            throw new IllegalArgumentException("TouhouLittleMaid 未加载");
        }
    }

    private static Entity findMaid(MinecraftServer server, MaidAgentTurnComplete result, MaidExternalTurnGuard.ActiveTurn pendingTurn) {
        var maidUuid = firstNonBlank(result.maidUuid(), pendingTurn.maidUuid());
        if (maidUuid.isBlank()) {
            return null;
        }
        return MaidEntityLookup.findByUuid(server, UUID.fromString(maidUuid));
    }

    private static boolean deliverChatText(Entity maid, String chatText) {
        if (tryAddLlmChatText(maid, chatText)) {
            return ownerOnline(maid);
        }
        var owner = invoke(maid, "getOwner");
        if (owner instanceof ServerPlayer player) {
            Component name = maid.getName();
            player.sendSystemMessage(Component.literal("<").append(name).append(">").append(CommonComponents.SPACE).append(chatText)
                    .withStyle(ChatFormatting.GRAY));
            return true;
        }
        throw new IllegalArgumentException("投递女仆轮次结果 chat_text 失败");
    }

    private static boolean tryAddLlmChatText(Entity maid, String chatText) {
        try {
            var bubbleManager = invoke(maid, "getChatBubbleManager");
            Method method = bubbleManager.getClass().getMethod("addLLMChatText", String.class, long.class);
            method.invoke(bubbleManager, chatText, -1L);
            return true;
        } catch (IllegalAccessException | NoSuchMethodException exception) {
            return false;
        } catch (InvocationTargetException exception) {
            throw rethrowInvocation("ChatBubbleManager.addLLMChatText", exception);
        }
    }

    private static TtsDispatch dispatchTtsIfRequested(Entity maid, String chatText, String ttsText) {
        if (ttsText == null || ttsText.isBlank()) {
            return new TtsDispatch(false, false, "", "");
        }
        if (!AIConfig.TTS_ENABLED.get()) {
            return new TtsDispatch(true, false, "", "TouhouLittleMaid TTS 未启用");
        }
        var chatManager = invoke(maid, "getAiChatManager");
        var site = invoke(chatManager, "getTTSSite");
        if (site == null) {
            return new TtsDispatch(true, false, "", "未配置 TouhouLittleMaid TTSSite");
        }
        var siteId = stringValue(invoke(site, "id"));
        var enabled = invoke(site, "enabled");
        if (!(enabled instanceof Boolean siteEnabled)) {
            throw new IllegalArgumentException("TouhouLittleMaid TTSSite.enabled 未为站点返回布尔值：" + siteId);
        }
        if (!siteEnabled) {
            return new TtsDispatch(true, false, siteId, "TouhouLittleMaid TTSSite 已禁用");
        }
        try {
            Class<?> siteType = Class.forName(TTS_SITE);
            if (!siteType.isInstance(site)) {
                throw new IllegalArgumentException("配置的 TouhouLittleMaid TTS 站点不是 TTSSite：" + siteId);
            }
            MaidApiReflection.invokeExact(
                    chatManager,
                    "tts",
                    new Class<?>[]{siteType, String.class, String.class, long.class},
                    site,
                    chatText,
                    ttsText,
                    -1L
            );
            return new TtsDispatch(true, true, siteId, "");
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException("调用 MaidAIChatManager.tts 失败", exception);
        }
    }

    private static void putTtsDispatch(Map<String, Object> payload, TtsDispatch ttsDispatch) {
        payload.put("tts_requested", ttsDispatch.requested());
        payload.put("tts_dispatched", ttsDispatch.dispatched());
        payload.put("tts_site_id", ttsDispatch.siteId());
        if (!ttsDispatch.error().isBlank()) {
            payload.put("tts_error", ttsDispatch.error());
        }
    }

    private static boolean appendHistoryIfRequested(Entity maid, String chatText, String historyPolicy) {
        if (HISTORY_NONE.equals(historyPolicy) || HISTORY_SKIP.equals(historyPolicy)) {
            return false;
        }
        // user 句已由 mixin / emitInjectedTurn 写入 history，这里只追加 assistant 回复。
        var chatManager = invoke(maid, "getAiChatManager");
        try {
            Method assistantMethod = chatManager.getClass().getMethod("addAssistantHistory", String.class);
            assistantMethod.invoke(chatManager, chatText);
            return true;
        } catch (IllegalAccessException | NoSuchMethodException exception) {
            throw new IllegalArgumentException("追加 MaidAIChatManager history 失败", exception);
        } catch (InvocationTargetException exception) {
            throw rethrowInvocation("MaidAIChatManager history append", exception);
        }
    }

    private static String normalizedHistoryPolicy(String historyPolicy) {
        var normalized = historyPolicy == null ? "" : historyPolicy.trim();
        if (normalized.isBlank() || HISTORY_APPEND.equals(normalized) || HISTORY_NONE.equals(normalized) || HISTORY_SKIP.equals(normalized)) {
            return normalized.isBlank() ? HISTORY_APPEND : normalized;
        }
        throw new IllegalArgumentException("不支持的 " + BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE + " history_policy：" + normalized);
    }

    private static boolean ownerOnline(Entity maid) {
        return invoke(maid, "getOwner") instanceof ServerPlayer;
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (IllegalAccessException | NoSuchMethodException exception) {
            throw new IllegalArgumentException("调用失败：" + methodName, exception);
        } catch (InvocationTargetException exception) {
            throw rethrowInvocation(methodName, exception);
        }
    }

    private static RuntimeException rethrowInvocation(String methodName, InvocationTargetException exception) {
        var cause = exception.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalArgumentException("调用失败：" + methodName, cause);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private record TtsDispatch(boolean requested, boolean dispatched, String siteId, String error) {
    }
}
