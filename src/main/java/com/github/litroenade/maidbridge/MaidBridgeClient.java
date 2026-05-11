package com.github.litroenade.maidbridge;

import com.github.litroenade.maidbridge.client.MaidBridgeClientPayloadHandlers;
import com.github.litroenade.maidbridge.client.MaidBridgeTouhouClothConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = MaidBridge.MODID, dist = Dist.CLIENT)
public final class MaidBridgeClient {
    public MaidBridgeClient() {
        // 客户端只注册界面回调和 TLM 配置页扩展，避免服务端加载 GUI/Cloth Config 类。
        MaidBridgeClientPayloadHandlers.register();
        MaidBridgeTouhouClothConfig.register();
    }
}
