package com.github.litroenade.maidbridge.transport;

import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.maid.turn.MaidExternalTurnGuard;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.github.litroenade.maidbridge.protocol.frame.MaidTurnIdentity;

import java.util.Map;

final class PendingMaidTurnReleaser {
    static final String OUTCOME_DISCONNECT = BridgeProtocol.TYPE_MAID_AGENT_TURN_OUTCOME_DISCONNECT;

    private PendingMaidTurnReleaser() {
    }

    static MaidTurnIdentity fromPayload(Map<String, Object> payload) {
        var maidUuid = "";
        var maid = payload.get("maid");
        if (maid instanceof Map<?, ?> maidMap) {
            maidUuid = safeString(maidMap.get("uuid"));
        }
        return new MaidTurnIdentity(maidUuid, safeString(payload.get("turn_id")), safeString(payload.get("request_id")));
    }

    static MaidExternalTurnGuard.CompletedTurn releaseDroppedIdentity(MaidTurnIdentity identity, String reason) {
        var released = MaidExternalTurnGuard.releaseForIdentity(
                identity,
                BridgeProtocol.TYPE_MAID_AGENT_TURN_OUTCOME_DROP,
                reason
        );
        if (released != null) {
            logReleased(released);
        }
        return released;
    }

    static void logReleased(MaidExternalTurnGuard.CompletedTurn released) {
        var turn = released.turn();
        MaidBridge.LOGGER.warn(
                "已释放待处理外部女仆轮次 outcome={} reason={} maidUuid={} requestId={} turnId={}",
                released.outcome(),
                released.reason(),
                turn.maidUuid(),
                turn.requestId(),
                turn.turnId()
        );
    }

    private static String safeString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
