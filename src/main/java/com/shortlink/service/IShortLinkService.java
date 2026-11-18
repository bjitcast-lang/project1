package com.shortlink.service;

import com.shortlink.dto.GotoResult;
import com.shortlink.dto.Result;
import com.shortlink.dto.ShortLinkCreateDTO;
import com.shortlink.dto.ShortLinkUpdateDTO;

public interface IShortLinkService {

    Result create(ShortLinkCreateDTO dto);

    Result page(int current, int size, Long groupId);

    Result update(ShortLinkUpdateDTO dto);

    Result remove(Long id);

    /**
     * 跳转查询：缓存 -> 空值缓存 -> 布隆过滤器 -> DB。
     */
    GotoResult gotoUrl(String shortUri);
}
