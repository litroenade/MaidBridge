package com.github.litroenade.maidbridge.client;

import com.github.litroenade.maidbridge.MaidBridge;
import com.github.tartaricacid.touhoulittlemaid.api.client.decoder.GifDecoder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class ExternalEmojiTextureCache {
    private static final Set<ResourceLocation> REGISTERED_TEXTURES = new HashSet<>();

    private ExternalEmojiTextureCache() {
    }

    public static void register(ResourceLocation textureId, byte[] imageBytes, int expectedWidth, int expectedHeight) {
        var manager = Minecraft.getInstance().getTextureManager();
        if (REGISTERED_TEXTURES.contains(textureId)) {
            MaidBridge.LOGGER.debug("跳过已注册的外部表情包纹理 textureId={}", textureId);
            return;
        }
        if (isGif(imageBytes)) {
            manager.register(textureId, new ExternalGifTexture(textureId, imageBytes, expectedWidth, expectedHeight));
            return;
        }
        manager.register(textureId, new ExternalStaticTexture(textureId, imageBytes, expectedWidth, expectedHeight));
    }

    private static boolean isGif(byte[] imageBytes) {
        return imageBytes.length >= 6
                && imageBytes[0] == 0x47
                && imageBytes[1] == 0x49
                && imageBytes[2] == 0x46
                && imageBytes[3] == 0x38
                && (imageBytes[4] == 0x37 || imageBytes[4] == 0x39)
                && imageBytes[5] == 0x61;
    }

    private static boolean hasUnexpectedSize(
            ResourceLocation textureId,
            int expectedWidth,
            int expectedHeight,
            int actualWidth,
            int actualHeight
    ) {
        if (actualWidth == expectedWidth && actualHeight == expectedHeight) {
            return false;
        }
        MaidBridge.LOGGER.warn(
                "拒绝注册外部表情包纹理：尺寸不匹配 textureId={} expected={}x{} actual={}x{}",
                textureId,
                expectedWidth,
                expectedHeight,
                actualWidth,
                actualHeight
        );
        return true;
    }

    @SuppressWarnings("resource")
    private static NativeImage toNativeImage(BufferedImage image, int width, int height) {
        NativeImage nativeImage = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                nativeImage.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return nativeImage;
    }

    private static void closeFrames(NativeImage[] closedFrames) {
        for (NativeImage frame : closedFrames) {
            if (frame != null) {
                frame.close();
            }
        }
    }

    private static final class ExternalStaticTexture extends AbstractTexture {
        private final ResourceLocation textureId;
        private final byte[] imageBytes;
        private final int expectedWidth;
        private final int expectedHeight;

        private ExternalStaticTexture(ResourceLocation textureId, byte[] imageBytes, int expectedWidth, int expectedHeight) {
            this.textureId = textureId;
            this.imageBytes = Arrays.copyOf(imageBytes, imageBytes.length);
            this.expectedWidth = expectedWidth;
            this.expectedHeight = expectedHeight;
        }

        @Override
        public void load(@NotNull ResourceManager manager) {
            if (!RenderSystem.isOnRenderThreadOrInit()) {
                RenderSystem.recordRenderCall(this::doLoad);
            } else {
                doLoad();
            }
        }

        private void doLoad() {
            try (var image = NativeImage.read(new ByteArrayInputStream(imageBytes))) {
                if (hasUnexpectedSize(textureId, expectedWidth, expectedHeight, image.getWidth(), image.getHeight())) {
                    return;
                }
                int width = image.getWidth();
                int height = image.getHeight();
                TextureUtil.prepareImage(getId(), 0, width, height);
                image.upload(0, 0, 0, 0, 0, width, height, false, false, false, false);
                REGISTERED_TEXTURES.add(textureId);
                MaidBridge.LOGGER.debug("外部静态表情包纹理注册完成 textureId={} size={}x{}", textureId, width, height);
            } catch (IOException exception) {
                MaidBridge.LOGGER.warn("注册外部表情包纹理失败：textureId={}", textureId, exception);
            }
        }
    }

    private static final class ExternalGifTexture extends AbstractTexture implements Tickable {
        private final ResourceLocation textureId;
        private final byte[] imageBytes;
        private final int expectedWidth;
        private final int expectedHeight;
        private NativeImage[] frames = new NativeImage[0];
        private int[] frameDelays = new int[0];
        private int currentFrame;
        private int currentFrameDelay;
        private int textureWidth;
        private int textureHeight;

        private ExternalGifTexture(ResourceLocation textureId, byte[] imageBytes, int expectedWidth, int expectedHeight) {
            this.textureId = textureId;
            this.imageBytes = Arrays.copyOf(imageBytes, imageBytes.length);
            this.expectedWidth = expectedWidth;
            this.expectedHeight = expectedHeight;
        }

        @Override
        public void load(@NotNull ResourceManager manager) {
            if (!RenderSystem.isOnRenderThreadOrInit()) {
                RenderSystem.recordRenderCall(this::doLoad);
            } else {
                doLoad();
            }
        }

        private void doLoad() {
            NativeImage[] decodedFrames = new NativeImage[0];
            try {
                GifDecoder decoder = new GifDecoder();
                int status = decoder.read(new ByteArrayInputStream(imageBytes));
                int totalFrames = decoder.getFrameCount();
                Dimension frameSize = decoder.getFrameSize();
                if (status != GifDecoder.STATUS_OK || totalFrames <= 0) {
                    MaidBridge.LOGGER.warn("外部 GIF 表情包解码失败：textureId={} status={} frames={}", textureId, status, totalFrames);
                    return;
                }
                if (hasUnexpectedSize(textureId, expectedWidth, expectedHeight, frameSize.width, frameSize.height)) {
                    return;
                }
                int[] decodedDelays = new int[totalFrames];
                decodedFrames = new NativeImage[totalFrames];
                for (int i = 0; i < totalFrames; i++) {
                    decodedFrames[i] = toNativeImage(decoder.getFrame(i), frameSize.width, frameSize.height);
                    decodedDelays[i] = Math.max(decoder.getDelay(i) / 50, 1);
                }
                TextureUtil.prepareImage(getId(), 0, frameSize.width, frameSize.height);
                decodedFrames[0].upload(0, 0, 0, 0, 0, frameSize.width, frameSize.height, false, false, false, false);
                closeFrames(frames);
                frames = decodedFrames;
                decodedFrames = new NativeImage[0];
                frameDelays = decodedDelays;
                textureWidth = frameSize.width;
                textureHeight = frameSize.height;
                currentFrame = 0;
                currentFrameDelay = 0;
                REGISTERED_TEXTURES.add(textureId);
                MaidBridge.LOGGER.debug(
                        "外部 GIF 表情包纹理注册完成 textureId={} size={}x{} frames={}",
                        textureId,
                        frameSize.width,
                        frameSize.height,
                        totalFrames
                );
            } catch (Exception exception) {
                closeFrames(decodedFrames);
                MaidBridge.LOGGER.warn("注册外部 GIF 表情包纹理失败：textureId={}", textureId, exception);
            }
        }

        @Override
        public void close() {
            super.close();
            closeFrames(frames);
            frames = new NativeImage[0];
            frameDelays = new int[0];
            textureWidth = 0;
            textureHeight = 0;
        }

        @Override
        public void tick() {
            if (!RenderSystem.isOnRenderThread()) {
                RenderSystem.recordRenderCall(this::tickOnRenderThread);
            } else {
                tickOnRenderThread();
            }
        }

        private void tickOnRenderThread() {
            if (frames.length == 0 || frameDelays.length == 0 || textureWidth <= 0 || textureHeight <= 0) {
                return;
            }
            currentFrameDelay++;
            if (currentFrameDelay < frameDelays[currentFrame]) {
                return;
            }
            currentFrameDelay = 0;
            currentFrame = (currentFrame + 1) % frames.length;
            TextureUtil.prepareImage(getId(), 0, textureWidth, textureHeight);
            frames[currentFrame].upload(0, 0, 0, 0, 0, textureWidth, textureHeight, false, false);
        }
    }
}
