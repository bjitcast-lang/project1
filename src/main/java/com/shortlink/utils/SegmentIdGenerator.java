package com.shortlink.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 号段模式发号器（双 buffer）。
 * <p>
 * 本地缓存一个号段，剩余 20% 时后台预取下一段，缓存耗尽直接切换，避免每次取号打 DB。
 * 重启后未使用的号段直接丢弃，max_id 不回退，因此不会发出重复 ID。
 */
@Slf4j
@Component
public class SegmentIdGenerator {

    private static final String BIZ_TAG = "short_link";
    private static final long PREFETCH_THRESHOLD = 200L;

    private final IdSegmentAllocator allocator;

    private final ExecutorService prefetchExecutor;

    private long cursor;

    private long max;

    private volatile Segment buffered;

    private final AtomicBoolean loading = new AtomicBoolean(false);

    public SegmentIdGenerator(IdSegmentAllocator allocator) {
        this.allocator = allocator;
        this.prefetchExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "segment-prefetch");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized long nextId() {
        if (cursor >= max) {
            if (buffered != null) {
                cursor = buffered.getMin();
                max = buffered.getMax();
                buffered = null;
            } else {
                Segment segment = allocator.allocate(BIZ_TAG);
                cursor = segment.getMin();
                max = segment.getMax();
                log.info("号段加载，bizTag={}, [{}, {}]", BIZ_TAG, cursor, max);
            }
        }
        long id = cursor++;
        maybePrefetch();
        return id;
    }

    private void maybePrefetch() {
        if (buffered != null || max - cursor > PREFETCH_THRESHOLD) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        prefetchExecutor.execute(() -> {
            try {
                buffered = allocator.allocate(BIZ_TAG);
                log.info("号段预取完成，[{}, {}]", buffered.getMin(), buffered.getMax());
            } catch (Exception e) {
                log.error("号段预取失败，将在耗尽时同步重试", e);
            } finally {
                loading.set(false);
            }
        });
    }
}
