package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.maid.ai.chat.client.MaidAIChatClientAccessState;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.AIChatScreen", remap = false)
public abstract class AIChatScreenMixin extends Screen {
    @Shadow
    @Final
    private EntityMaid maid;
    @Shadow
    private FlatColorButton configButton;
    @Shadow
    private FlatColorButton settingButton;
    @Shadow
    private FlatColorButton llmButton;
    @Shadow
    private FlatColorButton ttsButton;
    @Shadow
    private FlatColorButton langButton;

    private AIChatScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void maidbridge$lockChatOnlyControls(CallbackInfo ci) {
        if (!maidbridge$shouldLockControls()) {
            return;
        }
        maidbridge$lockButton(this.configButton);
        maidbridge$lockButton(this.settingButton);
        maidbridge$lockButton(this.llmButton);
        maidbridge$lockButton(this.ttsButton);
        maidbridge$lockButton(this.langButton);
    }

    @Inject(method = "refreshSelectorButtons", at = @At("TAIL"))
    private void maidbridge$keepChatOnlySelectorsLocked(CallbackInfo ci) {
        if (!maidbridge$shouldLockControls()) {
            return;
        }
        maidbridge$lockButton(this.llmButton);
        maidbridge$lockButton(this.ttsButton);
        maidbridge$lockButton(this.langButton);
    }

    @Inject(method = "togglePopup", at = @At("HEAD"), cancellable = true)
    private void maidbridge$blockChatOnlyPopup(@Coerce Object type, CallbackInfo ci) {
        if (maidbridge$shouldLockControls()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSelectionSummaries", at = @At("HEAD"), cancellable = true)
    private void maidbridge$hideChatOnlySelectionSummaries(GuiGraphics graphics, CallbackInfo ci) {
        if (maidbridge$shouldLockControls()) {
            ci.cancel();
        }
    }

    @Unique
    private boolean maidbridge$shouldLockControls() {
        return maidbridge$isChatOnly(this.maid) || maidbridge$isNonOwner(this.maid);
    }

    @Unique
    private static void maidbridge$lockButton(FlatColorButton button) {
        if (button == null) {
            return;
        }
        button.visible = false;
        button.active = false;
    }

    @Unique
    private static boolean maidbridge$isNonOwner(EntityMaid maid) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && !maid.isOwnedBy(player);
    }

    @Unique
    private static boolean maidbridge$isChatOnly(EntityMaid maid) {
        return MaidAIChatClientAccessState.isChatOnly(maid);
    }

}
