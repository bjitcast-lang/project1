package com.shortlink.controller;

import com.shortlink.dto.Result;
import com.shortlink.service.IStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/shortlink")
public class StatsController {

    @Resource
    private IStatsService statsService;

    /**
     * @param code  短码
     * @param start 开始日期 yyyy-MM-dd，默认 6 天前
     * @param end   结束日期 yyyy-MM-dd，默认今天
     */
    @GetMapping("/stats")
    public Result stats(@RequestParam("code") String code,
                        @RequestParam(value = "start", required = false) String start,
                        @RequestParam(value = "end", required = false) String end) {
        return statsService.query(code, start, end);
    }
}
