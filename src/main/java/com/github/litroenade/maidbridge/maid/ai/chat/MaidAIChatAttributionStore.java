package com.github.litroenade.maidbridge.maid.ai.chat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MaidAIChatAttributionStore {
    private static final int MAX_ENTRIES_PER_MAID = 256;
    private static final String ATTRIBUTIONS_TAG = "MaidBridgeHistoryAttributions";
    private static final String MAID_UUID_TAG = "MaidUuid";
    private static final String MAID_ENTITY_ID_TAG = "MaidEntityId";
    private static final String MESSAGE_TAG = "Message";
    private static final String GAME_TIME_TAG = "GameTime";
    private static final String SPEAKER_UUID_TAG = "SpeakerUuid";
    private static final String SPEAKER_NAME_TAG = "SpeakerName";
    private static final Map<UUID, Deque<Entry>> ENTRIES_BY_MAID = new HashMap<>();

    private MaidAIChatAttributionStore() {
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

    public static synchronized void clear(EntityMaid maid) {
        ENTRIES_BY_MAID.remove(maid.getUUID());
    }

    public static synchronized List<Entry> entriesFor(EntityMaid maid) {
        var entries = ENTRIES_BY_MAID.get(maid.getUUID());
        if (entries == null) {
            return List.of();
        }
        return List.copyOf(entries);
    }

    public static synchronized void writeToTag(EntityMaid maid, CompoundTag tag) {
        var entries = ENTRIES_BY_MAID.get(maid.getUUID());
        if (entries == null || entries.isEmpty()) {
            tag.remove(ATTRIBUTIONS_TAG);
            return;
        }
        var list = new ListTag();
        for (Entry entry : entries) {
            if (!maid.getUUID().equals(entry.maidUuid())) {
                continue;
            }
            var item = new CompoundTag();
            item.putUUID(MAID_UUID_TAG, entry.maidUuid());
            item.putInt(MAID_ENTITY_ID_TAG, entry.maidEntityId());
            item.putString(MESSAGE_TAG, entry.message());
            item.putLong(GAME_TIME_TAG, entry.gameTime());
            item.putUUID(SPEAKER_UUID_TAG, entry.speakerUuid());
            item.putString(SPEAKER_NAME_TAG, entry.speakerName());
            list.add(item);
        }
        if (list.isEmpty()) {
            tag.remove(ATTRIBUTIONS_TAG);
            return;
        }
        tag.put(ATTRIBUTIONS_TAG, list);
    }

    public static synchronized void readFromTag(EntityMaid maid, CompoundTag tag) {
        if (!tag.contains(ATTRIBUTIONS_TAG, Tag.TAG_LIST)) {
            clear(maid);
            return;
        }
        var list = tag.getList(ATTRIBUTIONS_TAG, Tag.TAG_COMPOUND);
        var entries = new ArrayDeque<Entry>();
        for (int i = 0; i < list.size(); i++) {
            Entry entry = entryFromTag(maid, list.getCompound(i));
            if (entry == null) {
                continue;
            }
            entries.addLast(entry);
            trim(entries);
        }
        if (entries.isEmpty()) {
            clear(maid);
            return;
        }
        ENTRIES_BY_MAID.put(maid.getUUID(), entries);
    }

    private static void trim(Deque<Entry> entries) {
        while (entries.size() > MAX_ENTRIES_PER_MAID) {
            entries.removeFirst();
        }
    }

    private static Entry entryFromTag(EntityMaid maid, CompoundTag tag) {
        if (!tag.hasUUID(MAID_UUID_TAG)
                || !tag.contains(MAID_ENTITY_ID_TAG, Tag.TAG_INT)
                || !tag.contains(MESSAGE_TAG, Tag.TAG_STRING)
                || !tag.contains(GAME_TIME_TAG, Tag.TAG_LONG)
                || !tag.hasUUID(SPEAKER_UUID_TAG)
                || !tag.contains(SPEAKER_NAME_TAG, Tag.TAG_STRING)) {
            return null;
        }
        UUID maidUuid = tag.getUUID(MAID_UUID_TAG);
        if (!maid.getUUID().equals(maidUuid)) {
            return null;
        }
        String speakerName = tag.getString(SPEAKER_NAME_TAG);
        if (speakerName.isBlank()) {
            return null;
        }
        return new Entry(
                maidUuid,
                tag.getInt(MAID_ENTITY_ID_TAG),
                tag.getString(MESSAGE_TAG),
                tag.getLong(GAME_TIME_TAG),
                tag.getUUID(SPEAKER_UUID_TAG),
                speakerName
        );
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
