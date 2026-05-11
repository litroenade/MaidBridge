package com.github.litroenade.maidbridge.registry;

import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.item.MaidUuidProbeItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MaidBridgeItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MaidBridge.MODID);

    public static final DeferredItem<Item> MAID_UUID_PROBE = ITEMS.register("maid_uuid_probe", MaidUuidProbeItem::create);

    private MaidBridgeItems() {
    }
}
