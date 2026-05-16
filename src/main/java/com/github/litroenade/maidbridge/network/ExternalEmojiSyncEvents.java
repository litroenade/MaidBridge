package com.github.litroenade.maidbridge.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class ExternalEmojiSyncEvents {
    private ExternalEmojiSyncEvents() {
    }

    public static void onTrackingPlayer(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof EntityMaid && event.getEntity() instanceof ServerPlayer serverPlayer) {
            ExternalEmojiPayloadCache.syncTo(serverPlayer);
        }
    }
}
