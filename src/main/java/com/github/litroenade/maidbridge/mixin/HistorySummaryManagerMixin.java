package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.Config;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.summary.HistorySummaryManager;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = HistorySummaryManager.class, remap = false)
public abstract class HistorySummaryManagerMixin {
    @Shadow
    @Final
    private MaidAIChatManager chatManager;

    @Inject(method = "tryScheduleHistorySummary", at = @At("HEAD"), cancellable = true)
    private void maidbridge$cancelExternalSummarySchedule(CallbackInfo ci) {
        if (Config.isExternalMaidAgentMode()) {
            this.chatManager.historySummaryRunning = false;
            ci.cancel();
        }
    }

    @Inject(method = "completeHistorySummary", at = @At("HEAD"), cancellable = true)
    private void maidbridge$cancelExternalSummaryComplete(String summary, List<LLMMessage> snapshot, CallbackInfo ci) {
        if (!Config.isExternalMaidAgentMode()) {
            return;
        }
        this.chatManager.historySummaryRunning = false;
        ci.cancel();
    }
}
