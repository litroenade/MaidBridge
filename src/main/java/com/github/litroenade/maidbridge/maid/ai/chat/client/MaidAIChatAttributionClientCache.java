package com.github.litroenade.maidbridge.maid.ai.chat.client;

import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatAttributionStore;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MaidAIChatAttributionClientCache {
    private static final int MAX_ENTRIES_PER_MAID = 256;
    private static final Map<UUID, Deque<MaidAIChatAttributionStore.Entry>> ENTRIES_BY_MAID = new HashMap<>();

    private MaidAIChatAttributionClientCache() {
    }

    public static synchronized void replaceEntries(UUID maidUuid, List<MaidAIChatAttributionStore.Entry> entries) {
        Objects.requireNonNull(maidUuid, "maidUuid");
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            ENTRIES_BY_MAID.remove(maidUuid);
            return;
        }
        var replacement = new ArrayDeque<MaidAIChatAttributionStore.Entry>();
        int start = Math.max(0, entries.size() - MAX_ENTRIES_PER_MAID);
        for (int i = start; i < entries.size(); i++) {
            var entry = Objects.requireNonNull(entries.get(i), "entry");
            if (maidUuid.equals(entry.maidUuid())) {
                replacement.addLast(entry);
            }
        }
        if (replacement.isEmpty()) {
            ENTRIES_BY_MAID.remove(maidUuid);
            return;
        }
        ENTRIES_BY_MAID.put(maidUuid, replacement);
    }

    public static synchronized MaidAIChatAttributionStore.Entry resolveSpeaker(UUID maidUuid, String message, long gameTime) {
        Objects.requireNonNull(maidUuid, "maidUuid");
        Objects.requireNonNull(message, "message");
        var entries = ENTRIES_BY_MAID.get(maidUuid);
        if (entries == null) {
            return null;
        }
        var descending = entries.descendingIterator();
        while (descending.hasNext()) {
            var entry = descending.next();
            if (entry.gameTime() == gameTime && message.equals(entry.message())) {
                return entry;
            }
        }
        return null;
    }
}
