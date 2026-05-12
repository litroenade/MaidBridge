package com.github.litroenade.maidbridge.mixin;

import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatAccess;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatServerAccessState;
import com.github.litroenade.maidbridge.network.MaidBridgeNetwork;
import com.github.litroenade.maidbridge.network.OpenReadonlyMaidAIChatPacket;
import com.github.litroenade.maidbridge.network.SyncMaidAIChatAttributionsPacket;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.network.message.ai.OpenMaidAIChatPacket;
import com.github.tartaricacid.touhoulittlemaid.network.message.ai.SyncMaidAIDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.network.message.ai.OpenMaidAIChatPacket", remap = false)
public abstract class OpenMaidAIChatPacketMixin {
    @Inject(
            method = "handle(Lcom/github/tartaricacid/touhoulittlemaid/network/message/ai/OpenMaidAIChatPacket;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    @SuppressWarnings("resource")
    private static void maidbridge$openMultiplayerMaidChat(OpenMaidAIChatPacket message, ServerPlayer player, CallbackInfo ci) {
        if (player == null) {
            return;
        }
        Entity entity = player.level().getEntity(message.entityId());
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive()) {
            return;
        }
        if (!MaidAIChatAccess.canOpenChat(maid, player)) {
            return;
        }
        if (MaidAIChatAccess.canEditSettings(maid, player)) {
            MaidAIChatServerAccessState.clearChatOnly(player);
            MaidBridgeNetwork.sendToClientPlayer(SyncMaidAIChatAttributionsPacket.from(maid, player), player);
            NetworkHandler.sendToClientPlayer(new SyncMaidAIDataPacket(maid, player), player);
            ci.cancel();
            return;
        }
        if (MaidAIChatAccess.canOpenChat(maid, player)) {
            MaidAIChatServerAccessState.setChatOnly(player, maid.getUUID());
            MaidBridgeNetwork.sendToClientPlayer(OpenReadonlyMaidAIChatPacket.from(maid, player), player);
            ci.cancel();
        }
    }
}
