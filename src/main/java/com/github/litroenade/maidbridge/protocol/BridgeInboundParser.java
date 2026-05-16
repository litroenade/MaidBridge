package com.github.litroenade.maidbridge.protocol;

import com.github.litroenade.maidbridge.protocol.frame.BridgeFrameIdentity;
import com.github.litroenade.maidbridge.protocol.frame.BridgeFrameRouting;
import com.github.litroenade.maidbridge.protocol.frame.BridgeGatewayMessage;
import com.github.litroenade.maidbridge.protocol.frame.BridgeSessionInitialize;
import com.github.litroenade.maidbridge.protocol.frame.MaidAgentTurnComplete;
import com.github.litroenade.maidbridge.protocol.frame.MaidApiRequest;
import com.github.litroenade.maidbridge.protocol.frame.MaidClientInfo;
import com.github.litroenade.maidbridge.protocol.frame.MaidMessageIn;
import com.github.litroenade.maidbridge.protocol.frame.MaidTurnIdentity;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import static com.github.litroenade.maidbridge.protocol.BridgeJson.firstNonBlank;
import static com.github.litroenade.maidbridge.protocol.BridgeJson.javaValue;
import static com.github.litroenade.maidbridge.protocol.BridgeJson.objectMap;
import static com.github.litroenade.maidbridge.protocol.BridgeJson.optionalIdString;
import static com.github.litroenade.maidbridge.protocol.BridgeJson.optionalObjectMap;
import static com.github.litroenade.maidbridge.protocol.BridgeJson.optionalString;
import static com.github.litroenade.maidbridge.protocol.BridgeJson.optionalStringList;
import static com.github.litroenade.maidbridge.protocol.BridgeJson.parseRoot;
import static com.github.litroenade.maidbridge.protocol.BridgeJson.requiredObject;
import static com.github.litroenade.maidbridge.protocol.BridgeJson.requiredString;

/**
 * 解析外部客户端发回的 MaidBridge 帧。
 * <p>这里只做协议外壳校验和 JSON 到内部模型转换；是否改动游戏状态由后续分发器决定。</p>
 */
public final class BridgeInboundParser {
    private BridgeInboundParser() {
    }

    public static String peekType(String rawFrame, int maxBytes) {
        return requiredString(parseRoot(rawFrame, maxBytes, false), "type");
    }

    public static BridgeFrameIdentity parseFrameIdentity(String rawFrame, int maxBytes) {
        var json = parseRoot(rawFrame, maxBytes, false);
        return new BridgeFrameIdentity(
                firstNonBlank(optionalString(json, "id"), optionalString(json, "turn_id")),
                optionalString(json, "trace_id")
        );
    }

    public static MaidTurnIdentity parseMaidTurnIdentityLenient(String rawFrame, int maxBytes) {
        try {
            var json = parseRoot(rawFrame, maxBytes, false);
            var payload = optionalPayload(json);
            return new MaidTurnIdentity(
                    firstNonBlank(optionalMaidUuid(json), optionalMaidUuid(payload)),
                    firstNonBlank(optionalString(payload, "turn_id"), optionalString(json, "turn_id")),
                    firstNonBlank(optionalString(json, "request_id"), optionalString(payload, "request_id"), optionalString(json, "id"))
            );
        } catch (RuntimeException exception) {
            return new MaidTurnIdentity("", "", "");
        }
    }

    public static BridgeFrameRouting parseFrameRoutingLenient(String rawFrame, int maxBytes) {
        try {
            var json = parseRoot(rawFrame, maxBytes, false);
            var payload = optionalPayload(json);
            var turnId = firstNonBlank(optionalString(payload, "turn_id"), optionalString(json, "turn_id"));
            return new BridgeFrameRouting(
                    optionalString(json, "type"),
                    firstNonBlank(optionalString(json, "request_id"), optionalString(payload, "request_id"), turnId),
                    firstNonBlank(optionalMaidUuid(json), optionalMaidUuid(payload)),
                    turnId
            );
        } catch (RuntimeException exception) {
            return new BridgeFrameRouting("", "", "", "");
        }
    }

