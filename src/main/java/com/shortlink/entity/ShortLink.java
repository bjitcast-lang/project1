package com.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_short_link")
public class ShortLink implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 号段发号器生成，既是主键也用于生成短码
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long groupId;

    private String originUrl;

    private String shortUri;

    private String fullShortUrl;

    /**
     * 0 自动生成 1 自定义
     */
    private Integer createType;

    /**
     * 0 永久 1 自定义过期时间
     */
    private Integer validDateType;

    private LocalDateTime validDate;

    /**
     * 1 启用 0 禁用
     */
    private Integer state;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
