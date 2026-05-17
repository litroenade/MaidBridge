package com.github.litroenade.maidbridge.trace;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ServerChatEvents {
    private ServerChatEvents() {
    }

    @SuppressWarnings("resource")
    public static void onServerChat(ServerChatEvent event) {
        if (!Config.enableServerChatBridge) {
            return;
        }
        // 公共聊天是服务器级群聊，不依赖任何女仆实体是否处于已加载区块。
        var player = event.getPlayer();
        var componentText = event.getMessage().getString();
        var plainText = Config.serverChatUseRawText ? event.getRawText() : componentText;
        var dimension = player.serverLevel().dimension().location().toString();

        var payload = new LinkedHashMap<String, Object>();
        payload.put("message", Map.of(
                "kind", "member",
                "text", plainText,
                "raw_text", event.getRawText()
        ));
        payload.put("speaker", Map.of(
                "id", player.getUUID().toString(),
                "name", event.getUsername()
        ));
        payload.put("room", Map.of(
                "id", Config.serverChatRoomId,
                "name", Config.serverChatRoomName
        ));
        payload.put("metadata", Map.of(
                "source", "minecraft_server_chat",
                "dimension", dimension,
                "component_text", componentText
        ));
        AiChainEventSink.emit(BridgeProtocol.TYPE_SERVER_CHAT_MESSAGE, payload);
    }
}
