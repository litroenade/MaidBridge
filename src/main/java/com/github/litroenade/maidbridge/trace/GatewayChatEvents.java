package com.github.litroenade.maidbridge.trace;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GatewayChatEvents {
    private GatewayChatEvents() {
    }

    @SuppressWarnings("resource")
    public static void onServerChat(ServerChatEvent event) {
        if (!Config.enableGatewayChatCapture) {
            return;
        }
        // 公共聊天只在显式开启后进入桥接入口，避免默认污染女仆 AI 链路。
        var player = event.getPlayer();
        var componentText = event.getMessage().getString();
        var plainText = Config.gatewayChatUseRawText ? event.getRawText() : componentText;
        var dimension = player.serverLevel().dimension().location().toString();

        var payload = new LinkedHashMap<String, Object>();
        payload.put("plain_text", plainText);
        payload.put("raw_text", event.getRawText());
        payload.put("actor", Map.of(
                "id", player.getUUID().toString(),
                "name", event.getUsername()
        ));
        payload.put("room", Map.of(
                "id", Config.gatewayChatRoomId,
                "name", Config.gatewayChatRoomName
        ));
        payload.put("metadata", Map.of(
                "source", "minecraft_server_chat",
                "dimension", dimension,
                "component_text", componentText
        ));
        AiChainEventSink.emit(BridgeProtocol.TYPE_GATEWAY_MESSAGE, payload);
    }
}
