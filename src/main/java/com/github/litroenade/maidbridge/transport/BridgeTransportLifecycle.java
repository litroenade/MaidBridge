package com.github.litroenade.maidbridge.transport;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.trace.AiChainEventSink;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidAIChatServerAccessState;
import com.github.litroenade.maidbridge.maid.api.MaidRegistryExporter;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class BridgeTransportLifecycle {
    private static final String DISABLE_AUTO_START_WS_PROPERTY = "maidbridge.disableAutoStartWs";

    private final BridgeTransport bridgeTransport;
    private BridgeServerSettings bridgeServerSettings = BridgeServerSettings.current();

    public BridgeTransportLifecycle(BridgeTransport bridgeTransport) {
        this.bridgeTransport = bridgeTransport;
    }

    public void register(IEventBus modEventBus) {
        modEventBus.addListener(this::onConfigReloading);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onServerStarted(ServerStartedEvent event) {
        AiChainEventSink.configure(Config.maxBufferedEvents);
        bridgeTransport.configure(Config.maxOutboundFrames);
        bridgeServerSettings = BridgeServerSettings.current();
        if (Boolean.getBoolean(DISABLE_AUTO_START_WS_PROPERTY)) {
            MaidBridge.LOGGER.info("MaidBridge WebSocket 自动启动已被系统属性关闭 property={}", DISABLE_AUTO_START_WS_PROPERTY);
        } else {
            bridgeTransport.start(event.getServer());
        }
        AiChainEventSink.setConsumer(bridgeTransport::publish);
        AiChainEventSink.emitLifecycle("maidbridge.server.started", event.getServer().getServerModName());
        MaidRegistryExporter.emitCatalogs();
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != Config.SPEC) {
            return;
        }
        Config.refreshFromSpec();
        AiChainEventSink.configure(Config.maxBufferedEvents);
        bridgeTransport.configure(Config.maxOutboundFrames);

        // 只有绑定参数变化才重启 WS，队列和诊断配置可以直接热更新。
        var nextSettings = BridgeServerSettings.current();
        if (bridgeTransport.hasServerContext() && !nextSettings.equals(bridgeServerSettings)) {
            try {
                bridgeTransport.restartCurrentServer();
                AiChainEventSink.setConsumer(bridgeTransport::publish);
                MaidBridge.LOGGER.info("MaidBridge WebSocket 已按配置重新启动 [启用={}, 端点=ws://{}:{}{}]",
                        Config.bridgeServerEnabled,
                        Config.bridgeServerHost,
                        Config.bridgeServerPort,
                        Config.bridgeServerPath);
            } catch (RuntimeException exception) {
                MaidBridge.LOGGER.error("从配置重启 MaidBridge WebSocket 传输层失败", exception);
            }
        }
        bridgeServerSettings = nextSettings;
    }

    private void onServerStopping(ServerStoppingEvent event) {
        AiChainEventSink.emitLifecycle("maidbridge.server.stopping", event.getServer().getServerModName());
        AiChainEventSink.setConsumer(null);
        MaidAIChatServerAccessState.clearAll();
        bridgeTransport.stop();
        AiChainEventSink.clear();
    }

    private record BridgeServerSettings(
            boolean enabled,
            String host,
            int port,
            String path,
            String accessToken,
            int maxMessageBytes
    ) {
        private static BridgeServerSettings current() {
            return new BridgeServerSettings(
                    Config.bridgeServerEnabled,
                    Config.bridgeServerHost,
                    Config.bridgeServerPort,
                    Config.bridgeServerPath,
                    Config.bridgeAccessToken,
                    Config.maxBridgeMessageBytes
            );
        }
    }
}
