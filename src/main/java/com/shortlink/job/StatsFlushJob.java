package com.shortlink.job;

import com.shortlink.mapper.LinkAccessStatsMapper;
import com.shortlink.utils.ShortLinkConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 每分钟把 Redis 中的 PV/UV 落库。
 * <p>
 * dirty ZSet 的 member 是 {@code yyyyMMdd:shortUri}，score 为最近一次访问时间；
 * 只清理 score 早于 cutoff 的 member，处理期间有新访问会刷新 score，从而保留到下一批。
 */
@Slf4j
@Component
public class StatsFlushJob {

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private static final long CUTOFF_MILLIS = 10_000L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private LinkAccessStatsMapper linkAccessStatsMapper;

    @Value("${shortlink.domain:http://localhost:8082/}")
    private String domain;

    @Scheduled(fixedDelay = 60_000L)
    public void flush() {
        long cutoff = System.currentTimeMillis() - CUTOFF_MILLIS;
        Set<String> members = stringRedisTemplate.opsForZSet()
                .rangeByScore(ShortLinkConstants.STATS_DIRTY_KEY, 0, cutoff, 0, 500);
        if (members == null || members.isEmpty()) {
            return;
        }
        int flushed = 0;
        for (String member : members) {
            Double score = stringRedisTemplate.opsForZSet()
                    .score(ShortLinkConstants.STATS_DIRTY_KEY, member);
            if (score == null || score > cutoff) {
                continue;
            }
            if (flushOne(member)) {
                stringRedisTemplate.opsForZSet()
                        .remove(ShortLinkConstants.STATS_DIRTY_KEY, member);
                flushed++;
            }
        }
        log.info("访问统计落库完成，本次处理 {} 条", flushed);
    }

    private boolean flushOne(String member) {
        String[] parts = member.split(":");
        if (parts.length != 2) {
            return true;
        }
        try {
            LocalDate statDate = LocalDate.parse(parts[0], DAY_FORMATTER);
            String shortUri = parts[1];
            String pvKey = ShortLinkConstants.PV_KEY + parts[0] + ":" + shortUri;
            String uvKey = ShortLinkConstants.UV_KEY + parts[0] + ":" + shortUri;

            String pvValue = stringRedisTemplate.opsForValue().get(pvKey);
            Long uvValue = stringRedisTemplate.opsForHyperLogLog().size(uvKey);
            int pv = pvValue == null ? 0 : Integer.parseInt(pvValue);
            int uv = uvValue == null ? 0 : uvValue.intValue();

            String fullShortUrl = (domain.endsWith("/") ? domain : domain + "/") + shortUri;
            linkAccessStatsMapper.upsert(fullShortUrl, statDate, pv, uv);
            return true;
        } catch (Exception e) {
            log.error("访问统计落库失败，member={}", member, e);
            return false;
        }
    }
}
