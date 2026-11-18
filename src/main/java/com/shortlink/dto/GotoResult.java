package com.shortlink.dto;

import lombok.Data;

/**
 * 跳转结果：status=200 时 originUrl 有效；404 不存在；410 已过期。
 */
@Data
public class GotoResult {

    private int status;

    private String originUrl;

    public static GotoResult ok(String originUrl) {
        GotoResult result = new GotoResult();
        result.setStatus(200);
        result.setOriginUrl(originUrl);
        return result;
    }

    public static GotoResult notFound() {
        GotoResult result = new GotoResult();
        result.setStatus(404);
        return result;
    }

    public static GotoResult gone() {
        GotoResult result = new GotoResult();
        result.setStatus(410);
        return result;
    }
}
