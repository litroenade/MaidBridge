package com.github.litroenade.maidbridge.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class MaidBridgeTouhouClothConfig {
    private MaidBridgeTouhouClothConfig() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MaidBridgeTouhouClothConfig::appendMaidBridgeConfig);
    }

    private static void appendMaidBridgeConfig(AddClothConfigEvent event) {
        // 女仆本体提供了附属配置页事件，通过事件接入可以避免改 GUI 类。
        MaidBridgeClientConfig.appendAsTouhouCategory(event.getRoot(), event.getEntryBuilder());
    }
}
