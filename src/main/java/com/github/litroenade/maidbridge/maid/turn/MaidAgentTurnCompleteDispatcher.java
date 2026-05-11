package com.github.litroenade.maidbridge.maid.turn;

import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.maid.action.MaidOperationQueue;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.github.litroenade.maidbridge.protocol.ResponseCallbacks;
import com.github.litroenade.maidbridge.protocol.frame.MaidAgentTurnComplete;
import com.github.litroenade.maidbridge.protocol.frame.MaidTurnIdentity;
import net.minecraft.server.MinecraftServer;

import java.util.Map;

public final class MaidAgentTurnCompleteDispatcher {
    private MaidAgentTurnCompleteDispatcher() {
    }

    public static void schedule(
            MinecraftServer server,
            MaidAgentTurnComplete complete,
            ResponseCallbacks.Success successSender,
            ResponseCallbacks.Failure failureSender
    ) {
        if (server == null) {
            failureSender.send(complete.id(), complete.traceId(), "Minecraft 服务器不可用");
            return;
        }
        MaidOperationQueue.INSTANCE.enqueue(
                server,
                MaidOperationQueue.key(complete.maidUuid()),
                () -> execute(server, complete, successSender, failureSender),
                exception -> failureSender.send(complete.id(), complete.traceId(), exception.getMessage())
        );
    }

    private static void execute(
            MinecraftServer server,
            MaidAgentTurnComplete complete,
            ResponseCallbacks.Success successSender,
            ResponseCallbacks.Failure failureSender
    ) {
        try {
            Map<String, Object> payload;
            if (complete.isReply()) {
                payload = MaidAgentTurnCompleteFacade.applyReply(server, complete);
            } else if (complete.isNoReply()) {
                payload = releaseNoReply(complete);
            } else {
                throw new IllegalArgumentException("不支持的女仆 agent 轮次 outcome=" + complete.outcome());
            }
            successSender.send(complete.id(), complete.traceId(), payload);
            MaidBridge.LOGGER.info(
                    "女仆 agent 轮次完成已处理 maidUuid={} turnId={} outcome={}",
                    complete.maidUuid(),
                    complete.turnId(),
                    complete.outcome()
            );
        } catch (RuntimeException exception) {
            MaidBridge.LOGGER.warn("女仆 agent 轮次完成处理失败 id={} turnId={}", complete.id(), complete.turnId(), exception);
            failureSender.send(complete.id(), complete.traceId(), exception.getMessage());
        }
    }

    private static Map<String, Object> releaseNoReply(MaidAgentTurnComplete complete) {
        var released = MaidExternalTurnGuard.releaseForIdentity(
                new MaidTurnIdentity(complete.maidUuid(), complete.turnId(), complete.id()),
                BridgeProtocol.TYPE_MAID_AGENT_TURN_OUTCOME_NO_REPLY,
                complete.reason()
        );
        if (released == null) {
            throw new IllegalArgumentException("没有待处理的外部女仆轮次 turn_id=" + complete.turnId());
        }
        var turn = released.turn();
        return Map.of(
                "routed", BridgeProtocol.TYPE_MAID_AGENT_TURN_COMPLETE,
                "outcome", BridgeProtocol.TYPE_MAID_AGENT_TURN_OUTCOME_NO_REPLY,
                "turn_id", turn.turnId(),
                "request_id", turn.requestId(),
                "reason", complete.reason()
        );
    }
}