    public static BridgeSessionInitialize parseSessionInitialize(String rawFrame, int maxBytes) {
        var json = parseRoot(rawFrame, maxBytes);
        var type = requiredString(json, "type");
        if (!BridgeProtocol.isSessionInitializeType(type)) {
            throw new IllegalArgumentException("不支持的 MaidBridge 会话初始化帧类型：" + type);
        }
        requireClientToJavaDirection(json, type);
        var payload = requiredObject(json, "payload");
        var agent = optionalAgentObject(payload);
        return new BridgeSessionInitialize(
                requiredString(json, "id"),
                optionalString(json, "trace_id"),
                firstNonBlank(optionalString(agent, "name"), optionalString(payload, "client_name"), optionalString(payload, "name"), optionalString(payload, "client_id"), optionalString(json, "source_endpoint")),
                firstNonBlank(optionalString(agent, "id"), optionalString(payload, "agent_id"), optionalString(payload, "agentId"), optionalString(payload, "client_id"), optionalString(payload, "client_name"), optionalString(payload, "name")),
                optionalStringList(payload, "roles"),
                optionalStringList(payload, "subscriptions")
        );
    }

    public static BridgeGatewayMessage parseGatewayMessage(String rawFrame, int maxBytes, int maxTextCharacters) {
        var json = parseRoot(rawFrame, maxBytes);
        var type = requiredString(json, "type");
        if (!BridgeProtocol.TYPE_GATEWAY_MESSAGE.equals(type)) {
            throw new IllegalArgumentException("不支持的 MaidBridge 网关帧类型：" + type);
        }
        requireClientToJavaDirection(json, BridgeProtocol.TYPE_GATEWAY_MESSAGE);
        var payload = requiredObject(json, "payload");
        var message = requiredObject(payload, "message");
        var plainText = requiredString(message, "processed_plain_text");
        if (plainText.length() > Math.max(1, maxTextCharacters)) {
            throw new IllegalArgumentException("processed_plain_text 超过 maxInboundGatewayTextCharacters");
        }
        var endpointId = optionalString(json, "endpoint_id");
        var routeScope = firstNonBlank(optionalRouteScope(payload), endpointId);
        if (!isSupportedGatewayRouteScope(routeScope)) {
            throw new IllegalArgumentException("MaidBridge Java 不支持 bridge.gateway.message 的 route scope");
        }
        return new BridgeGatewayMessage(
                requiredString(json, "id"),
                optionalString(json, "trace_id"),
                endpointId,
                routeScope,
                plainText,
                optionalString(json, "source_endpoint"),
                optionalString(json, "target_endpoint"),
                optionalObjectMap(payload, "route"),
                optionalObjectMap(payload, "target"),
                optionalObjectMap(payload, "metadata")
        );
    }

    public static MaidApiRequest parseMaidApiRequest(String rawFrame, int maxBytes) {
        var json = parseRoot(rawFrame, maxBytes);
        var type = requiredString(json, "type");
        if (!BridgeProtocol.isSupportedMaidApiType(type)) {
            throw new IllegalArgumentException("不支持的 Maid API 帧类型：" + type);
        }
        requireClientToJavaDirection(json, "maid.api request");
        return new MaidApiRequest(
                type,
                requiredString(json, "id"),
                optionalString(json, "trace_id"),
                optionalString(json, "source_endpoint"),
                optionalString(json, "target_endpoint"),
                optionalObjectMap(json, "payload")
        );
    }

    public static MaidMessageIn parseMaidMessageIn(String rawFrame, int maxBytes, int maxTextCharacters) {
        var json = parseRoot(rawFrame, maxBytes);
        var type = requiredString(json, "type");
        if (!BridgeProtocol.TYPE_MAID_MESSAGE_IN.equals(type)) {
            throw new IllegalArgumentException("不支持的 Maid message 帧类型：" + type);
        }
        requireClientToJavaDirection(json, BridgeProtocol.TYPE_MAID_MESSAGE_IN);
        var payload = requiredObject(json, "payload");
        var text = optionalString(payload, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("payload.text 必须是非空字符串");
        }
        if (text.length() > Math.max(1, maxTextCharacters)) {
            throw new IllegalArgumentException("payload.text 超过 maxInboundGatewayTextCharacters");
        }
        var maidUuid = optionalMaidUuid(payload);
        if (maidUuid.isBlank()) {
            throw new IllegalArgumentException(BridgeProtocol.ERROR_MISSING_MAID_UUID);
        }
        var id = requiredString(json, "id");
        var requestId = firstNonBlank(optionalString(json, "request_id"), id);
        var turnId = firstNonBlank(optionalString(payload, "turn_id"), optionalString(json, "turn_id"), requestId);
        return new MaidMessageIn(
                id,
                optionalString(json, "trace_id"),
                maidUuid,
                text,
                optionalClientInfo(payload),
                turnId
        );
    }

