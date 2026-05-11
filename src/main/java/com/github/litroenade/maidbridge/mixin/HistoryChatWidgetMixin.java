package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.maid.ai.chat.client.MaidAIChatHistoryWidgetSpeakerNames;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.HistoryChatWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.HistoryChatWidget", remap = false)
public abstract class HistoryChatWidgetMixin extends AbstractWidget {
    @Shadow
    @Final
    private boolean isLeft;
    @Shadow
    @Final
    private boolean isTool;

    private HistoryChatWidgetMixin() {
        super(0, 0, 0, 0, Component.empty());
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void maidbridge$renderSpeakerName(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                              CallbackInfo ci) {
        if (this.isLeft || this.isTool) {
            return;
        }
        String speakerName = MaidAIChatHistoryWidgetSpeakerNames.get((HistoryChatWidget) (Object) this);
        if (StringUtils.isBlank(speakerName)) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        float scale = 0.5f;
        int avatarCenterX = this.getX() + this.getWidth() + 14;
        int avatarTopY = this.getY() + (this.getHeight() - 16) / 2;
        String clippedName = font.plainSubstrByWidth(speakerName, 96);
        int scaledX = Math.round(avatarCenterX / scale) - font.width(clippedName) / 2;
        int scaledY = Math.round((avatarTopY - 7) / scale);

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, clippedName, scaledX, scaledY, 0xFFE6E6E6, false);
        graphics.pose().popPose();
    }
}
