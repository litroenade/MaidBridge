package com.github.litroenade.maidbridge.trace;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AiChainEvent(
        String type,
        long timestampMs,
        Map<String, Object> payload
) {
    public AiChainEvent {
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
