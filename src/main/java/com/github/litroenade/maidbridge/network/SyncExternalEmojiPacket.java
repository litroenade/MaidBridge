package com.github.litroenade.maidbridge.network;

import com.github.litroenade.maidbridge.MaidBridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public record SyncExternalEmojiPacket(
        ResourceLocation textureId,
        byte[] imageBytes,
        int width,
        int height
) implements CustomPacketPayload {
    public static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;
    public static final CustomPacketPayload.Type<SyncExternalEmojiPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidBridge.MODID, "sync_external_emoji")
    );
    public static final StreamCodec<ByteBuf, SyncExternalEmojiPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SyncExternalEmojiPacket decode(@NotNull ByteBuf byteBuf) {
            var buf = new FriendlyByteBuf(byteBuf);
            var textureId = buf.readResourceLocation();
            var width = buf.readVarInt();
            var height = buf.readVarInt();
            var imageBytes = buf.readByteArray(MAX_IMAGE_BYTES);
            return new SyncExternalEmojiPacket(textureId, imageBytes, width, height);
        }

        @Override
        public void encode(@NotNull ByteBuf byteBuf, @NotNull SyncExternalEmojiPacket packet) {
            var buf = new FriendlyByteBuf(byteBuf);
            buf.writeResourceLocation(packet.textureId);
            buf.writeVarInt(packet.width);
            buf.writeVarInt(packet.height);
            buf.writeByteArray(packet.imageBytes);
        }
    };

    public SyncExternalEmojiPacket {
        Objects.requireNonNull(textureId, "textureId");
        var safeImageBytes = imageBytes == null ? new byte[0] : imageBytes;
        if (safeImageBytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("外部表情包网络载荷超过协议上限");
        }
        imageBytes = Arrays.copyOf(safeImageBytes, safeImageBytes.length);
    }

    @Override
    public byte[] imageBytes() {
        return Arrays.copyOf(imageBytes, imageBytes.length);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncExternalEmojiPacket packet, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> MaidBridgeClientPayloadDispatch.handle(packet));
        }
    }
}
