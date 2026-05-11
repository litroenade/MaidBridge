package com.github.litroenade.maidbridge.maid.ai.chat.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MaidAIChatClientAccessState {
    private static final Map<UUID, Boolean> CHAT_ONLY_BY_MAID = new ConcurrentHashMap<>();

    private MaidAIChatClientAccessState() {
    }

    public static void setChatOnly(UUID maidUuid, boolean chatOnly) {
        CHAT_ONLY_BY_MAID.put(maidUuid, chatOnly);
    }

    public static boolean isChatOnly(UUID maidUuid) {
        return CHAT_ONLY_BY_MAID.getOrDefault(maidUuid, false);
    }

    public static boolean isChatOnly(EntityMaid maid) {
        return isChatOnly(maid.getUUID());
    }
}
