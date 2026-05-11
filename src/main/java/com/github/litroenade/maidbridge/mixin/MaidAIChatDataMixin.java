package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatAttributionContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData", remap = false)
public abstract class MaidAIChatDataMixin {
    @Shadow
    @Final
    protected EntityMaid maid;

    @Inject(method = "addUserHistory", at = @At("TAIL"))
    private void maidbridge$commitUserChatAttribution(String message, CallbackInfo ci) {
        MaidAIChatAttributionContext.commitIfCurrent(this.maid, message);
    }
}
