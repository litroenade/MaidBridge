package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatAccess;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatAttributionContext;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidExternalAgentDisplayState;
import com.github.litroenade.maidbridge.maid.turn.MaidAgentTurnRequest;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.SendUserChatPackage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.network.message.SendUserChatPackage", remap = false)
public abstract class SendUserChatPackageMixin {
    @Inject(method = "onHandle", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("resource")
    private static void maidbridge$sendMultiplayerMaidChat(SendUserChatPackage message, IPayloadContext context, CallbackInfo ci) {
        if (!(context.player() instanceof ServerPlayer sender)) {
            return;
        }
        Entity entity = sender.level().getEntity(message.maidId());
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive()) {
            return;
        }
        if (Config.isExternalMaidAgentMode() && MaidExternalAgentDisplayState.hasAgent(maid.getUUID())) {
            ci.cancel();
            if (MaidAIChatAccess.canSendChat(maid, sender)) {
                var result = MaidAgentTurnRequest.emit(maid.getAiChatManager(), message.message(), message.clientInfo(), sender);
                MaidBridge.LOGGER.info(
                        "外部 agent 已拦截女仆聊天包 status={} maidUuid={} turnId={} player={} reason={}",
                        result.status(),
                        result.maidUuid(),
                        result.turnId(),
                        sender.getUUID(),
                        result.reason()
                );
            } else {
                MaidBridge.LOGGER.info(
                        "外部 agent 已拦截未授权女仆聊天包 maidUuid={} player={}",
                        maid.getUUID(),
                        sender.getUUID()
                );
            }
            return;
        }
        if (!MaidAIChatAccess.canSendChat(maid, sender)) {
            ci.cancel();
            return;
        }
        MaidAIChatAttributionContext.runWith(
                maid,
                sender,
                message.message(),
                () -> maid.getAiChatManager().chat(message.message(), message.clientInfo(), sender)
        );
        ci.cancel();
    }
}
