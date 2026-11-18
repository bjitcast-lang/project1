package com.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_id_segment")
public class IdSegment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "biz_tag", type = IdType.INPUT)
    private String bizTag;

    private Long maxId;

    private Integer step;

    private LocalDateTime updateTime;
}
