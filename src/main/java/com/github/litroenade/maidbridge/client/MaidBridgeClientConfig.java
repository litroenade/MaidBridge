package com.github.litroenade.maidbridge.client;

import com.github.litroenade.maidbridge.Config;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class MaidBridgeClientConfig {
    private MaidBridgeClientConfig() {
    }

    public static void appendAsTouhouCategory(ConfigBuilder root, ConfigEntryBuilder entries) {
        Config.refreshFromSpec();
        ConfigCategory category = root.getOrCreateCategory(Component.translatable("config.maidbridge.title"));
        category.addEntry(subCategory(entries, Component.translatable("config.maidbridge.category.connection"), sink -> connectionEntries(sink, entries)));
        category.addEntry(subCategory(entries, Component.translatable("config.maidbridge.category.maid_link"), sink -> maidLinkEntries(sink, entries)));
        category.addEntry(subCategory(entries, Component.translatable("config.maidbridge.category.gateway_chat"), sink -> gatewayChatEntries(sink, entries)));
        category.addEntry(subCategory(entries, Component.translatable("config.maidbridge.category.debug"), sink -> debugEntries(sink, entries)));
    }

    private static AbstractConfigListEntry<?> subCategory(ConfigEntryBuilder entries, Component title, Consumer<ConfigEntrySink> consumer) {
        SubCategoryBuilder builder = entries.startSubCategory(title);
        builder.setExpanded(false);
        consumer.accept(builder::add);
        return builder.build();
    }

    private interface ConfigEntrySink {
        void add(AbstractConfigListEntry<?> entry);
    }

    private static void connectionEntries(ConfigEntrySink sink, ConfigEntryBuilder entries) {
        sink.add(entries.startBooleanToggle(Component.translatable("config.maidbridge.bridge_server_enabled"), Config.bridgeServerEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.maidbridge.bridge_server_enabled.tooltip"))
                .setSaveConsumer(Config::setBridgeServerEnabled)
                .build());
        sink.add(entries.startStrField(Component.translatable("config.maidbridge.websocket_host"), Config.bridgeServerHost)
                .setDefaultValue(Config.DEFAULT_BRIDGE_SERVER_HOST)
                .setTooltip(Component.translatable("config.maidbridge.websocket_host.tooltip"))
                .setSaveConsumer(Config::setBridgeServerHost)
                .build());
        sink.add(entries.startIntField(Component.translatable("config.maidbridge.websocket_port"), Config.bridgeServerPort)
                .setDefaultValue(Config.DEFAULT_BRIDGE_SERVER_PORT)
                .setMin(1)
                .setMax(65535)
                .setTooltip(Component.translatable("config.maidbridge.websocket_port.tooltip"))
                .setSaveConsumer(Config::setBridgeServerPort)
                .build());
        sink.add(entries.startStrField(Component.translatable("config.maidbridge.websocket_path"), Config.bridgeServerPath)
                .setDefaultValue(Config.DEFAULT_BRIDGE_SERVER_PATH)
                .setTooltip(Component.translatable("config.maidbridge.websocket_path.tooltip"))
                .setSaveConsumer(Config::setBridgeServerPath)
                .build());
    }

    private static void maidLinkEntries(ConfigEntrySink sink, ConfigEntryBuilder entries) {
        sink.add(entries.startEnumSelector(
                        Component.translatable("config.maidbridge.maid_bridge_mode"),
                        MaidBridgeMode.class,
                        Config.isExternalMaidAgentMode() ? MaidBridgeMode.EXTERNAL_AGENT : MaidBridgeMode.MESSAGE_BRIDGE
                )
                .setDefaultValue(MaidBridgeMode.MESSAGE_BRIDGE)
                .setTooltip(Component.translatable("config.maidbridge.maid_bridge_mode.tooltip"))
                .setEnumNameProvider(value -> Component.translatable(((MaidBridgeMode) value).translationKey()))
                .setSaveConsumer(value -> {
                    Config.setEnableMaidMessageBridge(value == MaidBridgeMode.MESSAGE_BRIDGE);
                    Config.setEnableExternalMaidAgentTurns(value == MaidBridgeMode.EXTERNAL_AGENT);
                })
                .build());
        sink.add(entries.startBooleanToggle(Component.translatable("config.maidbridge.enable_multiplayer_maid_chat"), Config.enableMultiplayerMaidChat)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.maidbridge.enable_multiplayer_maid_chat.tooltip"))
                .setSaveConsumer(Config::setEnableMultiplayerMaidChat)
                .build());
        sink.add(entries.startBooleanToggle(Component.translatable("config.maidbridge.enable_external_agent_emoji"), Config.enableExternalAgentEmoji)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.maidbridge.enable_external_agent_emoji.tooltip"))
                .setSaveConsumer(Config::setEnableExternalAgentEmoji)
                .build());
    }

    private static void gatewayChatEntries(ConfigEntrySink sink, ConfigEntryBuilder entries) {
        sink.add(entries.startBooleanToggle(Component.translatable("config.maidbridge.enable_gateway_chat_capture"), Config.enableGatewayChatCapture)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.maidbridge.enable_gateway_chat_capture.tooltip"))
                .setSaveConsumer(Config::setEnableGatewayChatCapture)
                .build());
        sink.add(entries.startBooleanToggle(Component.translatable("config.maidbridge.enable_inbound_gateway_messages"), Config.enableInboundGatewayMessages)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.maidbridge.enable_inbound_gateway_messages.tooltip"))
                .setSaveConsumer(Config::setEnableInboundGatewayMessages)
                .build());
        sink.add(entries.startStrField(Component.translatable("config.maidbridge.gateway_chat_room_name"), Config.gatewayChatRoomName)
                .setDefaultValue(Config.DEFAULT_GATEWAY_CHAT_ROOM_NAME)
                .setTooltip(Component.translatable("config.maidbridge.gateway_chat_room_name.tooltip"))
                .setSaveConsumer(Config::setGatewayChatRoomName)
                .build());
        sink.add(entries.startStrField(Component.translatable("config.maidbridge.inbound_gateway_message_prefix"), Config.inboundGatewayMessagePrefix)
                .setDefaultValue(Config.DEFAULT_INBOUND_GATEWAY_MESSAGE_PREFIX)
                .setTooltip(Component.translatable("config.maidbridge.inbound_gateway_message_prefix.tooltip"))
                .setSaveConsumer(Config::setInboundGatewayMessagePrefix)
                .build());
        sink.add(entries.startIntField(Component.translatable("config.maidbridge.max_inbound_gateway_text_characters"), Config.maxInboundGatewayTextCharacters)
                .setDefaultValue(1024)
                .setMin(1)
                .setMax(8192)
                .setTooltip(Component.translatable("config.maidbridge.max_inbound_gateway_text_characters.tooltip"))
                .setSaveConsumer(Config::setMaxInboundGatewayTextCharacters)
                .build());
    }

    private static void debugEntries(ConfigEntrySink sink, ConfigEntryBuilder entries) {
        sink.add(entries.startBooleanToggle(Component.translatable("config.maidbridge.enable_ai_chain_capture"), Config.enableAiChainCapture)
                .setDefaultValue(false)
                .setSaveConsumer(Config::setEnableAiChainCapture)
                .build());
        sink.add(entries.startBooleanToggle(Component.translatable("config.maidbridge.capture_raw_llm_request_bodies"), Config.captureRawLlmRequestBodies)
                .setDefaultValue(false)
                .setSaveConsumer(Config::setCaptureRawLlmRequestBodies)
                .build());
        sink.add(entries.startIntField(Component.translatable("config.maidbridge.max_raw_llm_request_characters"), Config.maxRawLlmRequestCharacters)
                .setDefaultValue(4096)
                .setMin(0)
                .setMax(65536)
                .setSaveConsumer(Config::setMaxRawLlmRequestCharacters)
                .build());
        sink.add(entries.startBooleanToggle(Component.translatable("config.maidbridge.log_captured_events"), Config.logCapturedEvents)
                .setDefaultValue(false)
                .setSaveConsumer(Config::setLogCapturedEvents)
                .build());
        sink.add(entries.startStrField(Component.translatable("config.maidbridge.websocket_access_token"), Config.bridgeAccessToken)
                .setDefaultValue("")
                .setTooltip(Component.translatable("config.maidbridge.websocket_access_token.tooltip"))
                .setSaveConsumer(Config::setBridgeAccessToken)
                .build());
        sink.add(entries.startIntField(Component.translatable("config.maidbridge.bridge_deadline_ms"), Config.bridgeDeadlineMs)
                .setDefaultValue(30000)
                .setMin(1000)
                .setMax(300000)
                .setSaveConsumer(Config::setBridgeDeadlineMs)
                .build());
        sink.add(entries.startIntField(Component.translatable("config.maidbridge.max_bridge_message_bytes"), Config.maxBridgeMessageBytes)
                .setDefaultValue(32768)
                .setMin(1024)
                .setMax(1048576)
                .setSaveConsumer(Config::setMaxBridgeMessageBytes)
                .build());
        sink.add(entries.startIntField(Component.translatable("config.maidbridge.max_outbound_frames"), Config.maxOutboundFrames)
                .setDefaultValue(512)
                .setMin(32)
                .setMax(8192)
                .setSaveConsumer(Config::setMaxOutboundFrames)
                .build());
        sink.add(entries.startIntField(Component.translatable("config.maidbridge.max_buffered_events"), Config.maxBufferedEvents)
                .setDefaultValue(512)
                .setMin(32)
                .setMax(8192)
                .setSaveConsumer(Config::setMaxBufferedEvents)
                .build());
        sink.add(entries.startIntField(Component.translatable("config.maidbridge.maid_external_turn_ttl_ms"), Config.maidExternalTurnTtlMs)
                .setDefaultValue(120000)
                .setMin(1000)
                .setMax(1800000)
                .setSaveConsumer(Config::setMaidExternalTurnTtlMs)
                .build());
        sink.add(entries.startStrField(Component.translatable("config.maidbridge.maid_injection_policy"), Config.maidInjectionPolicy)
                .setDefaultValue(Config.DEFAULT_MAID_INJECTION_POLICY)
                .setSaveConsumer(Config::setMaidInjectionPolicy)
                .build());
        sink.add(entries.startBooleanToggle(Component.translatable("config.maidbridge.enable_maid_api_exposure"), Config.enableMaidApiExposure)
                .setDefaultValue(false)
                .setSaveConsumer(Config::setEnableMaidApiExposure)
                .build());
        sink.add(entries.startBooleanToggle(Component.translatable("config.maidbridge.enable_maid_api_actions"), Config.enableMaidApiActions)
                .setDefaultValue(false)
                .setSaveConsumer(Config::setEnableMaidApiActions)
                .build());
        sink.add(entries.startIntField(Component.translatable("config.maidbridge.max_pending_maid_operations_per_key"), Config.maxPendingMaidOperationsPerKey)
                .setDefaultValue(64)
                .setMin(1)
                .setMax(1024)
                .setSaveConsumer(Config::setMaxPendingMaidOperationsPerKey)
                .build());
        sink.add(entries.startStrField(Component.translatable("config.maidbridge.gateway_chat_room_id"), Config.gatewayChatRoomId)
                .setDefaultValue(Config.DEFAULT_GATEWAY_CHAT_ROOM_ID)
                .setSaveConsumer(Config::setGatewayChatRoomId)
                .build());
        sink.add(entries.startBooleanToggle(Component.translatable("config.maidbridge.gateway_chat_use_raw_text"), Config.gatewayChatUseRawText)
                .setDefaultValue(false)
                .setSaveConsumer(Config::setGatewayChatUseRawText)
                .build());
        sink.add(entries.startIntField(Component.translatable("config.maidbridge.max_pending_inbound_gateway_messages"), Config.maxPendingInboundGatewayMessages)
                .setDefaultValue(64)
                .setMin(1)
                .setMax(1024)
                .setSaveConsumer(Config::setMaxPendingInboundGatewayMessages)
                .build());
        sink.add(entries.startStrField(Component.translatable("config.maidbridge.source_endpoint"), Config.sourceEndpoint)
                .setDefaultValue(Config.DEFAULT_SOURCE_ENDPOINT)
                .setSaveConsumer(Config::setSourceEndpoint)
                .build());
        sink.add(entries.startStrField(Component.translatable("config.maidbridge.target_endpoint"), Config.targetEndpoint)
                .setDefaultValue(Config.DEFAULT_TARGET_ENDPOINT)
                .setSaveConsumer(Config::setTargetEndpoint)
                .build());
    }
}
