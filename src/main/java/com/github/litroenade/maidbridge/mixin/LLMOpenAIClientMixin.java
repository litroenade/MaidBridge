package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.trace.AiChainEventSink;
import com.github.litroenade.maidbridge.trace.LlmRequestCapturePayload;
import com.github.litroenade.maidbridge.trace.ReflectiveAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 只观察 OpenAI 兼容客户端的出站请求，不修改请求内容。
 */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.LLMOpenAIClient", remap = false)
public abstract class LLMOpenAIClientMixin {
    @Inject(method = "chat", at = @At("HEAD"))
    private void maidbridge$captureSelectedClient(@Coerce Object callback, CallbackInfo ci) {
        if (!Config.enableAiChainCapture) {
            return;
        }
        Object site = ReflectiveAccess.field(this, "site");
        Object maid = ReflectiveAccess.invoke(callback, "getMaid");
        Object manager = ReflectiveAccess.invoke(maid, "getAiChatManager");
        Object tools = ReflectiveAccess.staticInvoke(
                "com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ToolRegister",
                "getAllTools"
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("llm_site_id", ReflectiveAccess.invoke(site, "id"));
        payload.put("llm_model", ReflectiveAccess.invoke(manager, "getLLMModel"));
        Map<String, Object> maidPayload = new LinkedHashMap<>();
        maidPayload.put("entity_id", ReflectiveAccess.invoke(maid, "getId"));
        maidPayload.put("uuid", ReflectiveAccess.invoke(maid, "getUUID"));
        maidPayload.put("model_id", ReflectiveAccess.invoke(maid, "getModelId"));
        payload.put("maid", maidPayload);
        payload.put("available_tool_ids", tools instanceof Map<?, ?> map ? String.join(",", map.keySet().stream().map(String::valueOf).toList()) : "");
        payload.put("tool_count", ReflectiveAccess.size(tools));
        AiChainEventSink.emit("maid.ai.llm.client.selected", payload);
    }

    @ModifyArg(
            method = "chat",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/net/http/HttpRequest$BodyPublishers;ofString(Ljava/lang/String;)Ljava/net/http/HttpRequest$BodyPublisher;"
            ),
            index = 0,
            require = 0
    )
    private String maidbridge$captureRawRequest(String requestBody) {
        if (!Config.enableAiChainCapture) {
            return requestBody;
        }
        Map<String, Object> payload = LlmRequestCapturePayload.fromRequestBody(
                requestBody,
                Config.captureRawLlmRequestBodies,
                Config.maxRawLlmRequestCharacters
        );
        AiChainEventSink.emit("maid.ai.llm.request", payload);
        return requestBody;
    }
}
