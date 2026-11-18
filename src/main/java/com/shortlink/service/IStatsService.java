package com.shortlink.service;

import com.shortlink.dto.Result;

public interface IStatsService {

    /**
     * 跳转成功后异步记录 PV/UV，失败不影响跳转。
     */
    void recordAsync(String shortUri, String ip);

    /**
     * 查询指定短链在 [start, end] 内的每日统计。
     */
    Result query(String code, String start, String end);
}
