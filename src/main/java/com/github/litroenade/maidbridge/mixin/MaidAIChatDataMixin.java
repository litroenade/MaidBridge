package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatAttributionContext;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatAttributionStore;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData", remap = false)
public abstract class MaidAIChatDataMixin {
    @Shadow
    @Final
    protected EntityMaid maid;

    @Inject(method = "addUserHistory", at = @At("TAIL"))
    private void maidbridge$commitUserChatAttribution(String message, CallbackInfo ci) {
        MaidAIChatAttributionContext.commitIfCurrent(this.maid, message);
    }

    @Inject(method = "readFromTag", at = @At("RETURN"))
    private void maidbridge$readHistoryAttributions(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        MaidAIChatAttributionStore.readFromTag(this.maid, tag);
    }

    @Inject(method = "writeToTag", at = @At("RETURN"))
    private void maidbridge$writeHistoryAttributions(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        // TLM 历史不保存发言人，MaidBridge 只把多人归因作为附加记录落盘。
        MaidAIChatAttributionStore.writeToTag(this.maid, cir.getReturnValue());
    }

    @Inject(method = "clearAllChatMemory", at = @At("TAIL"))
    private void maidbridge$clearHistoryAttributions(CallbackInfo ci) {
        MaidAIChatAttributionStore.clear(this.maid);
    }
}
