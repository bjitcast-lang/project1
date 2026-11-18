package com.shortlink.utils;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shortlink.entity.ShortLink;
import com.shortlink.mapper.ShortLinkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis Bitmap 的布隆过滤器（不依赖 Redisson）。
 * <p>
 * 双缓冲：current 指向对外提供查询的 bitmap，重建时写 building，
 * 新建短链时同时写入两者，切换指针后再让旧 key 过期。
 * <p>
 * 未完成首轮重建时 ready 标记不存在，{@link #mightContain} 直接返回 true，
 * 退化为「查 DB」，避免误判导致 404。
 */
@Slf4j
@Component
public class BloomFilterService {

    private static final String CURRENT_KEY = "shortlink:bloom:current";
    private static final String BUILDING_KEY = "shortlink:bloom:building";
    private static final String READY_KEY = "shortlink:bloom:ready";
    private static final String BITMAP_PREFIX = "shortlink:bloom:data:";
    private static final int HASH_COUNT = 3;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShortLinkMapper shortLinkMapper;

    @Value("${shortlink.bloom.bits:10000000}")
    private long bits;

    public void add(String shortUri) {
        if (shortUri == null) {
            return;
        }
        String current = currentBitmap();
        setBits(current, shortUri);
        String building = stringRedisTemplate.opsForValue().get(BUILDING_KEY);
        if (building != null && !building.equals(current)) {
            setBits(building, shortUri);
        }
    }

    public boolean mightContain(String shortUri) {
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(READY_KEY))) {
            return true;
        }
        String key = currentBitmap();
        for (long offset : offsets(shortUri)) {
            if (!Boolean.TRUE.equals(stringRedisTemplate.opsForValue().getBit(key, offset))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 全量重建：分页扫描 DB，写入新 bitmap，最后原子切换指针。
     */
    public synchronized void rebuild() {
        String oldKey = stringRedisTemplate.opsForValue().get(CURRENT_KEY);
        String newKey = BITMAP_PREFIX + System.currentTimeMillis();
        stringRedisTemplate.opsForValue().set(BUILDING_KEY, newKey);

        long lastId = 0L;
        long total = 0L;
        while (true) {
            List<ShortLink> batch = shortLinkMapper.selectList(
                    Wrappers.<ShortLink>lambdaQuery()
                            .gt(ShortLink::getId, lastId)
                            .orderByAsc(ShortLink::getId)
                            .last("limit 1000"));
            if (batch.isEmpty()) {
                break;
            }
            for (ShortLink link : batch) {
                setBits(newKey, link.getShortUri());
                lastId = link.getId();
            }
            total += batch.size();
            if (batch.size() < 1000) {
                break;
            }
        }

        stringRedisTemplate.opsForValue().set(CURRENT_KEY, newKey);
        stringRedisTemplate.opsForValue().set(READY_KEY, "1");
        stringRedisTemplate.delete(BUILDING_KEY);
        if (oldKey != null && !oldKey.equals(newKey)) {
            // 留 5 分钟过渡期，避免切换瞬间旧 key 被立刻删除
            stringRedisTemplate.expire(oldKey, 5, TimeUnit.MINUTES);
        }
        log.info("布隆过滤器重建完成，bitmap={}, 链接数={}", newKey, total);
    }

    public boolean isReady() {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(READY_KEY));
    }

    private String currentBitmap() {
        String current = stringRedisTemplate.opsForValue().get(CURRENT_KEY);
        return current == null ? BITMAP_PREFIX + "default" : current;
    }

    private void setBits(String bitmapKey, String value) {
        for (long offset : offsets(value)) {
            stringRedisTemplate.opsForValue().setBit(bitmapKey, offset, true);
        }
    }

    private long[] offsets(String value) {
        String md5 = SecureUtil.md5(value);
        long[] result = new long[HASH_COUNT];
        for (int i = 0; i < HASH_COUNT; i++) {
            long hash = Long.parseLong(md5.substring(i * 8, i * 8 + 8), 16);
            result[i] = Math.abs(hash % bits);
        }
        return result;
    }
}
