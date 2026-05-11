package com.github.litroenade.maidbridge.maid.api;

import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public final class MaidToolApi {
    private static final String LLM_CALLBACK = "com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback";
    private static final String LLM_CLIENT = "com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient";

    private MaidToolApi() {
    }

    public static Map<String, Object> queryToolSchemas(Object maid, String toolId) {
        List<Map<String, Object>> schemas = new ArrayList<>();
        if (toolId != null && !toolId.isBlank()) {
            schemas.add(schemaForTool(toolId, toolById(toolId), maid));
        } else {
            MaidRegistryIntrospection.toolMap().forEach((id, tool) -> schemas.add(schemaForTool(MaidApiReflection.clean(id), tool, maid)));
        }
        return Map.of(
                "request_type", BridgeProtocol.TYPE_MAID_API_QUERY_MAID_TOOL_SCHEMA,
                "schemas", schemas,
                "count", schemas.size()
        );
    }

    public static Map<String, Object> callTool(Object maid, Map<String, Object> payload) {
        String toolId = MaidApiReflection.stringValue(payload, "tool_id", "name");
        if (toolId.isBlank()) {
            throw new IllegalArgumentException("tool_id 必须是非空字符串");
        }
        Object tool = toolById(toolId);
        Object decoded = decodeArguments(tool, argumentsPayload(payload), toolId);
        Object callback = createCallback(maid);
        Object client = createUnsupportedClient();
        String toolCallId = MaidApiReflection.stringValue(payload, "tool_call_id", "call_id");
        if (toolCallId.isBlank()) {
            toolCallId = "maidbridge:" + toolId;
        }

        Object future = MaidApiReflection.invoke(tool, "onCallAsync", toolCallId, decoded, callback, client);
        if (!(future instanceof CompletableFuture<?> completable)) {
            throw new IllegalStateException("ITool.onCallAsync 未为工具返回 CompletableFuture：" + toolId);
        }
        if (!completable.isDone()) {
            completable.cancel(false);
            throw new IllegalArgumentException("女仆工具需要异步 LLM/客户端流程，无法由 " + BridgeProtocol.TYPE_MAID_API_CALL_MAID_TOOL + " 直接完成：" + toolId);
        }

        Object returnedCallback;
        try {
            returnedCallback = completable.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalArgumentException("女仆工具执行失败 '%s'：%s".formatted(toolId, cause.getMessage()), cause);
        }
        List<?> messages = callbackMessages(returnedCallback == null ? callback : returnedCallback);
        Object lastToolMessage = lastToolMessage(messages);
        if (lastToolMessage == null) {
            throw new IllegalStateException("女仆工具未产生工具结果：" + toolId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request_type", BridgeProtocol.TYPE_MAID_API_CALL_MAID_TOOL);
        result.put("tool_id", toolId);
        result.put("tool_call_id", MaidApiReflection.clean(MaidApiReflection.invoke(lastToolMessage, "toolCallId")));
        result.put("result", MaidApiReflection.clean(MaidApiReflection.invoke(lastToolMessage, "message")));
        result.put("message_count", messages.size());
        return result;
    }

    public static Map<String, Object> toolSchemaItem(String id, String summary, Object parameters, String className) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("summary", summary);
        item.put("parameters", parameters);
        item.put("result", toolResultSchema());
        item.put("class_name", className == null ? "" : className);
        return item;
    }

    private static Map<String, Object> toolResultSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("tool_call_id", "result"),
                "properties", Map.of(
                        "tool_call_id", Map.of("type", "string"),
                        "result", Map.of("type", "string"),
                        "message_count", Map.of("type", "integer")
                )
        );
    }

    private static Map<String, Object> schemaForTool(String id, Object tool, Object maid) {
        String actualId = MaidApiReflection.clean(MaidApiReflection.invoke(tool, "id"));
        if (actualId.isBlank()) {
            actualId = id;
        }
        String summary = MaidApiReflection.clean(MaidApiReflection.invoke(tool, "summary", maid));
        Object root = MaidRegistryIntrospection.objectParameterRoot();
        Object parameter = MaidApiReflection.invoke(tool, "parameters", root, maid);
        Object schema = MaidApiReflection.jsonCompatible(parameter);
        return toolSchemaItem(actualId, summary, schema, tool.getClass().getName());
    }

    private static Object toolById(String toolId) {
        Object tool = MaidRegistryIntrospection.toolById(toolId);
        if (tool == null) {
            throw new IllegalArgumentException("未找到女仆工具：" + toolId);
        }
        return tool;
    }

    private static JsonElement argumentsPayload(Map<String, Object> payload) {
        Object arguments = payload.get("arguments");
        if (arguments == null) {
            arguments = payload.get("parameters");
        }
        if (arguments == null) {
            arguments = payloadWithoutBridgeFields(payload);
        }
        return MaidApiReflection.GSON.toJsonTree(arguments);
    }

    private static Map<String, Object> payloadWithoutBridgeFields(Map<String, Object> payload) {
        var arguments = new LinkedHashMap<>(payload);
        arguments.remove("maid");
        arguments.remove("maid_uuid");
        arguments.remove("maid_entity_id");
        arguments.remove("server_id");
        arguments.remove("endpoint_id");
        arguments.remove("tool_id");
        arguments.remove("name");
        arguments.remove("tool_call_id");
        arguments.remove("call_id");
        return arguments;
    }

    private static Object decodeArguments(Object tool, JsonElement arguments, String toolId) {
        Object codec = MaidApiReflection.invoke(tool, "codec");
        Object dataResult = MaidApiReflection.invoke(codec, "parse", JsonOps.INSTANCE, arguments);
        List<String> errors = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Optional<Object> decoded = (Optional<Object>) MaidApiReflection.invoke(dataResult, "resultOrPartial", (Consumer<String>) errors::add);
        if (decoded.isEmpty()) {
            String detail = errors.isEmpty() ? "codec 未返回结果" : String.join("; ", errors);
            throw new IllegalArgumentException("解析女仆工具参数失败 '%s'：%s".formatted(toolId, detail));
        }
        return decoded.get();
    }

    private static Object createCallback(Object maid) {
        Object chatManager = MaidApiReflection.invoke(maid, "getAiChatManager");
        return MaidApiReflection.construct(LLM_CALLBACK, new Class<?>[]{chatManager.getClass(), List.class, boolean.class},
                chatManager, new ArrayList<>(), true);
    }

    private static Object createUnsupportedClient() {
        try {
            Class<?> clientType = Class.forName(LLM_CLIENT);
            return Proxy.newProxyInstance(
                    clientType.getClassLoader(),
                    new Class<?>[]{clientType},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("chat".equals(methodName)) {
                            throw new UnsupportedOperationException("MaidBridge 外部工具调用没有用于子 agent 聊天的 LLM 客户端");
                        }
                        if ("toString".equals(methodName) && method.getParameterCount() == 0) {
                            return "MaidBridgeUnsupportedLlmClient";
                        }
                        if ("hashCode".equals(methodName) && method.getParameterCount() == 0) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(methodName) && args != null && args.length == 1) {
                            return proxy == args[0];
                        }
                        return null;
                    }
            );
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("创建 TouhouLittleMaid LLMClient 包装器失败", exception);
        }
    }

    private static List<?> callbackMessages(Object callback) {
        Object messages = MaidApiReflection.invoke(callback, "getMessages");
        if (!(messages instanceof List<?> list)) {
            throw new IllegalStateException("LLMCallback.getMessages 未返回 List");
        }
        return list;
    }

    private static Object lastToolMessage(List<?> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Object message = messages.get(index);
            if (!MaidApiReflection.clean(MaidApiReflection.invoke(message, "toolCallId")).isBlank()) {
                return message;
            }
        }
        return null;
    }
}
