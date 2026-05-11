package com.github.litroenade.maidbridge.item;

import com.github.litroenade.maidbridge.registry.MaidBridgeItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class MaidUuidProbeItem extends Item {
    private MaidUuidProbeItem() {
        super(new Properties().stacksTo(1));
    }

    public static Item create() {
        return new MaidUuidProbeItem();
    }

    @Override
    public boolean isFoil(ItemStack pStack) {
        return true;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand usedHand) {
        if (target instanceof EntityMaid maid) {
            sendMaidIdentity(player, maid);
            return InteractionResult.SUCCESS;
        }
        return super.interactLivingEntity(stack, player, target, usedHand);
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        Entity target = event.getTarget();
        if (!stack.is(MaidBridgeItems.MAID_UUID_PROBE.get()) || !(target instanceof EntityMaid maid)) {
            return;
        }
        // 非主人右键女仆时物品交互可能走不到，这里兜底保证探针可用。
        sendMaidIdentity(player, maid);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SuppressWarnings("resource")
    private static void sendMaidIdentity(Player player, EntityMaid maid) {
        if (player.level().isClientSide && FMLEnvironment.dist == Dist.CLIENT) {
            copyMaidUuid(player, maid.getUUID().toString());
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void copyMaidUuid(Player player, String maidUuid) {
        Minecraft.getInstance().keyboardHandler.setClipboard(maidUuid);
        player.sendSystemMessage(Component.literal("已复制女仆 UUID: " + maidUuid));
    }
}
