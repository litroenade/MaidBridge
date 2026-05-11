package com.github.litroenade.maidbridge.network;

import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatAttributionStore;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataAttachment;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OpenReadonlyMaidAIChatPacket(
        int entityId,
        UUID maidUuid,
        CompoundTag historyData,
        int currentTokens,
        int maxTokens,
        List<MaidAIChatAttributionStore.Entry> attributionEntries
) implements CustomPacketPayload {
    private static final String MAID_HISTORY_CHAT_TAG = "MaidHistoryChat";
    private static final String MAID_HISTORY_SUMMARY_TAG = "MaidHistorySummary";

    public static final CustomPacketPayload.Type<OpenReadonlyMaidAIChatPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidBridge.MODID, "open_readonly_maid_ai_chat")
    );
    public static final StreamCodec<ByteBuf, OpenReadonlyMaidAIChatPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull OpenReadonlyMaidAIChatPacket decode(@NotNull ByteBuf byteBuf) {
            var buf = new FriendlyByteBuf(byteBuf);
            var entityId = buf.readVarInt();
            var maidUuid = buf.readUUID();
            var historyData = Objects.requireNonNullElse(buf.readNbt(), new CompoundTag());
            var currentTokens = buf.readVarInt();
            var maxTokens = buf.readVarInt();
            var size = buf.readVarInt();
            var attributionEntries = new ArrayList<MaidAIChatAttributionStore.Entry>(size);
            for (int i = 0; i < size; i++) {
                attributionEntries.add(readEntry(buf));
            }
            return new OpenReadonlyMaidAIChatPacket(entityId, maidUuid, historyData, currentTokens, maxTokens, attributionEntries);
        }

        @Override
        public void encode(@NotNull ByteBuf byteBuf, @NotNull OpenReadonlyMaidAIChatPacket packet) {
            var buf = new FriendlyByteBuf(byteBuf);
            buf.writeVarInt(packet.entityId);
            buf.writeUUID(packet.maidUuid);
            buf.writeNbt(packet.historyData);
            buf.writeVarInt(packet.currentTokens);
            buf.writeVarInt(packet.maxTokens);
            buf.writeVarInt(packet.attributionEntries.size());
            for (var entry : packet.attributionEntries) {
                writeEntry(buf, entry);
            }
        }
    };

    public OpenReadonlyMaidAIChatPacket {
        Objects.requireNonNull(maidUuid, "maidUuid");
        historyData = Objects.requireNonNull(historyData, "historyData").copy();
        attributionEntries = List.copyOf(attributionEntries);
    }

    public static OpenReadonlyMaidAIChatPacket from(EntityMaid maid, ServerPlayer viewer) {
        var fullData = maid.getAiChatManager().writeToTag(new CompoundTag());
        var historyData = new CompoundTag();
        copyHistoryTag(fullData, historyData, MAID_HISTORY_CHAT_TAG);
        copyHistoryTag(fullData, historyData, MAID_HISTORY_SUMMARY_TAG);

        return new OpenReadonlyMaidAIChatPacket(
                maid.getId(),
                maid.getUUID(),
                historyData,
                viewer.getData(InitDataAttachment.CHAT_TOKENS).get(),
                AIConfig.MAX_TOKENS_PER_PLAYER.get(),
                MaidAIChatAttributionStore.entriesFor(maid)
        );
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenReadonlyMaidAIChatPacket packet, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> MaidBridgeClientPayloadDispatch.handle(packet));
        }
    }

    private static void copyHistoryTag(CompoundTag source, CompoundTag target, String key) {
        if (source.contains(key)) {
            target.put(key, Objects.requireNonNull(source.get(key)).copy());
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
