package com.github.litroenade.maidbridge.maid.api;

import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.maid.action.MaidOperationQueue;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.github.litroenade.maidbridge.protocol.ResponseCallbacks;
import com.github.litroenade.maidbridge.protocol.frame.MaidApiRequest;
import net.minecraft.server.MinecraftServer;

/**
 * 把外部 maid.api.* 请求切到 Minecraft 服务端线程。
 * <p>写操作按女仆 UUID 串行化，避免同一只女仆被并发修改状态。</p>
 */
public final class MaidApiRequestDispatcher {
    private MaidApiRequestDispatcher() {
    }

    public static void schedule(
            MinecraftServer server,
            MaidApiRequest request,
            ResponseCallbacks.Success successSender,
            ResponseCallbacks.Failure failureSender
    ) {
        if (server == null) {
            failureSender.send(request.id(), request.traceId(), "Minecraft 服务器不可用");
            return;
        }
        if (requiresMaidOperationQueue(request.type())) {
            MaidOperationQueue.INSTANCE.enqueue(
                    server,
                    MaidOperationQueue.key(request.maidUuid()),
                    () -> executeRequest(server, request, successSender, failureSender),
                    exception -> failureSender.send(request.id(), request.traceId(), exception.getMessage())
            );
            return;
        }
        try {
            server.execute(() -> executeRequest(server, request, successSender, failureSender));
        } catch (RuntimeException exception) {
            failureSender.send(request.id(), request.traceId(), exception.getMessage());
        }
    }

    private static boolean requiresMaidOperationQueue(String requestType) {
        return BridgeProtocol.TYPE_MAID_API_CALL_MAID_ACTION.equals(requestType)
                || BridgeProtocol.TYPE_MAID_API_CALL_MAID_TOOL.equals(requestType);
    }

    private static void executeRequest(
            MinecraftServer server,
            MaidApiRequest request,
            ResponseCallbacks.Success successSender,
            ResponseCallbacks.Failure failureSender
    ) {
        try {
            var payload = MaidApiFacade.execute(server, request);
            successSender.send(request.id(), request.traceId(), payload);
        } catch (RuntimeException exception) {
            MaidBridge.LOGGER.warn("女仆 API 请求失败 type={} id={}", request.type(), request.id(), exception);
            failureSender.send(request.id(), request.traceId(), exception.getMessage());
        }
    }
}
