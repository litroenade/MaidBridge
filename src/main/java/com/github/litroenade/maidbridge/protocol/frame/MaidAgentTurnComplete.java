package com.github.litroenade.maidbridge.protocol.frame;

import com.github.litroenade.maidbridge.protocol.BridgeProtocol;

import java.util.List;

public record MaidAgentTurnComplete(
        String id,
        String traceId,
        String turnId,
        String maidUuid,
        String outcome,
        String chatText,
        String ttsText,
        String historyPolicy,
        List<Object> actions,
        String reason
) {
    public MaidAgentTurnComplete {
        id = safeString(id);
        traceId = safeString(traceId);
        turnId = safeString(turnId);
        maidUuid = safeString(maidUuid);
        outcome = safeString(outcome);
        chatText = safeString(chatText);
        ttsText = safeString(ttsText);
        historyPolicy = safeString(historyPolicy);
        actions = List.copyOf(actions == null ? List.of() : actions);
        reason = safeString(reason);
    }

    public boolean isReply() {
        return BridgeProtocol.TYPE_MAID_AGENT_TURN_OUTCOME_REPLY.equals(outcome);
    }

    public boolean isNoReply() {
        return BridgeProtocol.TYPE_MAID_AGENT_TURN_OUTCOME_NO_REPLY.equals(outcome);
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
