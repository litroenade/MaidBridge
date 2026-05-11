package com.github.litroenade.maidbridge.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.event.PressAIChatKeyEvent", remap = false)
public abstract class PressAIChatKeyEventMixin {
    @Inject(method = "maidCheck", at = @At("RETURN"), cancellable = true)
    private static void maidbridge$allowMultiplayerMaidChatKey(CallbackInfoReturnable<EntityMaid> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        HitResult hitResult = mc.hitResult;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return;
        }
        if (entityHitResult.getEntity() instanceof EntityMaid maid && maid.isAlive()) {
            cir.setReturnValue(maid);
        }
    }
}
