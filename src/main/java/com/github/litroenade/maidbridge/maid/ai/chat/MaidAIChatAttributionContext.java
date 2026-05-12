package com.github.litroenade.maidbridge.maid.ai.chat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

public final class MaidAIChatAttributionContext {
    private static final ThreadLocal<PendingAttribution> CURRENT = new ThreadLocal<>();

    private MaidAIChatAttributionContext() {
    }

    public static void runWith(EntityMaid maid, ServerPlayer speaker, String message, Runnable action) {
        Objects.requireNonNull(maid, "maid");
        Objects.requireNonNull(speaker, "speaker");
        runWith(maid, speaker.getUUID(), speaker.getGameProfile().getName(), message, action);
    }

    public static void runWith(EntityMaid maid, UUID speakerUuid, String speakerName, String message, Runnable action) {
        Objects.requireNonNull(maid, "maid");
        Objects.requireNonNull(speakerUuid, "speakerUuid");
        Objects.requireNonNull(speakerName, "speakerName");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(action, "action");
        var previous = CURRENT.get();
        CURRENT.set(new PendingAttribution(maid.getUUID(), speakerUuid, speakerName, message));
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void commitIfCurrent(EntityMaid maid, String message) {
        Objects.requireNonNull(maid, "maid");
        Objects.requireNonNull(message, "message");
        var pending = CURRENT.get();
        if (pending == null || pending.committed || !pending.matches(maid, message)) {
            return;
        }
        pending.committed = true;
        MaidAIChatAttributionStore.record(maid, pending.speakerUuid, pending.speakerName, message);
    }

    private static final class PendingAttribution {
        private final UUID maidUuid;
        private final UUID speakerUuid;
        private final String speakerName;
        private final String message;
        private boolean committed;

        private PendingAttribution(UUID maidUuid, UUID speakerUuid, String speakerName, String message) {
            this.maidUuid = maidUuid;
            this.speakerUuid = speakerUuid;
            this.speakerName = speakerName;
            this.message = message;
        }

        private boolean matches(EntityMaid maid, String message) {
            return maidUuid.equals(maid.getUUID()) && this.message.equals(message);
        }
    }
}
