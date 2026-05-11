package com.github.litroenade.maidbridge.maid.action;

import com.github.litroenade.maidbridge.Config;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 按女仆 UUID 串行执行外部写操作。
 * <p>查询可以并行；坐下、任务、日程等状态修改需要排队，避免同一只女仆被多个请求同时改写。
 */
public final class MaidOperationQueue {
    public static final MaidOperationQueue INSTANCE = new MaidOperationQueue();

    private static final String GLOBAL_KEY = "global";

    private final Map<String, ArrayDeque<QueuedOperation>> queues = new HashMap<>();
    private final Set<String> runningKeys = new HashSet<>();

    private MaidOperationQueue() {
    }

    public static String key(String maidUuid) {
        var uuid = safe(maidUuid);
        if (!uuid.isBlank()) {
            return "uuid:" + uuid;
        }
        return GLOBAL_KEY;
    }

    public void enqueue(
            MinecraftServer server,
            String key,
            Runnable operation,
            Consumer<RuntimeException> scheduleFailureHandler
    ) {
        var queueKey = normalizeKey(key);
        RuntimeException rejected = null;
        synchronized (this) {
            var queue = queues.computeIfAbsent(queueKey, ignored -> new ArrayDeque<>());
            if (queue.size() >= Config.maxPendingMaidOperationsPerKey) {
                rejected = new IllegalStateException("女仆操作队列已满，key=" + queueKey);
            } else {
                queue.add(new QueuedOperation(server, operation, scheduleFailureHandler));
            }
            if (rejected != null) {
                if (queue.isEmpty() && !runningKeys.contains(queueKey)) {
                    queues.remove(queueKey);
                }
            } else if (runningKeys.contains(queueKey)) {
                return;
            } else {
                runningKeys.add(queueKey);
            }
        }
        if (rejected != null) {
            scheduleFailureHandler.accept(rejected);
            return;
        }
        scheduleNext(queueKey);
    }

    private void scheduleNext(String key) {
        QueuedOperation operation;
        synchronized (this) {
            var queue = queues.get(key);
            if (queue == null || queue.isEmpty()) {
                queues.remove(key);
                runningKeys.remove(key);
                return;
            }
            operation = queue.removeFirst();
        }

        try {
            operation.executor().execute(() -> {
                try {
                    operation.operation().run();
                } finally {
                    scheduleNext(key);
                }
            });
        } catch (RuntimeException exception) {
            try {
                operation.scheduleFailureHandler().accept(exception);
            } finally {
                scheduleNext(key);
            }
        }
    }

    private static String normalizeKey(String key) {
        var normalized = safe(key);
        return normalized.isBlank() ? GLOBAL_KEY : normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record QueuedOperation(
            Executor executor,
            Runnable operation,
            Consumer<RuntimeException> scheduleFailureHandler
    ) {
    }
}
