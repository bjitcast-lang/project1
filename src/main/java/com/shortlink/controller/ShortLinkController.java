package com.shortlink.controller;

import com.shortlink.dto.Result;
import com.shortlink.dto.ShortLinkCreateDTO;
import com.shortlink.dto.ShortLinkUpdateDTO;
import com.shortlink.service.IShortLinkService;
import com.shortlink.utils.IpUtil;
import com.shortlink.utils.ShortLinkConstants;
import com.shortlink.utils.TokenBucketLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 短链管理接口，由 ApiKeyInterceptor 校验 X-Api-Key。
 */
@Slf4j
@RestController
@RequestMapping("/api/shortlink")
public class ShortLinkController {

    @Resource
    private IShortLinkService shortLinkService;

    @Resource
    private TokenBucketLimiter tokenBucketLimiter;

    @PostMapping("/create")
    public Object create(@RequestBody ShortLinkCreateDTO dto, HttpServletRequest request) {
        String ip = IpUtil.getIp(request);
        try {
            if (!tokenBucketLimiter.tryAcquire(ShortLinkConstants.LIMIT_CREATE_KEY + ip)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Result.fail("创建过于频繁，请稍后再试"));
            }
        } catch (Exception e) {
            // 限流组件故障时放行，优先保证可用性
            log.warn("限流组件异常，放行创建请求", e);
        }
        return shortLinkService.create(dto);
    }

    @GetMapping("/page")
    public Result page(@RequestParam(value = "current", defaultValue = "1") int current,
                       @RequestParam(value = "size", defaultValue = "10") int size,
                       @RequestParam(value = "groupId", required = false) Long groupId) {
        return shortLinkService.page(current, size, groupId);
    }

    @PostMapping("/update")
    public Result update(@RequestBody ShortLinkUpdateDTO dto) {
        return shortLinkService.update(dto);
    }

    @DeleteMapping("/{id}")
    public Result remove(@PathVariable("id") Long id) {
        return shortLinkService.remove(id);
    }
}
