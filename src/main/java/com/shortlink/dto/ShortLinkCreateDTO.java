package com.shortlink.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShortLinkCreateDTO {

    private String originUrl;

    private Long groupId;

    /**
     * 自定义后缀，为空则自动生成
     */
    private String customCode;

    /**
     * 过期时间，为空表示永久有效
     */
    private LocalDateTime validDate;
}
