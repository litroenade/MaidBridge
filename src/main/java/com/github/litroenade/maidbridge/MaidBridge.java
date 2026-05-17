package com.github.litroenade.maidbridge;

import com.github.litroenade.maidbridge.command.MaidBridgeCommands;
import com.github.litroenade.maidbridge.item.MaidUuidProbeItem;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatEvents;
import com.github.litroenade.maidbridge.network.ExternalEmojiSyncEvents;
import com.github.litroenade.maidbridge.network.MaidBridgeNetwork;
import com.github.litroenade.maidbridge.registry.MaidBridgeItems;
import com.github.litroenade.maidbridge.trace.ServerChatEvents;
import com.github.litroenade.maidbridge.transport.BridgeTransport;
import com.github.litroenade.maidbridge.transport.BridgeTransportLifecycle;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(MaidBridge.MODID)
public final class MaidBridge {
    public static final String MODID = "maidbridge";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final int GUARD_SWEEP_TICK_INTERVAL = 20;

    private final BridgeTransport bridgeTransport = new BridgeTransport();
    private int guardSweepTickCounter;

    public MaidBridge(IEventBus modEventBus, ModContainer modContainer) {
        initRegister(modEventBus);
        registerConfiguration(modContainer);
        registerGameEvents();
        MaidBridgeNetwork.setWebSocketRestartHandler(bridgeTransport::restartCurrentServer);
        // WebSocket 启动依赖 MinecraftServer，交给服务端生命周期处理。
        new BridgeTransportLifecycle(bridgeTransport).register(modEventBus);
    }

    private void initRegister(IEventBus eventBus) {
        MaidBridgeItems.ITEMS.register(eventBus);
        eventBus.addListener(Config::onLoad);
        eventBus.addListener(MaidBridgeNetwork::register);
        eventBus.addListener(this::addCreativeItems);
    }

    private void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.TOOLS_AND_UTILITIES.equals(event.getTabKey())) {
            event.accept(MaidBridgeItems.MAID_UUID_PROBE.get());
        }
    }

    private static void registerConfiguration(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerGameEvents() {
        NeoForge.EVENT_BUS.addListener(MaidUuidProbeItem::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(ExternalEmojiSyncEvents::onTrackingPlayer);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(MaidAIChatEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerChatEvents::onServerChat);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        MaidBridgeCommands.register(event.getDispatcher(), bridgeTransport);
    }

    private void onServerTick(ServerTickEvent.Post ignored) {
        // 周期触发 pending turn 过期清理，避免无交互时 turn 在 guard 中滞留。
        if (++guardSweepTickCounter < GUARD_SWEEP_TICK_INTERVAL) {
            return;
        }
        guardSweepTickCounter = 0;
        bridgeTransport.sweepExpiredTurns();
    }
}
