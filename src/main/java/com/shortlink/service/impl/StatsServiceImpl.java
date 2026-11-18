package com.shortlink.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shortlink.dto.Result;
import com.shortlink.entity.LinkAccessStats;
import com.shortlink.entity.ShortLink;
import com.shortlink.mapper.LinkAccessStatsMapper;
import com.shortlink.mapper.ShortLinkMapper;
import com.shortlink.service.IStatsService;
import com.shortlink.utils.ShortLinkConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class StatsServiceImpl implements IStatsService {

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private LinkAccessStatsMapper linkAccessStatsMapper;

    @Resource
    private ShortLinkMapper shortLinkMapper;

    @Override
    @Async("statsExecutor")
    public void recordAsync(String shortUri, String ip) {
        try {
            String date = LocalDate.now().format(DAY_FORMATTER);
            String pvKey = ShortLinkConstants.PV_KEY + date + ":" + shortUri;
            String uvKey = ShortLinkConstants.UV_KEY + date + ":" + shortUri;

            stringRedisTemplate.opsForValue().increment(pvKey);
            stringRedisTemplate.expire(pvKey,
                    ShortLinkConstants.STATS_KEY_TTL_HOURS, TimeUnit.HOURS);
            stringRedisTemplate.opsForHyperLogLog()
                    .add(uvKey, StrUtil.isBlank(ip) ? "unknown" : ip);
            stringRedisTemplate.expire(uvKey,
                    ShortLinkConstants.STATS_KEY_TTL_HOURS, TimeUnit.HOURS);
            // 标记为待落库，StatsFlushJob 按 (date:code) 聚合并 upsert
            stringRedisTemplate.opsForZSet().add(
                    ShortLinkConstants.STATS_DIRTY_KEY,
                    date + ":" + shortUri,
                    System.currentTimeMillis());
        } catch (Exception e) {
            // 统计失败不能影响跳转
            log.warn("记录访问统计失败，shortUri={}", shortUri, e);
        }
    }

    @Override
    public Result query(String code, String start, String end) {
        if (StrUtil.isBlank(code)) {
            return Result.fail("短码不能为空");
        }
        ShortLink link = shortLinkMapper.selectOne(Wrappers.<ShortLink>lambdaQuery()
                .eq(ShortLink::getShortUri, code.trim())
                .last("limit 1"));
        if (link == null) {
            return Result.fail("短链不存在");
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = StrUtil.isBlank(start) ? today.minusDays(6) : LocalDate.parse(start);
            endDate = StrUtil.isBlank(end) ? today : LocalDate.parse(end);
        } catch (Exception e) {
            return Result.fail("日期格式应为 yyyy-MM-dd");
        }
        if (startDate.isAfter(endDate)) {
            return Result.fail("开始日期不能晚于结束日期");
        }

        List<LinkAccessStats> records = new ArrayList<>(linkAccessStatsMapper.selectList(
                Wrappers.<LinkAccessStats>lambdaQuery()
                        .eq(LinkAccessStats::getFullShortUrl, link.getFullShortUrl())
                        .ge(LinkAccessStats::getStatDate, startDate)
                        .le(LinkAccessStats::getStatDate, endDate)
                        .orderByAsc(LinkAccessStats::getStatDate)));

        // 当天数据以 Redis 实时值为准
        if (!today.isBefore(startDate) && !today.isAfter(endDate)) {
            String date = today.format(DAY_FORMATTER);
            String pvKey = ShortLinkConstants.PV_KEY + date + ":" + code;
            String uvKey = ShortLinkConstants.UV_KEY + date + ":" + code;
            String pv = stringRedisTemplate.opsForValue().get(pvKey);
            Long uv = stringRedisTemplate.opsForHyperLogLog().size(uvKey);

            records.removeIf(record -> today.equals(record.getStatDate()));
            records.add(new LinkAccessStats()
                    .setFullShortUrl(link.getFullShortUrl())
                    .setStatDate(today)
                    .setPv(pv == null ? 0 : Integer.parseInt(pv))
                    .setUv(uv == null ? 0 : uv.intValue()));
            records.sort((a, b) -> a.getStatDate().compareTo(b.getStatDate()));
        }
        return Result.ok(records);
    }
}
