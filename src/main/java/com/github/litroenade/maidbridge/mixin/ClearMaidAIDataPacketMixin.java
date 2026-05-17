package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatServerAccessState;
import com.github.tartaricacid.touhoulittlemaid.network.message.ai.ClearMaidAIDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.network.message.ai.ClearMaidAIDataPacket", remap = false)
public abstract class ClearMaidAIDataPacketMixin {
    @Inject(
            method = "handle(Lcom/github/tartaricacid/touhoulittlemaid/network/message/ai/ClearMaidAIDataPacket;Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void maidbridge$blockExternalOrReadonlyClear(ClearMaidAIDataPacket message, IPayloadContext context, CallbackInfo ci) {
        if (context.flow().isServerbound()
                && context.player() instanceof ServerPlayer player
                && MaidAIChatServerAccessState.shouldBlockMaidAiDataEdit(player, message.entityId())) {
            ci.cancel();
        }
    }
}
