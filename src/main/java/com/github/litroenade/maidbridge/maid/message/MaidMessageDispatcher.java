package com.github.litroenade.maidbridge.maid.message;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.maid.action.MaidOperationQueue;
import com.github.litroenade.maidbridge.protocol.ResponseCallbacks;
import com.github.litroenade.maidbridge.protocol.frame.MaidMessageIn;
import net.minecraft.server.MinecraftServer;

public final class MaidMessageDispatcher {
    private MaidMessageDispatcher() {
    }

    public static void schedule(
            MinecraftServer server,
            MaidMessageIn message,
            ResponseCallbacks.Success successSender,
            ResponseCallbacks.Failure failureSender
    ) {
        var bridgeError = MaidMessageFacade.disabledBridgeError(Config.enableMaidMessageBridge);
        if (!bridgeError.isBlank()) {
            failureSender.send(message.id(), message.traceId(), bridgeError);
            return;
        }
        var policyError = MaidMessageFacade.injectionPolicyError(Config.maidInjectionPolicy);
        if (!policyError.isBlank()) {
            failureSender.send(message.id(), message.traceId(), policyError);
            return;
        }
        if (server == null) {
            failureSender.send(message.id(), message.traceId(), "Minecraft 服务器不可用");
            return;
        }
        MaidOperationQueue.INSTANCE.enqueue(
                server,
                MaidOperationQueue.key(message.maidUuid()),
                () -> {
                    try {
                        var payload = MaidMessageFacade.inject(server, message);
                        successSender.send(message.id(), message.traceId(), payload);
                    } catch (RuntimeException exception) {
                        MaidBridge.LOGGER.warn("女仆消息注入失败 id={}", message.id(), exception);
                        failureSender.send(message.id(), message.traceId(), exception.getMessage());
                    }
                },
                exception -> failureSender.send(message.id(), message.traceId(), exception.getMessage())
        );
    }
}