    public static MaidAgentTurnComplete parseMaidAgentTurnComplete(String rawFrame, int maxBytes, int maxTextCharacters) {
        var json = parseRoot(rawFrame, maxBytes, false);
        var type = requiredString(json, "type");
        if (!BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE.equals(type)) {
            throw new IllegalArgumentException("不支持的 Maid agent turn complete 帧类型：" + type);
        }
        return parseCompleteFrame(json, maxTextCharacters);
    }

    private static MaidAgentTurnComplete parseCompleteFrame(JsonObject json, int maxTextCharacters) {
        requireProtocol(json);
        requireClientToJavaDirection(json, BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE);
        var payload = requiredObject(json, "payload");
        var maidUuid = optionalMaidUuid(payload);
        if (maidUuid.isBlank()) {
            throw new IllegalArgumentException(BridgeProtocol.ERROR_MISSING_MAID_UUID);
        }
        var turnId = firstNonBlank(optionalString(payload, "turn_id"), optionalString(json, "turn_id"));
        if (turnId.isBlank()) {
            throw new IllegalArgumentException("payload.turn_id 必须是非空字符串");
        }
        var outcome = requiredString(payload, "outcome");
        if (BridgeProtocol.TYPE_MAID_AGENT_TURN_OUTCOME_REPLY.equals(outcome)) {
            return parseReplyComplete(json, payload, maidUuid, turnId, maxTextCharacters);
        }
        if (BridgeProtocol.TYPE_MAID_AGENT_TURN_OUTCOME_NO_REPLY.equals(outcome)) {
            var agent = optionalAgentObject(payload);
            return new MaidAgentTurnComplete(
                    requiredString(json, "id"),
                    optionalString(json, "trace_id"),
                    turnId,
                    maidUuid,
                    outcome,
                    "",
                    "",
                    optionalHistoryPolicy(payload),
                    optionalActionsList(payload),
                    optionalString(agent, "id"),
                    optionalString(agent, "name"),
                    optionalString(payload, "reason")
            );
        }
        throw new IllegalArgumentException("不支持的 maid.agent.turn.complete outcome：" + outcome);
    }

    private static MaidAgentTurnComplete parseReplyComplete(
            JsonObject json,
            JsonObject payload,
            String maidUuid,
            String turnId,
            int maxTextCharacters
    ) {
        var reply = requiredObject(payload, "reply");
        var agent = optionalAgentObject(payload);
        var chatText = requiredReplyText(reply);
        if (chatText.length() > Math.max(1, maxTextCharacters)) {
            throw new IllegalArgumentException("reply.text 超过 maxInboundGatewayTextCharacters");
        }
        var ttsText = optionalString(reply, "tts_text");
        if (ttsText.length() > Math.max(1, maxTextCharacters)) {
            throw new IllegalArgumentException("reply.tts_text 超过 maxInboundGatewayTextCharacters");
        }
        return new MaidAgentTurnComplete(
                requiredString(json, "id"),
                optionalString(json, "trace_id"),
                turnId,
                maidUuid,
                BridgeProtocol.TYPE_MAID_AGENT_TURN_OUTCOME_REPLY,
                chatText,
                ttsText,
                optionalHistoryPolicy(payload),
                optionalActionsList(payload),
                optionalString(agent, "id"),
                optionalString(agent, "name"),
                optionalString(payload, "reason")
        );
    }

    private static String optionalHistoryPolicy(JsonObject json) {
        if (!json.has("history") || json.get("history").isJsonNull()) {
            return "";
        }
        if (!json.get("history").isJsonObject()) {
            throw new IllegalArgumentException("history 必须是对象");
        }
        return optionalString(json.getAsJsonObject("history"), "policy");
    }

