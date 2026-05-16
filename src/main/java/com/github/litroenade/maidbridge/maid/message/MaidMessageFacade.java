package com.github.litroenade.maidbridge.maid.message;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.maid.ai.chat.MaidExternalAgentDisplayState;
import com.github.litroenade.maidbridge.maid.turn.MaidAgentTurnRequest;
import com.github.litroenade.maidbridge.maid.api.MaidApiReflection;
import com.github.litroenade.maidbridge.maid.api.MaidEntityLookup;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.github.litroenade.maidbridge.protocol.frame.MaidClientInfo;
import com.github.litroenade.maidbridge.protocol.frame.MaidMessageIn;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * 处理外部 maid.message.in 注入。
 * <p>未开启外部 agent 时，仍复用 TouhouLittleMaid 原生 ChatClientInfo 和 MaidAIChatManager.chat，
 * 让历史与语音链路由本体维护。
 */
public final class MaidMessageFacade {
    private static final String TOUHOU_LITTLE_MAID_MOD_ID = "touhou_little_maid";
    private static final String CHAT_CLIENT_INFO = "com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo";
    private static final String OWNER_ONLINE_POLICY = "owner_online";

    private MaidMessageFacade() {
    }

    public static Map<String, Object> inject(MinecraftServer server, MaidMessageIn message) {
        ensureMaidModLoaded();
        var maid = findMaid(server, message);
        if (maid == null) {
            throw new IllegalArgumentException("未找到女仆");
        }
        if (!maid.isAlive()) {
            throw new IllegalArgumentException("女仆不处于存活状态");
        }
        var maidUuid = maid.getUUID().toString();
        if (Config.isExternalMaidAgentMode() && MaidExternalAgentDisplayState.hasAgent(maid.getUUID())) {
            MaidAgentTurnRequest.emitInjectedTurn(maid, message.text(), message.clientInfo(), message.turnId(), message.id());
            return externalResponsePayload(
                    message,
                    maidUuid,
                    maid.getName().getString(),
                    System::currentTimeMillis
            );
        }
        var owner = invoke(maid, "getOwner");
        if (!(owner instanceof ServerPlayer sender)) {
            throw new IllegalArgumentException("女仆主人不是在线服务端玩家");
        }
        var clientInfo = newChatClientInfo(message.clientInfo());
        var chatManager = invoke(maid, "getAiChatManager");
        invokeChat(chatManager, message.text(), clientInfo, sender);
        return responsePayload(
                message,
                maidUuid,
                maid.getName().getString(),
                sender.getUUID().toString(),
                System::currentTimeMillis
        );
    }

    private static Map<String, Object> responsePayload(
            MaidMessageIn message,
            String maidUuid,
            String maidName,
            String ownerUuid,
            LongSupplier clock
    ) {
        var payload = baseResponsePayload(message, BridgeProtocol.TYPE_MAID_MESSAGE_IN, maidUuid, maidName, clock);
        putOwner(payload, ownerUuid);
        return payload;
    }

    private static Map<String, Object> externalResponsePayload(
            MaidMessageIn message,
            String maidUuid,
            String maidName,
            LongSupplier clock
    ) {
        var payload = baseResponsePayload(message, BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST, maidUuid, maidName, clock);
        payload.put("request_id", firstNonBlank(message.id(), message.turnId()));
        payload.put("native_ai_chat_cancelled", true);
        return payload;
    }

    private static Map<String, Object> baseResponsePayload(
            MaidMessageIn message,
            String routed,
            String maidUuid,
            String maidName,
            LongSupplier clock
    ) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("routed", routed);
        payload.put("accepted_at_ms", clock.getAsLong());
        putMaid(payload, maidUuid, maidName);
        payload.put("turn_id", firstNonBlank(message.turnId(), message.id()));
        putClient(payload, message.clientInfo());
        return payload;
    }

    private static void putMaid(Map<String, Object> payload, String maidUuid, String maidName) {
        var maid = new LinkedHashMap<String, Object>();
        maid.put("uuid", maidUuid);
        maid.put("name", firstNonBlank(maidName, ""));
        payload.put("maid", maid);
    }

    private static void putOwner(Map<String, Object> payload, String ownerUuid) {
        var normalizedOwnerUuid = firstNonBlank(ownerUuid, "");
        if (!normalizedOwnerUuid.isBlank()) {
            payload.put("owner", Map.of("uuid", normalizedOwnerUuid));
        }
    }

    private static void putClient(Map<String, Object> payload, MaidClientInfo clientInfo) {
        payload.put("client", clientInfo.toBridgePayload("en_us", "MaidBridge"));
    }

    static String disabledBridgeError(boolean enabled) {
        return enabled ? "" : "女仆消息桥接未启用";
    }

    static String injectionPolicyError(String policy) {
        var normalizedPolicy = policy == null ? "" : policy.trim();
        if (OWNER_ONLINE_POLICY.equals(normalizedPolicy)) {
            return "";
        }
        return "不支持的女仆注入策略：" + normalizedPolicy;
    }

    private static void ensureMaidModLoaded() {
        if (!ModList.get().isLoaded(TOUHOU_LITTLE_MAID_MOD_ID)) {
            throw new IllegalArgumentException("TouhouLittleMaid 未加载");
        }
    }

    private static Entity findMaid(MinecraftServer server, MaidMessageIn message) {
        if (message.maidUuid().isBlank()) {
            return null;
        }
        return MaidEntityLookup.findByUuid(server, UUID.fromString(message.maidUuid()));
    }

    private static Object newChatClientInfo(MaidClientInfo clientInfo) {
        try {
            Class<?> type = Class.forName(CHAT_CLIENT_INFO);
            var constructor = type.getConstructor(String.class, String.class, List.class);
            return constructor.newInstance(
                    firstNonBlank(clientInfo.language(), "en_us"),
                    firstNonBlank(clientInfo.name(), "MaidBridge"),
                    clientInfo.description()
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("构造 ChatClientInfo 失败", exception);
        }
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (IllegalAccessException | NoSuchMethodException exception) {
            throw new IllegalArgumentException("调用失败：" + methodName, exception);
        } catch (InvocationTargetException exception) {
            throw rethrowInvocation(methodName, exception);
        }
    }

    private static void invokeChat(Object chatManager, String text, Object clientInfo, ServerPlayer sender) {
        try {
            Class<?> clientInfoType = Class.forName(CHAT_CLIENT_INFO);
            MaidApiReflection.invokeExact(
                    chatManager,
                    "chat",
                    new Class<?>[]{String.class, clientInfoType, ServerPlayer.class},
                    text,
                    clientInfo,
                    sender
            );
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException("调用 MaidAIChatManager.chat 失败", exception);
        }
    }

    private static RuntimeException rethrowInvocation(String methodName, InvocationTargetException exception) {
        var cause = exception.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalArgumentException("调用失败：" + methodName, cause);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
