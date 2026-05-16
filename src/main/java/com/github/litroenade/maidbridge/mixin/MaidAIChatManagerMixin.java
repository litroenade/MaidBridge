package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidExternalAgentDisplayState;
import com.github.litroenade.maidbridge.trace.AiChainEventSink;
import com.github.litroenade.maidbridge.trace.ReflectiveAccess;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接入女仆原生 AIChat 管理器。
 * <p>诊断采集保留 TLM 原生字段；外部接管由 MaidAgentTurnRequest 负责写入历史和发出轮次。</p>
 */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager", remap = false)
public abstract class MaidAIChatManagerMixin {
    @Inject(method = "chat", at = @At("HEAD"))
    private void maidbridge$captureIncomingChat(String message, @Coerce Object clientInfo, @Coerce Object sender, CallbackInfo ci) {
        Object manager = maidbridge$self();
        if (maidbridge$isExternalAgentMaid(manager)) {
            return;
        }
        if (Config.enableAiChainCapture) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("message", message);
            payload.put("client_language", ReflectiveAccess.invoke(clientInfo, "language"));
            payload.put("client_name", ReflectiveAccess.invoke(clientInfo, "name"));
            payload.put("client_description", ReflectiveAccess.joinCollection(ReflectiveAccess.invoke(clientInfo, "description"), "\n"));
            if (sender != null) {
                payload.put("sender", maidbridge$senderPayload(sender));
            }
            maidbridge$putMaidState(payload, manager);
            AiChainEventSink.emit("maid.ai.request.received", payload);
        }
    }

    @Inject(
            method = "normalChat",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/service/llm/LLMClient;chat(Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/LLMCallback;)V"
            )
    )
    private void maidbridge$capturePromptBeforeSend(String message, List<?> messages, @Coerce Object chatClient, CallbackInfo ci) {
        if (maidbridge$isExternalAgentMaid(maidbridge$self()) || !Config.enableAiChainCapture) {
            return;
        }
        Object manager = maidbridge$self();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("raw_message", message);
        payload.put("message_count", messages.size());
        payload.put("messages", maidbridge$serializeMessages(messages));
        payload.put("llm_client_class", chatClient.getClass().getName());
        maidbridge$putMaidState(payload, manager);
        AiChainEventSink.emit("maid.ai.prompt.built", payload);
    }

    @Inject(method = "tts", at = @At("HEAD"))
    private void maidbridge$captureTtsRequest(@Coerce Object site, String chatText, String ttsText, long waitingChatBubbleId, CallbackInfo ci) {
        if (!Config.enableAiChainCapture) {
            return;
        }
        Object manager = maidbridge$self();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tts_site_id", ReflectiveAccess.invoke(site, "id"));
        payload.put("tts_model", ReflectiveAccess.invoke(manager, "getTTSModel"));
        payload.put("tts_language", ReflectiveAccess.invoke(manager, "getTTSLanguage"));
        payload.put("chat_text", chatText);
        payload.put("tts_text", ttsText);
        payload.put("waiting_chat_bubble_id", waitingChatBubbleId);
        maidbridge$putMaidState(payload, manager);
        AiChainEventSink.emit("maid.ai.tts.request", payload);
    }

    @Unique
    private Object maidbridge$self() {
        return this;
    }

    @Unique
    private static void maidbridge$putMaidState(Map<String, Object> payload, Object manager) {
        // 诊断事件保留 TLM 原生字段；正式外部 agent 轮次使用 MaidStateExporter 的清洗视图。
        Object maid = ReflectiveAccess.invoke(manager, "getMaid");
        Object llmSite = ReflectiveAccess.invoke(manager, "getLLMSite");
        Object ttsSite = ReflectiveAccess.invoke(manager, "getTTSSite");
        Map<String, Object> maidPayload = new LinkedHashMap<>();
        maidPayload.put("entity_id", ReflectiveAccess.invoke(maid, "getId"));
        maidPayload.put("uuid", ReflectiveAccess.invoke(maid, "getUUID"));
        maidPayload.put("name", ReflectiveAccess.componentText(ReflectiveAccess.invoke(maid, "getName")));
        maidPayload.put("model_id", ReflectiveAccess.invoke(maid, "getModelId"));
        maidPayload.put("sound_pack_id", ReflectiveAccess.invoke(maid, "getSoundPackId"));
        payload.put("maid", maidPayload);
        payload.put("llm_site_id", ReflectiveAccess.invoke(llmSite, "id"));
        payload.put("llm_model", ReflectiveAccess.invoke(manager, "getLLMModel"));
        payload.put("tts_site_id", ReflectiveAccess.invoke(ttsSite, "id"));
        payload.put("tts_model", ReflectiveAccess.invoke(manager, "getTTSModel"));
        payload.put("tts_language", ReflectiveAccess.invoke(manager, "getTTSLanguage"));
        payload.put("chat_language", ReflectiveAccess.invoke(manager, "getChatLanguage"));
        payload.put("custom_setting", ReflectiveAccess.field(manager, "customSetting"));
    }

    @Unique
    private static Map<String, Object> maidbridge$senderPayload(Object sender) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uuid", ReflectiveAccess.invoke(sender, "getUUID"));
        payload.put("name", ReflectiveAccess.componentText(ReflectiveAccess.invoke(sender, "getName")));
        return payload;
    }

    @Unique
    private static String maidbridge$serializeMessages(List<?> messages) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            Object message = messages.get(i);
            builder.append(i)
                    .append(":")
                    .append(ReflectiveAccess.invoke(message, "role"))
                    .append(":")
                    .append(ReflectiveAccess.invoke(message, "message"));
            Object toolCalls = ReflectiveAccess.invoke(message, "toolCalls");
            if (ReflectiveAccess.size(toolCalls) > 0) {
                builder.append(" tool_calls=").append(toolCalls);
            }
            String toolCallId = String.valueOf(ReflectiveAccess.invoke(message, "toolCallId"));
            if (!toolCallId.isBlank()) {
                builder.append(" tool_call_id=").append(toolCallId);
            }
            if (i + 1 < messages.size()) {
                builder.append("\n---\n");
            }
        }
        return builder.toString();
    }

    @Unique
    private static boolean maidbridge$isExternalAgentMaid(Object manager) {
        Object maid = ReflectiveAccess.invoke(manager, "getMaid");
        return Config.isExternalMaidAgentMode()
                && maid instanceof EntityMaid entityMaid
                && MaidExternalAgentDisplayState.hasAgent(entityMaid.getUUID());
    }
}
