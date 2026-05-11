package com.github.litroenade.maidbridge.trace;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LlmRequestCapturePayload {
    private static final Gson GSON = new Gson();

    private LlmRequestCapturePayload() {
    }

    public static Map<String, Object> fromRequestBody(String requestBody, boolean includeRawRequest, int maxRawCharacters) {
        var body = requestBody == null ? "" : requestBody;
        var payload = new LinkedHashMap<String, Object>();
        var json = parseObject(body);
        payload.put("llm_model", modelName(json));
        payload.put("message_count", arraySize(json, "messages"));
        payload.put("tool_count", arraySize(json, "tools"));
        payload.put("byte_length", body.getBytes(StandardCharsets.UTF_8).length);
        payload.put("sha256", sha256(body));
        if (includeRawRequest) {
            var limit = Math.max(0, maxRawCharacters);
            var truncated = body.length() > limit;
            payload.put("raw_request", truncated ? body.substring(0, limit) : body);
            payload.put("raw_request_truncated", truncated);
            payload.put("raw_request_characters", body.length());
        }
        return payload;
    }

    private static JsonObject parseObject(String body) {
        try {
            var json = GSON.fromJson(body, JsonObject.class);
            return json == null ? new JsonObject() : json;
        } catch (RuntimeException exception) {
            return new JsonObject();
        }
    }

    private static String modelName(JsonObject json) {
        if (!json.has("model") || !json.get("model").isJsonPrimitive()) {
            return "";
        }
        return json.get("model").getAsString();
    }

    private static int arraySize(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return 0;
        }
        return json.getAsJsonArray(key).size();
    }

    private static String sha256(String body) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(body.getBytes(StandardCharsets.UTF_8));
            var builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append("%02x".formatted(value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", exception);
        }
    }
}
