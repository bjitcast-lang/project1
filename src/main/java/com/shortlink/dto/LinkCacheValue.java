package com.shortlink.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 跳转缓存的 value，逻辑过期由 expireTime 控制。
 */
@Data
@Accessors(chain = true)
public class LinkCacheValue {

    private String originUrl;

    private Integer state;

    /**
     * 业务过期时间（毫秒时间戳），null 表示永久有效
     */
    private Long validDate;

    /**
     * 逻辑过期时间，到期后异步重建，避免缓存击穿
     */
    private Long expireTime;
}
