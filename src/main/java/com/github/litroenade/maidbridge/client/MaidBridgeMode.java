package com.github.litroenade.maidbridge.client;

public enum MaidBridgeMode {
    MESSAGE_BRIDGE("config.maidbridge.maid_bridge_mode.message_bridge"),
    EXTERNAL_AGENT("config.maidbridge.maid_bridge_mode.external_agent");

    private final String translationKey;

    MaidBridgeMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }
}
