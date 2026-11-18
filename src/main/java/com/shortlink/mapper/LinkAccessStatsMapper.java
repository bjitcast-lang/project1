package com.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortlink.entity.LinkAccessStats;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

public interface LinkAccessStatsMapper extends BaseMapper<LinkAccessStats> {

    /**
     * PV/UV 按 (full_short_url, stat_date) upsert，重复落库只覆盖最新值。
     */
    @Insert("INSERT INTO t_link_access_stats (full_short_url, stat_date, pv, uv) "
            + "VALUES (#{fullShortUrl}, #{statDate}, #{pv}, #{uv}) "
            + "ON DUPLICATE KEY UPDATE pv = VALUES(pv), uv = VALUES(uv)")
    int upsert(@Param("fullShortUrl") String fullShortUrl,
               @Param("statDate") LocalDate statDate,
               @Param("pv") int pv,
               @Param("uv") int uv);
}
