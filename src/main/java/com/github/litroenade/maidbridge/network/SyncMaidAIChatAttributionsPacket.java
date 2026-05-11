package com.github.litroenade.maidbridge.network;

import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatAccess;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatAttributionStore;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncMaidAIChatAttributionsPacket(
        UUID maidUuid,
        int maidEntityId,
        boolean chatOnly,
        List<MaidAIChatAttributionStore.Entry> entries
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncMaidAIChatAttributionsPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidBridge.MODID, "sync_maid_ai_chat_attributions")
    );
    public static final StreamCodec<ByteBuf, SyncMaidAIChatAttributionsPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SyncMaidAIChatAttributionsPacket decode(@NotNull ByteBuf byteBuf) {
            var buf = new FriendlyByteBuf(byteBuf);
            var maidUuid = buf.readUUID();
            var maidEntityId = buf.readVarInt();
            var chatOnly = buf.readBoolean();
            var size = buf.readVarInt();
            var entries = new ArrayList<MaidAIChatAttributionStore.Entry>(size);
            for (int i = 0; i < size; i++) {
                entries.add(readEntry(buf));
            }
            return new SyncMaidAIChatAttributionsPacket(maidUuid, maidEntityId, chatOnly, entries);
        }

        @Override
        public void encode(@NotNull ByteBuf byteBuf, @NotNull SyncMaidAIChatAttributionsPacket packet) {
            var buf = new FriendlyByteBuf(byteBuf);
            buf.writeUUID(packet.maidUuid);
            buf.writeVarInt(packet.maidEntityId);
            buf.writeBoolean(packet.chatOnly);
            buf.writeVarInt(packet.entries.size());
            for (var entry : packet.entries) {
                writeEntry(buf, entry);
            }
        }
    };

    public SyncMaidAIChatAttributionsPacket {
        entries = List.copyOf(entries);
    }

    public static SyncMaidAIChatAttributionsPacket from(EntityMaid maid, ServerPlayer viewer) {
        return new SyncMaidAIChatAttributionsPacket(
                maid.getUUID(),
                maid.getId(),
                !MaidAIChatAccess.canEditSettings(maid, viewer),
                MaidAIChatAttributionStore.entriesFor(maid)
        );
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncMaidAIChatAttributionsPacket packet, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> MaidBridgeClientPayloadDispatch.handle(packet));
        }
    }

    private static MaidAIChatAttributionStore.Entry readEntry(FriendlyByteBuf buf) {
        return new MaidAIChatAttributionStore.Entry(
                buf.readUUID(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readVarLong(),
                buf.readUUID(),
                buf.readUtf()
        );
    }

    private static void writeEntry(FriendlyByteBuf buf, MaidAIChatAttributionStore.Entry entry) {
        buf.writeUUID(entry.maidUuid());
        buf.writeVarInt(entry.maidEntityId());
        buf.writeUtf(entry.message());
        buf.writeVarLong(entry.gameTime());
        buf.writeUUID(entry.speakerUuid());
        buf.writeUtf(entry.speakerName());
    }
}
