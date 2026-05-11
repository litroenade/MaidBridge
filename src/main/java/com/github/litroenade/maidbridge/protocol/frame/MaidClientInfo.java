package com.github.litroenade.maidbridge.protocol.frame;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MaidClientInfo(
        String language,
        String name,
        List<String> description,
        String roomId,
        String sourceMemberId,
        String channelId,
        Map<String, Object> metadata
) {
    public MaidClientInfo(String language, String name, List<String> description) {
        this(language, name, description, "", "", "", Map.of());
    }

    public MaidClientInfo {
        language = language == null ? "" : language.trim();
        name = name == null ? "" : name.trim();
        description = List.copyOf(description == null ? List.of() : description);
        roomId = roomId == null ? "" : roomId.trim();
        sourceMemberId = sourceMemberId == null ? "" : sourceMemberId.trim();
        channelId = channelId == null ? "" : channelId.trim();
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata == null ? Map.of() : metadata));
    }

    public Map<String, Object> toBridgePayload(String defaultLanguage, String defaultName) {
        var payload = new LinkedHashMap<>(metadata);
        payload.put("language", firstNonBlank(language, defaultLanguage));
        payload.put("name", firstNonBlank(name, defaultName));
        payload.put("description", description);
        putIfPresent(payload, "room_id", roomId);
        putIfPresent(payload, "source_member_id", sourceMemberId);
        putIfPresent(payload, "channel_id", channelId);
        return payload;
    }

    private static void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
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
