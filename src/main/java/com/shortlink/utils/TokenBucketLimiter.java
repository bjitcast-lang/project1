package com.shortlink.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * 基于 Redis + Lua 的令牌桶限流。
 */
@Component
public class TokenBucketLimiter {

    private static final DefaultRedisScript<Long> SCRIPT;

    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        SCRIPT.setResultType(Long.class);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${shortlink.limit.capacity:20}")
    private int capacity;

    @Value("${shortlink.limit.rate:1}")
    private int rate;

    public boolean tryAcquire(String key) {
        Long allowed = stringRedisTemplate.execute(
                SCRIPT,
                Collections.singletonList(key),
                String.valueOf(capacity),
                String.valueOf(rate),
                String.valueOf(System.currentTimeMillis()),
                "3600");
        return allowed != null && allowed == 1L;
    }
}
