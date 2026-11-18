package com.shortlink.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class ShortLinkVO {

    private Long id;

    private Long groupId;

    private String originUrl;

    private String shortUri;

    private String fullShortUrl;

    private Integer createType;

    private Integer validDateType;

    private LocalDateTime validDate;

    private Integer state;

    private LocalDateTime createTime;
}
