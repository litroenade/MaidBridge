package com.github.litroenade.maidbridge.maid.turn;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.github.litroenade.maidbridge.protocol.frame.MaidTurnIdentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

public final class MaidExternalTurnGuard {
    private static final MaidExternalTurnGuard EXTERNAL_INJECTION_GUARD =
            new MaidExternalTurnGuard(System::currentTimeMillis, () -> Config.maidExternalTurnTtlMs, () -> Config.maxPendingMaidAgentTurns);

    private final LongSupplier clock;
    private final LongSupplier ttlMs;
    private final IntSupplier maxPendingTurns;
    private final Map<String, StoredTurn> activeTurns = new LinkedHashMap<>();

    public enum BeginStatus {
        ACCEPTED,
        DUPLICATE_PENDING,
        QUEUE_FULL
    }

    public record BeginResult(BeginStatus status) {
        public boolean accepted() {
            return status == BeginStatus.ACCEPTED;
        }
    }

    public record ActiveTurn(
            String maidUuid,
            String turnId,
            String requestId,
            String userMessage,
            Map<String, Object> clientMetadata,
            long startedAt,
            long deadlineAt,
            String deliveredSessionId,
            String deliveredAgentName,
            long deliveredAt
    ) {
        public ActiveTurn {
            clientMetadata = immutableMetadata(clientMetadata);
            deliveredAgentName = safeString(deliveredAgentName);
        }
    }

    public record ActiveTurnSnapshot(
            String maidUuid,
            String turnId,
            String requestId,
            String userMessage,
            long startedAt,
            long remainingMs,
            String deliveredSessionId,
            long deliveredAt
    ) {
    }

    public record CompletedTurn(
            ActiveTurn turn,
            String outcome,
            String reason,
            long completedAt
    ) {
    }

    private MaidExternalTurnGuard(LongSupplier clock, LongSupplier ttlMs, IntSupplier maxPendingTurns) {
        this.clock = clock;
        this.ttlMs = ttlMs;
        this.maxPendingTurns = maxPendingTurns;
    }

    public static BeginResult tryBeginExternalTurn(String maidUuid, String turnId, String requestId, String userMessage, Map<String, Object> clientMetadata) {
        return EXTERNAL_INJECTION_GUARD.tryBegin(maidUuid, turnId, requestId, userMessage, clientMetadata);
    }

    public static void completeExternalTurn(String maidUuid, String turnId) {
        EXTERNAL_INJECTION_GUARD.complete(maidUuid, turnId);
    }

    public static ActiveTurn findExternalTurn(String maidUuid, String turnId) {
        return EXTERNAL_INJECTION_GUARD.find(maidUuid, turnId);
    }

    public static CompletedTurn releaseForIdentity(MaidTurnIdentity identity, String outcome, String reason) {
        return EXTERNAL_INJECTION_GUARD.releaseIdentity(identity, outcome, reason);
    }

    public static void markDelivered(MaidTurnIdentity identity, String sessionId, String agentName) {
        EXTERNAL_INJECTION_GUARD.markIdentityDelivered(identity, sessionId, agentName);
    }

    public static List<CompletedTurn> releaseDeliveredToSession(String sessionId, String outcome, String reason) {
        return EXTERNAL_INJECTION_GUARD.releaseSession(sessionId, outcome, reason);
    }

    public static List<ActiveTurnSnapshot> snapshotExternalTurns() {
        return EXTERNAL_INJECTION_GUARD.snapshot();
    }

    public static List<CompletedTurn> sweepExpiredTurns() {
        return EXTERNAL_INJECTION_GUARD.sweep();
    }

    private synchronized BeginResult tryBegin(String maidUuid, String turnId, String requestId, String userMessage, Map<String, Object> clientMetadata) {
        var normalizedMaidUuid = requiredMaidUuid(maidUuid);
        var normalizedTurnId = requiredTurnId(turnId);
        var key = turnKey(normalizedMaidUuid, normalizedTurnId);
        var now = clock.getAsLong();
        releaseExpired(now);
        if (activeTurns.containsKey(key)) {
            return new BeginResult(BeginStatus.DUPLICATE_PENDING);
        }
        if (activeTurns.size() >= Math.max(1, maxPendingTurns.getAsInt())) {
            return new BeginResult(BeginStatus.QUEUE_FULL);
        }
        activeTurns.put(key, new StoredTurn(
                normalizedMaidUuid,
                normalizedTurnId,
                safeString(requestId),
                safeString(userMessage),
                immutableMetadata(clientMetadata),
                now,
                now + Math.max(1L, ttlMs.getAsLong()),
                "",
                "",
                0L
        ));
        return new BeginResult(BeginStatus.ACCEPTED);
    }

