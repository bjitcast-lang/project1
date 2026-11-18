package com.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortlink.entity.IdSegment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface IdSegmentMapper extends BaseMapper<IdSegment> {

    /**
     * 原子推进号段。行锁保证多实例并发时不发重复号。
     */
    @Update("UPDATE t_id_segment SET max_id = max_id + step WHERE biz_tag = #{bizTag}")
    int advance(@Param("bizTag") String bizTag);

    @Select("SELECT max_id FROM t_id_segment WHERE biz_tag = #{bizTag}")
    Long selectMaxId(@Param("bizTag") String bizTag);

    @Select("SELECT step FROM t_id_segment WHERE biz_tag = #{bizTag}")
    Integer selectStep(@Param("bizTag") String bizTag);
}
