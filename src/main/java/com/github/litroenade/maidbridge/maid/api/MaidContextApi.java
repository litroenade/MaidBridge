package com.github.litroenade.maidbridge.maid.api;

import com.github.litroenade.maidbridge.protocol.BridgeProtocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MaidContextApi {
    private MaidContextApi() {
    }

    public static Map<String, Object> queryContext(Object maid, String categoryId, String key) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("category 必须是非空字符串");
        }
        if (!MaidRegistryIntrospection.hasContextCategory(categoryId)) {
            throw new IllegalArgumentException("未知女仆上下文分类：" + categoryId);
        }

        var contextKeys = contextKeys(categoryId);
        if (contextKeys.isEmpty()) {
            throw new IllegalArgumentException("女仆上下文分类没有可用键：" + categoryId);
        }

        if (key != null && !key.isBlank()) {
            if (!contextKeys.contains(key)) {
                throw new IllegalArgumentException("未知女仆上下文键 '%s'，分类 '%s'".formatted(key, categoryId));
            }
            return contextPayload(categoryId, key, List.of(contextValue(key, maid)));
        }

        return contextPayload(categoryId, "", contextValues(categoryId, maid, contextKeys));
    }

    public static Map<String, Object> contextPayload(String categoryId, String key, List<Map<String, Object>> values) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("request_type", BridgeProtocol.TYPE_MAID_API_QUERY_MAID_CONTEXT);
        payload.put("category", categoryId);
        payload.put("key", key == null ? "" : key);
        payload.put("values", values == null ? List.of() : values);
        return payload;
    }

    private static List<Map<String, Object>> contextValues(String categoryId, Object maid, List<String> contextKeys) {
        Object rawLines = MaidRegistryIntrospection.context(categoryId, maid);
        if (!(rawLines instanceof List<?> lines)) {
            throw new IllegalStateException("GameContextRegister.getContext 未返回 List");
        }
        var values = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < lines.size(); index++) {
            var item = new LinkedHashMap<String, Object>();
            if (index < contextKeys.size()) {
                item.put("key", contextKeys.get(index));
            }
            item.put("text", MaidApiReflection.clean(lines.get(index)));
            values.add(item);
        }
        return values;
    }

    private static Map<String, Object> contextValue(String key, Object maid) {
        Object contexts = MaidRegistryIntrospection.contextsField();
        if (!(contexts instanceof Map<?, ?> contextMap)) {
            throw new IllegalStateException("GameContextRegister.CONTEXTS 未包含 Map");
        }
        Object context = contextMap.get(key);
        if (context == null) {
            throw new IllegalArgumentException("女仆上下文键未注册：" + key);
        }
        var item = new LinkedHashMap<String, Object>();
        item.put("key", key);
        item.put("label", MaidApiReflection.clean(MaidApiReflection.invoke(context, "label")));
        item.put("value", MaidApiReflection.clean(MaidApiReflection.invoke(context, "getValue", maid)));
        return item;
    }

    private static List<String> contextKeys(String categoryId) {
        Object rawKeys = MaidRegistryIntrospection.contextKeys(categoryId);
        if (!(rawKeys instanceof List<?> keys)) {
            throw new IllegalStateException("GameContextRegister.getContextKeys 未返回 List");
        }
        return cleanValues(keys);
    }

    private static List<String> cleanValues(List<?> values) {
        return values.stream().map(MaidApiReflection::clean).toList();
    }
}
