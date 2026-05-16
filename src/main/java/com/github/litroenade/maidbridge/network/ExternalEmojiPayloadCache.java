package com.github.litroenade.maidbridge.network;

import com.github.litroenade.maidbridge.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 外部表情纹理和 TLM 气泡数据走两条同步链路；玩家后进入追踪范围时需要补发纹理载荷。
 */
public final class ExternalEmojiPayloadCache {
    private static final LinkedHashMap<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();

    private ExternalEmojiPayloadCache() {
    }

    public static synchronized void remember(SyncExternalEmojiPacket packet) {
        sweepExpired(System.currentTimeMillis());
        var ttlMs = Math.max(1, Config.externalEmojiCacheTtlMs);
        ENTRIES.put(packet.textureId(), new Entry(packet, System.currentTimeMillis() + ttlMs));
        trimToLimit();
    }

    public static synchronized void syncTo(ServerPlayer player) {
        var now = System.currentTimeMillis();
        sweepExpired(now);
        for (var entry : ENTRIES.values()) {
            MaidBridgeNetwork.sendToClientPlayer(entry.packet(), player);
        }
    }

    public static synchronized void sweepExpired(long nowMs) {
        ENTRIES.entrySet().removeIf(entry -> entry.getValue().expiresAtMs() <= nowMs);
    }

    private static void trimToLimit() {
        var maxEntries = Math.max(1, Config.maxExternalEmojiCacheEntries);
        while (ENTRIES.size() > maxEntries) {
            ResourceLocation firstKey = null;
            for (Map.Entry<ResourceLocation, Entry> entry : ENTRIES.entrySet()) {
                firstKey = entry.getKey();
                break;
            }
            if (firstKey == null) {
                return;
            }
            ENTRIES.remove(firstKey);
        }
    }

    private record Entry(SyncExternalEmojiPacket packet, long expiresAtMs) {
    }
}
