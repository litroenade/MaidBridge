package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.maid.ai.chat.MaidExternalAgentDisplayState;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatServerAccessState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.ai.ClearMaidAIDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
    @SuppressWarnings("resource")
    private static void maidbridge$blockExternalOrReadonlyClear(ClearMaidAIDataPacket message, IPayloadContext context, CallbackInfo ci) {
        if (context.flow().isServerbound()
                && context.player() instanceof ServerPlayer player) {
            Entity entity = player.level().getEntity(message.entityId());
            if (!(entity instanceof EntityMaid maid)) {
                return;
            }
            boolean externalAgentMaid = MaidExternalAgentDisplayState.hasAgent(maid.getUUID());
            boolean readonlyMaid = MaidAIChatServerAccessState.isChatOnly(player, maid.getUUID());
            if (readonlyMaid || externalAgentMaid) {
                ci.cancel();
            }
        }
    }
}
