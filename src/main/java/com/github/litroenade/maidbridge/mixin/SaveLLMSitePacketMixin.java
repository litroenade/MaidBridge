package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatServerAccessState;
import com.github.tartaricacid.touhoulittlemaid.network.message.ai.SaveLLMSitePacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.network.message.ai.SaveLLMSitePacket", remap = false)
public abstract class SaveLLMSitePacketMixin {
    @Inject(
            method = "handle(Lcom/github/tartaricacid/touhoulittlemaid/network/message/ai/SaveLLMSitePacket;Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void maidbridge$blockChatOnlySaveLLMSite(SaveLLMSitePacket message, IPayloadContext context, CallbackInfo ci) {
        if (context.flow().isServerbound()
                && context.player() instanceof ServerPlayer player
                && (MaidAIChatServerAccessState.isChatOnly(player) || Config.isExternalMaidAgentMode())) {
            ci.cancel();
        }
    }
}
