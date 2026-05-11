package com.github.litroenade.maidbridge.maid.ai.chat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MaidAIChatAttributionStore {
    private static final int MAX_ENTRIES_PER_MAID = 256;
    private static final Map<UUID, Deque<Entry>> ENTRIES_BY_MAID = new HashMap<>();

    private MaidAIChatAttributionStore() {
    }

    public static synchronized void record(EntityMaid maid, ServerPlayer speaker, String message) {
        record(maid, speaker.getUUID(), speaker.getGameProfile().getName(), message);
    }

    @SuppressWarnings("resource")
    public static synchronized void record(EntityMaid maid, UUID speakerUuid, String speakerName, String message) {
        var entry = new Entry(
                maid.getUUID(),
                maid.getId(),
                message,
                maid.level().getGameTime(),
                speakerUuid,
                speakerName
        );
        var entries = ENTRIES_BY_MAID.computeIfAbsent(entry.maidUuid(), ignored -> new ArrayDeque<>());
        entries.addLast(entry);
        trim(entries);
    }

    public static synchronized List<Entry> entriesFor(EntityMaid maid) {
        var entries = ENTRIES_BY_MAID.get(maid.getUUID());
        if (entries == null) {
            return List.of();
        }
        return List.copyOf(entries);
    }

    private static void trim(Deque<Entry> entries) {
        while (entries.size() > MAX_ENTRIES_PER_MAID) {
            entries.removeFirst();
        }
    }

    public record Entry(
            UUID maidUuid,
            int maidEntityId,
            String message,
            long gameTime,
            UUID speakerUuid,
            String speakerName
    ) {
        public Entry {
            Objects.requireNonNull(maidUuid, "maidUuid");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(speakerUuid, "speakerUuid");
            Objects.requireNonNull(speakerName, "speakerName");
        }
    }
}
