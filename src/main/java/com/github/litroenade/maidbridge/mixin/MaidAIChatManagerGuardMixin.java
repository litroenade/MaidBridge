package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidExternalAgentDisplayState;
import com.github.litroenade.maidbridge.maid.turn.MaidAgentTurnRequest;
import com.github.litroenade.maidbridge.trace.ReflectiveAccess;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaidAIChatManager.class, remap = false)
public abstract class MaidAIChatManagerGuardMixin {
    @Inject(method = "chat", at = @At("HEAD"), cancellable = true)
    private void maidbridge$guardExternalAgentTurn(String message, ChatClientInfo clientInfo, ServerPlayer sender, CallbackInfo ci) {
        Object maid = ReflectiveAccess.invoke(this, "getMaid");
        if (!(maid instanceof EntityMaid entityMaid)
                || !Config.isExternalMaidAgentMode()
                || !MaidExternalAgentDisplayState.hasAgent(entityMaid.getUUID())) {
            return;
        }
        ci.cancel();
        var result = MaidAgentTurnRequest.emit(this, message, clientInfo, sender);
        MaidBridge.LOGGER.info(
                "外部 agent 已接管 MaidAIChatManager.chat status={} maidUuid={} turnId={} reason={}",
                result.status(),
                result.maidUuid(),
                result.turnId(),
                result.reason()
        );
    }
}
