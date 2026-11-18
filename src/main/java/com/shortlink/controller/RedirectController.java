package com.shortlink.controller;

import com.shortlink.dto.GotoResult;
import com.shortlink.service.IShortLinkService;
import com.shortlink.service.IStatsService;
import com.shortlink.utils.IpUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;

/**
 * 短链跳转。使用 302 而非 301，避免浏览器缓存导致访问统计丢失。
 */
@RestController
public class RedirectController {

    @Resource
    private IShortLinkService shortLinkService;

    @Resource
    private IStatsService statsService;

    @GetMapping("/{shortUri}")
    public ResponseEntity<Void> redirect(@PathVariable("shortUri") String shortUri,
                                         HttpServletRequest request) {
        GotoResult result = shortLinkService.gotoUrl(shortUri);
        if (result.getStatus() == 200) {
            statsService.recordAsync(shortUri, IpUtil.getIp(request));
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(result.getOriginUrl()))
                    .build();
        }
        return ResponseEntity.status(result.getStatus()).build();
    }
}
