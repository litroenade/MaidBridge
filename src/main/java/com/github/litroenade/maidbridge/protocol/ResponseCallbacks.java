package com.github.litroenade.maidbridge.protocol;

import java.util.Map;

@SuppressWarnings("unused")
public final class ResponseCallbacks {
    private ResponseCallbacks() {
    }

    @FunctionalInterface
    public interface Success {
        void send(String replyTo, String traceId, Map<String, Object> payload);
    }

    @FunctionalInterface
    public interface Failure {
        void send(String replyTo, String traceId, String error);
    }
}