    private synchronized void complete(String maidUuid, String turnId) {
        releaseExact(requiredMaidUuid(maidUuid), requiredTurnId(turnId), "reply", "");
    }

    private synchronized ActiveTurn find(String maidUuid, String turnId) {
        releaseExpired(clock.getAsLong());
        var normalizedTurnId = requiredTurnId(turnId);
        var normalizedMaidUuid = requiredMaidUuid(maidUuid);
        var activeTurn = activeTurns.get(turnKey(normalizedMaidUuid, normalizedTurnId));
        return activeTurn == null ? null : toActiveTurn(activeTurn);
    }

    private synchronized CompletedTurn releaseIdentity(MaidTurnIdentity identity, String outcome, String reason) {
        releaseExpired(clock.getAsLong());
        if (identity.maidUuid().isBlank() || identity.turnId().isBlank()) {
            return releaseByRequestOrUniqueTurn(identity, outcome, reason);
        }
        return releaseExact(identity.maidUuid(), identity.turnId(), outcome, reason);
    }

    private CompletedTurn releaseByRequestOrUniqueTurn(MaidTurnIdentity identity, String outcome, String reason) {
        var normalizedRequestId = safeString(identity.requestId());
        if (!normalizedRequestId.isBlank()) {
            for (Iterator<Map.Entry<String, StoredTurn>> iterator = activeTurns.entrySet().iterator(); iterator.hasNext(); ) {
                var entry = iterator.next();
                var activeTurn = entry.getValue();
                if (normalizedRequestId.equals(activeTurn.requestId())) {
                    iterator.remove();
                    return completed(activeTurn, outcome, reason);
                }
            }
        }
        var normalizedTurnId = safeString(identity.turnId());
        if (normalizedTurnId.isBlank()) {
            return null;
        }
        StoredTurn match = null;
        for (StoredTurn activeTurn : activeTurns.values()) {
            if (!normalizedTurnId.equals(activeTurn.turnId())) {
                continue;
            }
            if (match != null) {
                MaidBridge.LOGGER.warn("拒绝释放待处理外部女仆轮次，原因={}；turn_id 匹配到多只女仆 turnId={}", reason, normalizedTurnId);
                return null;
            }
            match = activeTurn;
        }
        return match == null ? null : releaseExact(match.maidUuid(), match.turnId(), outcome, reason);
    }

    private CompletedTurn releaseExact(String maidUuid, String turnId, String outcome, String reason) {
        var activeTurn = activeTurns.remove(turnKey(maidUuid, turnId));
        return activeTurn == null ? null : completed(activeTurn, outcome, reason);
    }

    private synchronized void markIdentityDelivered(MaidTurnIdentity identity, String sessionId, String agentName) {
        releaseExpired(clock.getAsLong());
        var storedTurn = findStored(identity);
        if (storedTurn == null) {
            return;
        }
        activeTurns.put(turnKey(storedTurn.maidUuid(), storedTurn.turnId()), new StoredTurn(
                storedTurn.maidUuid(),
                storedTurn.turnId(),
                storedTurn.requestId(),
                storedTurn.userMessage(),
                storedTurn.clientMetadata(),
                storedTurn.startedAt(),
                storedTurn.deadlineAt(),
                safeString(sessionId),
                safeString(agentName),
                clock.getAsLong()
        ));
    }

    private StoredTurn findStored(MaidTurnIdentity identity) {
        if (!identity.maidUuid().isBlank() && !identity.turnId().isBlank()) {
            return activeTurns.get(turnKey(identity.maidUuid(), identity.turnId()));
        }
        if (!identity.requestId().isBlank()) {
            for (StoredTurn activeTurn : activeTurns.values()) {
                if (identity.requestId().equals(activeTurn.requestId())) {
                    return activeTurn;
                }
            }
        }
        return null;
    }

