package com.shortlink.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShortLinkUpdateDTO {

    private Long id;

    private String originUrl;

    private Long groupId;

    private LocalDateTime validDate;

    /**
     * 1 启用 0 禁用
     */
    private Integer state;
}
