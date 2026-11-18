package com.shortlink.job;

import com.shortlink.utils.BloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 布隆过滤器重建：启动时若尚未就绪先构建一次，之后每日 03:00 重建。
 */
@Slf4j
@Component
public class BloomRebuildJob {

    @Resource
    private BloomFilterService bloomFilterService;

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        try {
            if (!bloomFilterService.isReady()) {
                log.info("布隆过滤器尚未初始化，开始首次构建");
                bloomFilterService.rebuild();
            }
        } catch (Exception e) {
            log.error("启动时构建布隆过滤器失败，将退化为查 DB", e);
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void rebuildDaily() {
        try {
            bloomFilterService.rebuild();
        } catch (Exception e) {
            log.error("每日重构布隆过滤器失败", e);
        }
    }
}
