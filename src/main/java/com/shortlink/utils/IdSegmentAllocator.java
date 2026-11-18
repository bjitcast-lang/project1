package com.shortlink.utils;

import com.shortlink.mapper.IdSegmentMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 号段分配：UPDATE 推进 max_id 再回查，行锁保证并发安全。
 * 单独成 Bean 是为了让 {@link Transactional} 通过代理生效。
 */
@Component
public class IdSegmentAllocator {

    @Resource
    private IdSegmentMapper idSegmentMapper;

    @Transactional(rollbackFor = Exception.class)
    public Segment allocate(String bizTag) {
        int updated = idSegmentMapper.advance(bizTag);
        if (updated == 0) {
            throw new IllegalStateException("号段不存在：" + bizTag);
        }
        Long maxId = idSegmentMapper.selectMaxId(bizTag);
        Integer step = idSegmentMapper.selectStep(bizTag);
        if (maxId == null || step == null) {
            throw new IllegalStateException("号段读取失败：" + bizTag);
        }
        return new Segment(maxId - step + 1, maxId);
    }
}
