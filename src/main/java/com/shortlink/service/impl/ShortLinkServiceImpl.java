package com.shortlink.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortlink.dto.GotoResult;
import com.shortlink.dto.LinkCacheValue;
import com.shortlink.dto.Result;
import com.shortlink.dto.ShortLinkCreateDTO;
import com.shortlink.dto.ShortLinkUpdateDTO;
import com.shortlink.dto.ShortLinkVO;
import com.shortlink.entity.ShortLink;
import com.shortlink.mapper.ShortLinkMapper;
import com.shortlink.service.IShortLinkService;
import com.shortlink.utils.BloomFilterService;
import com.shortlink.utils.SegmentIdGenerator;
import com.shortlink.utils.ShortCodeUtil;
import com.shortlink.utils.ShortLinkConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShortLinkServiceImpl implements IShortLinkService {

    @Resource
    private ShortLinkMapper shortLinkMapper;

    @Resource
    private SegmentIdGenerator segmentIdGenerator;

    @Resource
    private BloomFilterService bloomFilterService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource(name = "cacheRebuildExecutor")
    private ThreadPoolTaskExecutor cacheRebuildExecutor;

    @Value("${shortlink.domain:http://localhost:8082/}")
    private String domain;

    @Override
    public Result create(ShortLinkCreateDTO dto) {
        String originUrl = dto.getOriginUrl();
        if (StrUtil.isBlank(originUrl)) {
            return Result.fail("原始链接不能为空");
        }
        originUrl = originUrl.trim();
        if (originUrl.length() > 2048) {
            return Result.fail("原始链接过长");
        }
        if (!originUrl.startsWith("http://") && !originUrl.startsWith("https://")) {
            return Result.fail("原始链接必须以 http:// 或 https:// 开头");
        }

        long id = segmentIdGenerator.nextId();
        String shortUri;
        int createType;
        if (StrUtil.isNotBlank(dto.getCustomCode())) {
            String custom = dto.getCustomCode().trim();
            if (!ShortCodeUtil.isValidCustom(custom)) {
                return Result.fail("自定义短码需为 4-16 位字母、数字、_ 或 -");
            }
            if (ShortCodeUtil.isReserved(custom)) {
                return Result.fail("该短码为系统保留字");
            }
            if (existsShortUri(custom)) {
                return Result.fail("短码已被占用，请更换");
            }
            shortUri = custom;
            createType = 1;
        } else {
            shortUri = ShortCodeUtil.encode(id);
            createType = 0;
        }

        String fullShortUrl = domainWithSlash() + shortUri;
        LocalDateTime now = LocalDateTime.now();
        ShortLink link = new ShortLink()
                .setId(id)
                .setGroupId(dto.getGroupId() == null ? 0L : dto.getGroupId())
                .setOriginUrl(originUrl)
                .setShortUri(shortUri)
                .setFullShortUrl(fullShortUrl)
                .setCreateType(createType)
                .setValidDateType(dto.getValidDate() == null ? 0 : 1)
                .setValidDate(dto.getValidDate())
                .setState(1)
                .setDelFlag(0)
                .setCreateTime(now)
                .setUpdateTime(now);
        try {
            shortLinkMapper.insert(link);
        } catch (DuplicateKeyException e) {
            // 唯一索引兜底：并发创建同一自定义短码
            return Result.fail("短码已被占用，请更换");
        }

        try {
            bloomFilterService.add(shortUri);
        } catch (Exception e) {
            // 布隆过滤器不可用不影响创建，下一次重建会补上
            log.warn("写入布隆过滤器失败，shortUri={}", shortUri, e);
        }
        return Result.ok(toVO(link));
    }

    @Override
    public Result page(int current, int size, Long groupId) {
        int pageNo = current < 1 ? 1 : current;
        int pageSize = size < 1 ? 10 : Math.min(size, 100);
        LambdaQueryWrapper<ShortLink> wrapper = Wrappers.<ShortLink>lambdaQuery()
                .eq(groupId != null, ShortLink::getGroupId, groupId)
                .orderByDesc(ShortLink::getCreateTime);
        Page<ShortLink> page = shortLinkMapper.selectPage(
                new Page<>(pageNo, pageSize), wrapper);
        List<ShortLinkVO> records = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return Result.ok(records, page.getTotal());
    }

    @Override
    public Result update(ShortLinkUpdateDTO dto) {
        if (dto.getId() == null) {
            return Result.fail("id 不能为空");
        }
        ShortLink link = shortLinkMapper.selectById(dto.getId());
        if (link == null) {
            return Result.fail("短链不存在");
        }
        if (StrUtil.isNotBlank(dto.getOriginUrl())) {
            String originUrl = dto.getOriginUrl().trim();
            if (!originUrl.startsWith("http://") && !originUrl.startsWith("https://")) {
                return Result.fail("原始链接必须以 http:// 或 https:// 开头");
            }
            link.setOriginUrl(originUrl);
        }
        if (dto.getGroupId() != null) {
            link.setGroupId(dto.getGroupId());
        }
        if (dto.getValidDate() != null) {
            link.setValidDate(dto.getValidDate()).setValidDateType(1);
        }
        if (dto.getState() != null) {
            link.setState(dto.getState());
        }
        link.setUpdateTime(LocalDateTime.now());
        shortLinkMapper.updateById(link);
        evictCache(link.getShortUri());
        return Result.ok(toVO(link));
    }

    @Override
    public Result remove(Long id) {
        ShortLink link = shortLinkMapper.selectById(id);
        if (link == null) {
            return Result.fail("短链不存在");
        }
        // @TableLogic 逻辑删除
        shortLinkMapper.deleteById(id);
        evictCache(link.getShortUri());
        return Result.ok();
    }

    @Override
    public GotoResult gotoUrl(String shortUri) {
        String cacheKey = ShortLinkConstants.GOTO_CACHE_KEY + shortUri;
        String cached = null;
        try {
            cached = stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("读取短链缓存失败，降级查 DB，shortUri={}", shortUri, e);
        }
        if (cached != null) {
            if (cached.isEmpty()) {
                return GotoResult.notFound();
            }
            LinkCacheValue cacheValue = JSONUtil.toBean(cached, LinkCacheValue.class);
            if (cacheValue.getExpireTime() != null
                    && cacheValue.getExpireTime() <= System.currentTimeMillis()) {
                rebuildAsync(shortUri, cacheKey);
            }
            return toGotoResult(cacheValue);
        }

        // 布隆过滤器只做「一定不存在」判断，误判时多查一次 DB
        try {
            if (!bloomFilterService.mightContain(shortUri)) {
                cacheNull(cacheKey);
                return GotoResult.notFound();
            }
        } catch (Exception e) {
            log.warn("布隆过滤器不可用，降级查 DB，shortUri={}", shortUri, e);
        }

        ShortLink link = findByShortUri(shortUri);
        if (link == null) {
            cacheNull(cacheKey);
            return GotoResult.notFound();
        }
        cache(link, cacheKey);
        return toGotoResult(link);
    }

    private void rebuildAsync(String shortUri, String cacheKey) {
        String lockKey = ShortLinkConstants.GOTO_LOCK_KEY + shortUri;
        Boolean locked;
        try {
            locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("获取缓存重建锁失败，shortUri={}", shortUri, e);
            return;
        }
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            cacheRebuildExecutor.execute(() -> {
                try {
                    ShortLink link = findByShortUri(shortUri);
                    if (link == null) {
                        cacheNull(cacheKey);
                    } else {
                        cache(link, cacheKey);
                    }
                } catch (Exception e) {
                    log.error("短链缓存重建失败，shortUri={}", shortUri, e);
                } finally {
                    stringRedisTemplate.delete(lockKey);
                }
            });
        } catch (Exception e) {
            stringRedisTemplate.delete(lockKey);
            log.error("提交缓存重建任务失败，shortUri={}", shortUri, e);
        }
    }

    private GotoResult toGotoResult(ShortLink link) {
        if (!Integer.valueOf(1).equals(link.getState())) {
            return GotoResult.notFound();
        }
        if (link.getValidDate() != null && link.getValidDate().isBefore(LocalDateTime.now())) {
            return GotoResult.gone();
        }
        return GotoResult.ok(link.getOriginUrl());
    }

    private GotoResult toGotoResult(LinkCacheValue cacheValue) {
        if (!Integer.valueOf(1).equals(cacheValue.getState())) {
            return GotoResult.notFound();
        }
        if (cacheValue.getValidDate() != null
                && cacheValue.getValidDate() <= System.currentTimeMillis()) {
            return GotoResult.gone();
        }
        return GotoResult.ok(cacheValue.getOriginUrl());
    }

    private void cache(ShortLink link, String cacheKey) {
        try {
            LinkCacheValue cacheValue = new LinkCacheValue()
                    .setOriginUrl(link.getOriginUrl())
                    .setState(link.getState())
                    .setValidDate(link.getValidDate() == null ? null
                            : link.getValidDate().atZone(ZoneId.systemDefault())
                            .toInstant().toEpochMilli())
                    .setExpireTime(System.currentTimeMillis()
                            + ShortLinkConstants.CACHE_LOGICAL_EXPIRE_MILLIS);
            long ttl = ShortLinkConstants.CACHE_TTL_MINUTES + RandomUtil.randomInt(0, 10);
            stringRedisTemplate.opsForValue().set(
                    cacheKey, JSONUtil.toJsonStr(cacheValue), ttl, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入短链缓存失败，cacheKey={}", cacheKey, e);
        }
    }

    private void cacheNull(String cacheKey) {
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, "",
                    ShortLinkConstants.CACHE_NULL_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入空值缓存失败，cacheKey={}", cacheKey, e);
        }
    }

    private void evictCache(String shortUri) {
        try {
            stringRedisTemplate.delete(ShortLinkConstants.GOTO_CACHE_KEY + shortUri);
        } catch (Exception e) {
            log.warn("删除短链缓存失败，shortUri={}", shortUri, e);
        }
    }

    private boolean existsShortUri(String shortUri) {
        Integer count = shortLinkMapper.selectCount(Wrappers.<ShortLink>lambdaQuery()
                .eq(ShortLink::getShortUri, shortUri));
        return count != null && count > 0;
    }

    private ShortLink findByShortUri(String shortUri) {
        return shortLinkMapper.selectOne(Wrappers.<ShortLink>lambdaQuery()
                .eq(ShortLink::getShortUri, shortUri)
                .last("limit 1"));
    }

    private ShortLinkVO toVO(ShortLink link) {
        return new ShortLinkVO()
                .setId(link.getId())
                .setGroupId(link.getGroupId())
                .setOriginUrl(link.getOriginUrl())
                .setShortUri(link.getShortUri())
                .setFullShortUrl(link.getFullShortUrl())
                .setCreateType(link.getCreateType())
                .setValidDateType(link.getValidDateType())
                .setValidDate(link.getValidDate())
                .setState(link.getState())
                .setCreateTime(link.getCreateTime());
    }

    private String domainWithSlash() {
        return domain.endsWith("/") ? domain : domain + "/";
    }
}
