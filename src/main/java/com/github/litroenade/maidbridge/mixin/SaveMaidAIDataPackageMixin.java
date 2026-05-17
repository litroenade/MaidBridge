package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatServerAccessState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.ai.SaveMaidAIDataPackage;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.network.message.ai.SaveMaidAIDataPackage", remap = false)
public abstract class SaveMaidAIDataPackageMixin {
    @Inject(
            method = "handle(Lcom/github/tartaricacid/touhoulittlemaid/network/message/ai/SaveMaidAIDataPackage;Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void maidbridge$blockExternalOrReadonlySave(SaveMaidAIDataPackage message, IPayloadContext context, CallbackInfo ci) {
        if (!context.flow().isServerbound() || !(context.player() instanceof ServerPlayer player)) {
            return;
        }
        var maid = maidbridge$maidFrom(message, player);
        if (maid != null && MaidAIChatServerAccessState.isChatOnly(player, maid.getUUID())) {
            ci.cancel();
            return;
        }
        if (Config.isExternalMaidAgentMode()) {
            maidbridge$saveExternalAgentTtsData(message, player, maid);
            ci.cancel();
        }
    }

    private static EntityMaid maidbridge$maidFrom(SaveMaidAIDataPackage message, ServerPlayer player) {
        var entity = player.level().getEntity(message.entityId());
        return entity instanceof EntityMaid maid ? maid : null;
    }

    private static void maidbridge$saveExternalAgentTtsData(SaveMaidAIDataPackage message, ServerPlayer player, EntityMaid maid) {
        if (maid == null || !maid.isAlive() || !maid.isOwnedBy(player)) {
            return;
        }
        // 外部 agent 只接管 LLM 对话，TTS 配置仍然沿用女仆原生字段。
        var manager = maid.getAiChatManager();
        var data = message.data();
        manager.ttsSite = data.ttsSite;
        manager.ttsModel = data.ttsModel;
        manager.ttsLanguage = data.ttsLanguage;
    }
}
