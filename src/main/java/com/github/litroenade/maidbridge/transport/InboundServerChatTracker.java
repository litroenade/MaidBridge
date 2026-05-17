package com.github.litroenade.maidbridge.transport;

import java.util.LinkedHashMap;

final class InboundServerChatTracker {
    private final LinkedHashMap<String, DeliveryState> deliveries = new LinkedHashMap<>();
    private final int maxRememberedIds;
    private int pendingCount;

    InboundServerChatTracker(int maxRememberedIds) {
        this.maxRememberedIds = Math.max(1, maxRememberedIds);
    }

    synchronized Reservation reserve(String id, int maxPendingCount) {
        var existing = deliveries.get(id);
        if (existing == DeliveryState.PENDING) {
            return new Reservation(ReservationStatus.DUPLICATE_PENDING);
        }
        if (existing == DeliveryState.SUCCEEDED) {
            return new Reservation(ReservationStatus.DUPLICATE_SUCCEEDED);
        }
        if (pendingCount >= Math.max(1, maxPendingCount)) {
            return new Reservation(ReservationStatus.QUEUE_FULL);
        }
        deliveries.put(id, DeliveryState.PENDING);
        pendingCount++;
        evictOldCompleted();
        return new Reservation(ReservationStatus.ACCEPTED);
    }

    synchronized void markSucceeded(String id) {
        if (deliveries.get(id) == DeliveryState.PENDING) {
            pendingCount--;
        }
        deliveries.put(id, DeliveryState.SUCCEEDED);
        evictOldCompleted();
    }

    synchronized void markFailed(String id) {
        if (deliveries.remove(id) == DeliveryState.PENDING) {
            pendingCount--;
        }
    }

    synchronized void clear() {
        deliveries.clear();
        pendingCount = 0;
    }

    private void evictOldCompleted() {
        while (deliveries.size() > maxRememberedIds) {
            var iterator = deliveries.entrySet().iterator();
            var removed = false;
            while (iterator.hasNext()) {
                if (iterator.next().getValue() == DeliveryState.SUCCEEDED) {
                    iterator.remove();
                    removed = true;
                    break;
                }
            }
            if (!removed) {
                return;
            }
        }
    }

    enum ReservationStatus {
        ACCEPTED,
        DUPLICATE_PENDING,
        DUPLICATE_SUCCEEDED,
        QUEUE_FULL
    }

    record Reservation(ReservationStatus status) {
    }

    private enum DeliveryState {
        PENDING,
        SUCCEEDED
    }
}