    private synchronized List<CompletedTurn> releaseSession(String sessionId, String outcome, String reason) {
        var normalizedSessionId = safeString(sessionId);
        if (normalizedSessionId.isBlank()) {
            return List.of();
        }
        var completedTurns = new ArrayList<CompletedTurn>();
        for (Iterator<Map.Entry<String, StoredTurn>> iterator = activeTurns.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var activeTurn = entry.getValue();
            if (normalizedSessionId.equals(activeTurn.deliveredSessionId())) {
                iterator.remove();
                completedTurns.add(completed(activeTurn, outcome, reason));
            }
        }
        return List.copyOf(completedTurns);
    }

    private synchronized List<ActiveTurnSnapshot> snapshot() {
        var now = clock.getAsLong();
        releaseExpired(now);
        var snapshots = new ArrayList<ActiveTurnSnapshot>();
        for (StoredTurn activeTurn : activeTurns.values()) {
            snapshots.add(toSnapshot(activeTurn, now));
        }
        snapshots.sort(Comparator.comparingLong(ActiveTurnSnapshot::startedAt).thenComparing(ActiveTurnSnapshot::requestId));
        return List.copyOf(snapshots);
    }

    private synchronized List<CompletedTurn> sweep() {
        return releaseExpired(clock.getAsLong());
    }

    private List<CompletedTurn> releaseExpired(long now) {
        var completedTurns = new ArrayList<CompletedTurn>();
        for (Iterator<Map.Entry<String, StoredTurn>> iterator = activeTurns.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var activeTurn = entry.getValue();
            if (activeTurn.deadlineAt() <= now) {
                iterator.remove();
                completedTurns.add(completed(activeTurn, BridgeProtocol.TYPE_MAID_AGENT_TURN_OUTCOME_DEADLINE, "turn_deadline_elapsed"));
            }
        }
        return List.copyOf(completedTurns);
    }

    private CompletedTurn completed(StoredTurn activeTurn, String outcome, String reason) {
        return new CompletedTurn(toActiveTurn(activeTurn), safeString(outcome), safeString(reason), clock.getAsLong());
    }

    private static String safeString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String requiredMaidUuid(String maidUuid) {
        var normalizedUuid = safeString(maidUuid);
        if (normalizedUuid.isBlank()) {
            throw new IllegalArgumentException("外部注入保护需要非空 maid.uuid");
        }
        return normalizedUuid;
    }

    private static String requiredTurnId(String turnId) {
        var normalizedTurnId = safeString(turnId);
        if (normalizedTurnId.isBlank()) {
            throw new IllegalArgumentException("turn_id 不能为空");
        }
        return normalizedTurnId;
    }

    private static String turnKey(String maidUuid, String turnId) {
        return requiredMaidUuid(maidUuid) + "\n" + requiredTurnId(turnId);
    }

    private static ActiveTurn toActiveTurn(StoredTurn activeTurn) {
        return new ActiveTurn(
                activeTurn.maidUuid(),
                activeTurn.turnId(),
                activeTurn.requestId(),
                activeTurn.userMessage(),
                activeTurn.clientMetadata(),
                activeTurn.startedAt(),
                activeTurn.deadlineAt(),
                activeTurn.deliveredSessionId(),
                activeTurn.deliveredAgentName(),
                activeTurn.deliveredAt()
        );
    }

    private ActiveTurnSnapshot toSnapshot(StoredTurn activeTurn, long now) {
        return new ActiveTurnSnapshot(
                activeTurn.maidUuid(),
                activeTurn.turnId(),
                activeTurn.requestId(),
                activeTurn.userMessage(),
                activeTurn.startedAt(),
                Math.max(0L, activeTurn.deadlineAt() - now),
                activeTurn.deliveredSessionId(),
                activeTurn.deliveredAt()
        );
    }

    private static Map<String, Object> immutableMetadata(Map<String, Object> metadata) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(metadata == null ? Map.of() : metadata));
    }

    private record StoredTurn(
            String maidUuid,
            String turnId,
            String requestId,
            String userMessage,
            Map<String, Object> clientMetadata,
            long startedAt,
            long deadlineAt,
            String deliveredSessionId,
            String deliveredAgentName,
            long deliveredAt
    ) {
        private StoredTurn {
            clientMetadata = immutableMetadata(clientMetadata);
            deliveredSessionId = safeString(deliveredSessionId);
            deliveredAgentName = safeString(deliveredAgentName);
        }
    }
}
