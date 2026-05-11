package com.github.litroenade.maidbridge.maid.ai.chat;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class MaidAIChatEvents {
    private MaidAIChatEvents() {
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        MaidAIChatServerAccessState.clearChatOnly(event.getEntity().getUUID());
    }
}
