package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.trace.AiChainEventSink;
import com.github.litroenade.maidbridge.trace.ReflectiveAccess;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.Message;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.http.HttpRequest;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 采集 TouhouLittleMaid LLM 回调阶段的结果、工具调用和失败信息。
 */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback", remap = false)
public abstract class LLMCallbackMixin {
    @Inject(method = "onSuccess(Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/response/ResponseChat;)V", at = @At("HEAD"), cancellable = true)
    private void maidbridge$captureSuccess(ResponseChat responseChat, CallbackInfo ci) {
        Object maid = ReflectiveAccess.invoke(this, "getMaid");
        if (Config.isExternalMaidAgentMode()) {
            ci.cancel();
            return;
        }
        if ((!Config.enableAiChainCapture && !Config.enableMaidMessageBridge) || MaidBridgeMixinPlugin.isAiChainDiagnosticsDisabled()) {
            return;
        }
        Object chatText = ReflectiveAccess.invoke(responseChat, "getChatText");
        Object ttsText = ReflectiveAccess.invoke(responseChat, "getTtsText");
        if (Config.enableAiChainCapture) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chat_text", chatText);
            payload.put("tts_text", ttsText);
            payload.put("response", responseChat);
            AiChainEventSink.emit("maid.ai.output.final", payload);
        }

        if (Config.enableMaidMessageBridge && maidbridge$shouldEmitMaidMessageOut(chatText)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("maid", maidbridge$maidPayload(maid));
            payload.put("chat_text", chatText);
            payload.put("message_kind", "assistant_final");
            AiChainEventSink.emit(BridgeProtocol.TYPE_MAID_MESSAGE_OUT, payload);
        }
    }

    @Inject(method = "onFailure", at = @At("HEAD"))
    private void maidbridge$captureFailure(HttpRequest request, Throwable throwable, int errorCode, CallbackInfo ci) {
        if (!Config.enableAiChainCapture || MaidBridgeMixinPlugin.isAiChainDiagnosticsDisabled()) {
            return;
        }
        var payload = new LinkedHashMap<String, String>();
        payload.put("error_code", String.valueOf(errorCode));
        payload.put("error", throwable == null ? "" : String.valueOf(throwable.getMessage()));
        payload.put("request", String.valueOf(request));
        AiChainEventSink.emit("maid.ai.output.failure", payload);
    }

    @Inject(method = "onFunctionCall(Lcom/github/tartaricacid/touhoulittlemaid/ai/service/llm/openai/response/Message;Lcom/github/tartaricacid/touhoulittlemaid/ai/service/llm/LLMClient;)V", at = @At("HEAD"), cancellable = true)
    private void maidbridge$captureToolCalls(Message choice, LLMClient client, CallbackInfo ci) {
        if (Config.isExternalMaidAgentMode()) {
            ci.cancel();
            return;
        }
        if (!Config.enableAiChainCapture || MaidBridgeMixinPlugin.isAiChainDiagnosticsDisabled()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        Object toolCalls = ReflectiveAccess.invoke(choice, "getToolCalls");
        payload.put("content", ReflectiveAccess.invoke(choice, "getContent"));
        payload.put("tool_count", ReflectiveAccess.size(toolCalls));
        payload.put("tool_calls", maidbridge$toolCallsSummary(toolCalls));
        payload.put("llm_client_class", client.getClass().getName());
        AiChainEventSink.emit("maid.ai.tool_calls.proposed", payload);
    }

    @Inject(method = "onSingleCall", at = @At("HEAD"))
    private void maidbridge$captureDecodedToolCall(@Coerce Object toolCall, @Coerce Object callback, @Coerce Object client, CallbackInfoReturnable<Object> cir) {
        if (!Config.enableAiChainCapture || MaidBridgeMixinPlugin.isAiChainDiagnosticsDisabled()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        maidbridge$putToolCall(payload, toolCall);
        payload.put("llm_client_class", client.getClass().getName());
        Object maid = ReflectiveAccess.invoke(callback, "getMaid");
        payload.put("maid", maidbridge$maidPayload(maid));
        AiChainEventSink.emit("maid.ai.tool_call.decoded", payload);
    }

    @Inject(method = "addToolResult", at = @At("HEAD"))
    private void maidbridge$captureToolResult(String result, String toolId, CallbackInfoReturnable<Object> cir) {
        if (!Config.enableAiChainCapture || MaidBridgeMixinPlugin.isAiChainDiagnosticsDisabled()) {
            return;
        }
        var payload = new LinkedHashMap<String, String>();
        payload.put("tool_call_id", String.valueOf(toolId));
        payload.put("result", String.valueOf(result));
        AiChainEventSink.emit("maid.ai.tool_result.added", payload);
    }

    @Unique
    private static String maidbridge$toolCallsSummary(Object toolCalls) {
        if (!(toolCalls instanceof Collection<?> collection) || collection.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (Object toolCall : collection) {
            Map<String, Object> payload = new LinkedHashMap<>();
            maidbridge$putToolCall(payload, toolCall);
            builder.append(payload);
            index++;
            if (index < collection.size()) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    @Unique
    private static void maidbridge$putToolCall(Map<String, Object> payload, Object toolCall) {
        maidbridge$putReflected(payload, "tool_call_id", toolCall, "getId");
        maidbridge$putReflected(payload, "tool_call_type", toolCall, "getType");
        Object function = ReflectiveAccess.invoke(toolCall, "getFunction");
        if (function != null) {
            maidbridge$putReflected(payload, "tool_name", function, "getName");
            maidbridge$putReflected(payload, "arguments", function, "getArguments");
        }
    }

    @Unique
    private static Map<String, Object> maidbridge$maidPayload(Object maid) {
        Map<String, Object> payload = new LinkedHashMap<>();
        maidbridge$putReflected(payload, "uuid", maid, "getUUID");
        maidbridge$putReflected(payload, "entity_id", maid, "getId");
        payload.put("name", ReflectiveAccess.componentText(ReflectiveAccess.invoke(maid, "getName")));
        return payload;
    }

    @Unique
    private static void maidbridge$putReflected(Map<String, Object> payload, String key, Object target, String method) {
        payload.put(key, ReflectiveAccess.invoke(target, method));
    }

    @Unique
    private static boolean maidbridge$shouldEmitMaidMessageOut(Object chatText) {
        return !String.valueOf(chatText).isBlank();
    }

}