    private static String optionalRouteScope(JsonObject payload) {
        if (!payload.has("route") || !payload.get("route").isJsonObject()) {
            return "";
        }
        return optionalString(payload.getAsJsonObject("route"), "scope");
    }

    private static JsonObject optionalPayload(JsonObject json) {
        if (!json.has("payload") || json.get("payload").isJsonNull()) {
            return new JsonObject();
        }
        if (!json.get("payload").isJsonObject()) {
            throw new IllegalArgumentException("payload 必须是对象");
        }
        return json.getAsJsonObject("payload");
    }

    private static JsonObject optionalAgentObject(JsonObject payload) {
        if (!payload.has("agent") || payload.get("agent").isJsonNull()) {
            return new JsonObject();
        }
        if (!payload.get("agent").isJsonObject()) {
            throw new IllegalArgumentException("payload.agent 必须是对象");
        }
        return payload.getAsJsonObject("agent");
    }

    private static String optionalMaidUuid(JsonObject payload) {
        if (!payload.has("maid") || !payload.get("maid").isJsonObject()) {
            return "";
        }
        return optionalString(payload.getAsJsonObject("maid"), "uuid");
    }

    private static List<Object> optionalActionsList(JsonObject payload) {
        if (!payload.has("actions") || payload.get("actions").isJsonNull()) {
            return List.of();
        }
        if (!payload.get("actions").isJsonArray()) {
            throw new IllegalArgumentException("actions 必须是数组");
        }
        var list = new ArrayList<>();
        for (var item : payload.getAsJsonArray("actions")) {
            list.add(javaValue(item));
        }
        return List.copyOf(list);
    }

    private static MaidClientInfo optionalClientInfo(JsonObject payload) {
        if (!payload.has("client_info") || payload.get("client_info").isJsonNull()) {
            return new MaidClientInfo("", "", List.of());
        }
        if (!payload.get("client_info").isJsonObject()) {
            throw new IllegalArgumentException("payload.client_info 必须是对象");
        }
        var clientInfo = payload.getAsJsonObject("client_info");
        return new MaidClientInfo(
                optionalString(clientInfo, "language"),
                optionalString(clientInfo, "name"),
                optionalDescription(clientInfo),
                optionalIdString(clientInfo, "room_id"),
                optionalIdString(clientInfo, "source_member_id"),
                optionalIdString(clientInfo, "channel_id"),
                objectMap(clientInfo)
        );
    }

    private static List<String> optionalDescription(JsonObject clientInfo) {
        if (!clientInfo.has("description") || clientInfo.get("description").isJsonNull()) {
            return List.of();
        }
        var value = clientInfo.get("description");
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            var text = value.getAsString().trim();
            return text.isBlank() ? List.of() : List.of(text);
        }
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("payload.client_info.description 必须是字符串或字符串数组");
        }
        var description = new ArrayList<String>();
        for (var item : value.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("payload.client_info.description 必须是字符串或字符串数组");
            }
            var text = item.getAsString().trim();
            if (!text.isBlank()) {
                description.add(text);
            }
        }
        return List.copyOf(description);
    }

    private static boolean isSupportedGatewayRouteScope(String routeScope) {
        return routeScope.isBlank()
                || routeScope.equals("server")
                || routeScope.equals("broadcast")
                || routeScope.startsWith("server:");
    }

    private static void requireProtocol(JsonObject json) {
        var protocol = requiredString(json, "protocol");
        if (!BridgeProtocol.PROTOCOL.equals(protocol)) {
            throw new IllegalArgumentException("不支持的 MaidBridge 协议：" + protocol);
        }
    }

    private static void requireClientToJavaDirection(JsonObject json, String type) {
        var direction = requiredString(json, "direction");
        if (!BridgeProtocol.DIRECTION_CLIENT_TO_JAVA.equals(direction)) {
            throw new IllegalArgumentException(type + " 的 direction 必须是 client_to_java");
        }
    }

    private static String requiredReplyText(JsonObject reply) {
        var text = optionalString(reply, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("reply.text 必须是非空字符串");
        }
        return text;
    }

}
