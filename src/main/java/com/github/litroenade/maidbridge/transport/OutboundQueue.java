package com.github.litroenade.maidbridge.transport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;

final class OutboundQueue {
    private static final int DEFAULT_MAX_FRAMES = 512;
    private static final int MAX_DRAIN_COUNT = 64;

    private final Object lock = new Object();
    private final ArrayDeque<OutboundFrame> frames = new ArrayDeque<>();
    private int maxFrames = DEFAULT_MAX_FRAMES;
    private long droppedFrames;

    void configure(int nextMaxFrames) {
        List<OutboundFrame> dropped;
        synchronized (lock) {
            maxFrames = Math.max(1, nextMaxFrames);
            dropped = trimToLimit(false);
        }
        notifyDropped(dropped);
    }

    void offer(OutboundFrame frame) {
        List<OutboundFrame> dropped;
        synchronized (lock) {
            frames.addLast(frame);
            dropped = trimToLimit(false);
        }
        notifyDropped(dropped);
    }

    void offerPriority(OutboundFrame frame) {
        List<OutboundFrame> dropped;
        synchronized (lock) {
            frames.addFirst(frame);
            dropped = trimToLimit(true);
        }
        notifyDropped(dropped);
    }

    List<OutboundFrame> drainMatching(Predicate<OutboundFrame> predicate) {
        synchronized (lock) {
            var count = MAX_DRAIN_COUNT;
            List<OutboundFrame> drained = new ArrayList<>(Math.min(MAX_DRAIN_COUNT, frames.size()));
            ArrayDeque<OutboundFrame> remaining = new ArrayDeque<>(frames.size());
            while (!frames.isEmpty()) {
                var frame = frames.removeFirst();
                if (count > 0 && predicate.test(frame)) {
                    drained.add(frame);
                    count--;
                } else {
                    remaining.addLast(frame);
                }
            }
            frames.addAll(remaining);
            return List.copyOf(drained);
        }
    }

    List<OutboundFrame> dropMatching(Predicate<OutboundFrame> predicate) {
        List<OutboundFrame> dropped;
        synchronized (lock) {
            dropped = new ArrayList<>();
            ArrayDeque<OutboundFrame> remaining = new ArrayDeque<>(frames.size());
            while (!frames.isEmpty()) {
                var frame = frames.removeFirst();
                if (predicate.test(frame)) {
                    dropped.add(frame);
                    droppedFrames++;
                } else {
                    remaining.addLast(frame);
                }
            }
            frames.addAll(remaining);
        }
        notifyDropped(dropped);
        return List.copyOf(dropped);
    }

    int size() {
        synchronized (lock) {
            return frames.size();
        }
    }

    long droppedFrames() {
        synchronized (lock) {
            return droppedFrames;
        }
    }

    List<FrameSummary> summarizeByType() {
        synchronized (lock) {
            LinkedHashMap<String, FrameSummary> counts = new LinkedHashMap<>();
            for (OutboundFrame frame : frames) {
                var key = frame.type();
                var current = counts.get(key);
                if (current == null) {
                    counts.put(key, new FrameSummary(frame.type(), 1));
                } else {
                    counts.put(key, new FrameSummary(current.type(), current.count() + 1));
                }
            }
            return List.copyOf(counts.values());
        }
    }

    List<OutboundFrame> clearWithDropHooks() {
        synchronized (lock) {
            List<OutboundFrame> dropped = new ArrayList<>(frames);
            frames.clear();
            droppedFrames += dropped.size();
            return List.copyOf(dropped);
        }
    }

    private List<OutboundFrame> trimToLimit(boolean dropNewestRegularFramesFirst) {
        List<OutboundFrame> dropped = new ArrayList<>();
        while (frames.size() > maxFrames) {
            dropped.add(dropNewestRegularFramesFirst ? frames.removeLast() : frames.removeFirst());
            droppedFrames++;
        }
        return dropped;
    }

    private void notifyDropped(List<OutboundFrame> dropped) {
        for (OutboundFrame frame : dropped) {
            frame.onDropped();
        }
    }

    record FrameSummary(String type, int count) {
    }
}
