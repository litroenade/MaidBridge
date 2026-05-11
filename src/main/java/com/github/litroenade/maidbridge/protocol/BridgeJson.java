package com.github.litroenade.maidbridge.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BridgeJson {
    private static final Gson GSON = new Gson();

    private BridgeJson() {
    }

    static String toJson(Map<String, Object> frame, int maxBytes) {
        var json = GSON.toJson(frame);
        if (json.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException("MaidBridge 帧超过 maxBridgeMessageBytes");
        }
        return json;
    }

    static JsonObject parseRoot(String rawFrame, int maxBytes) {
        return parseRoot(rawFrame, maxBytes, true);
    }

    static JsonObject parseRoot(String rawFrame, int maxBytes, boolean requireProtocol) {
        if (rawFrame == null || rawFrame.isBlank()) {
            throw new IllegalArgumentException("MaidBridge 入站帧不能为空");
        }
        if (rawFrame.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException("MaidBridge 入站帧超过 maxBridgeMessageBytes");
        }
        var json = GSON.fromJson(rawFrame, JsonObject.class);
        if (json == null || !json.isJsonObject()) {
            throw new IllegalArgumentException("MaidBridge 入站帧根节点必须是对象");
        }
        if (requireProtocol) {
            validateProtocol(json);
        }
        return json;
    }

    static JsonObject requiredObject(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) {
            throw new IllegalArgumentException(key + " 必须是对象");
        }
        return json.getAsJsonObject(key);
    }

    static String requiredString(JsonObject json, String key) {
        var value = optionalString(json, key);
        if (value.isBlank()) {
            throw new IllegalArgumentException(key + " 必须是非空字符串");
        }
        return value;
    }

    static String optionalString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return "";
        }
        var value = json.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " 必须是字符串");
        }
        return value.getAsString().trim();
    }

    static String optionalIdString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return "";
        }
        var value = json.get(key);
        if (!value.isJsonPrimitive()) {
            throw new IllegalArgumentException(key + " 必须是字符串或数字");
        }
        var primitive = value.getAsJsonPrimitive();
        if (primitive.isString() || primitive.isNumber()) {
            return primitive.getAsString().trim();
        }
        throw new IllegalArgumentException(key + " 必须是字符串或数字");
    }

    static Map<String, Object> optionalObjectMap(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return Map.of();
        }
        if (!json.get(key).isJsonObject()) {
            throw new IllegalArgumentException(key + " 必须是对象");
        }
        return objectMap(json.getAsJsonObject(key));
    }

    static Map<String, Object> objectMap(JsonObject object) {
        var map = new LinkedHashMap<String, Object>();
        for (var entry : object.entrySet()) {
            map.put(entry.getKey(), javaValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(map);
    }

    static Object javaValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            return objectMap(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            var list = new ArrayList<>();
            for (var item : element.getAsJsonArray()) {
                list.add(javaValue(item));
            }
            return Collections.unmodifiableList(list);
        }
        var primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return numberValue(primitive);
        }
        return primitive.getAsString();
    }

    static List<String> optionalStringList(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return List.of();
        }
        if (!json.get(key).isJsonArray()) {
            throw new IllegalArgumentException(key + " 必须是数组");
        }
        var values = new ArrayList<String>();
        for (var item : json.getAsJsonArray(key)) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(key + " 只能包含字符串");
            }
            var value = item.getAsString().trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static void validateProtocol(JsonObject json) {
        /*
         * protocol 只校验命名空间，具体字段兼容性由各 parse* 方法按业务帧分别判断。
         * 这样协议名保持稳定，结构演进不会被误写进业务事件名。
         */
        var protocol = requiredString(json, "protocol");
        if (!BridgeProtocol.PROTOCOL.equals(protocol)) {
            throw new IllegalArgumentException("不支持的 MaidBridge 协议：" + protocol);
        }
    }

    private static Object numberValue(JsonPrimitive primitive) {
        var decimal = new BigDecimal(primitive.getAsString());
        var normalized = decimal.stripTrailingZeros();
        if (normalized.scale() <= 0) {
            try {
                return normalized.longValueExact();
            } catch (ArithmeticException exception) {
                return normalized.toPlainString();
            }
        }
        return decimal.doubleValue();
    }
}
