package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.maid.ai.chat.client.MaidAIChatAttributionClientCache;
import com.github.litroenade.maidbridge.maid.ai.chat.client.MaidAIChatClientAccessState;
import com.github.litroenade.maidbridge.maid.ai.chat.client.MaidAIChatHistoryWidgetSpeakerNames;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.HistoryChatWidget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.HistoryAIChatScreen", remap = false)
public abstract class HistoryAIChatScreenMixin extends Screen {
    @Shadow
    @Final
    private EntityMaid maid;
    @Shadow
    @Final
    private List<Renderable> historyWidgets;

    private HistoryAIChatScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "addButtons", at = @At("TAIL"))
    private void maidbridge$hideChatOnlyClearButton(CallbackInfo ci) {
        if (!maidbridge$shouldLockHistoryClear(this.maid)) {
            return;
        }
        int clearY = this.maidbridge$getClearButtonY();
        for (Renderable renderable : this.renderables) {
            if (renderable instanceof AbstractWidget widget && widget.getY() == clearY) {
                widget.visible = false;
                widget.active = false;
                return;
            }
        }
    }

    @Inject(method = "addHistoryWidget", at = @At("RETURN"))
    private void maidbridge$bindSpeakerName(LLMMessage message, int posX, CallbackInfoReturnable<Integer> cir) {
        if (message.role() != Role.USER || this.historyWidgets.isEmpty()) {
            return;
        }
        Renderable renderable = this.historyWidgets.getLast();
        if (!(renderable instanceof HistoryChatWidget widget)) {
            return;
        }
        String displayText = this.maidbridge$getDisplayMessage(message).getString();
        var speaker = MaidAIChatAttributionClientCache.resolveSpeaker(this.maid.getUUID(), displayText, message.gameTime());
        if (speaker != null) {
            MaidAIChatHistoryWidgetSpeakerNames.bind(widget, speaker.speakerUuid(), speaker.speakerName());
        }
    }

    @Invoker("getClearButtonY")
    protected abstract int maidbridge$getClearButtonY();

    @Invoker("getDisplayMessage")
    protected abstract Component maidbridge$getDisplayMessage(LLMMessage message);

    @Unique
    private static boolean maidbridge$shouldLockHistoryClear(EntityMaid maid) {
        return maidbridge$isChatOnly(maid) || maidbridge$isNonOwner(maid);
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
