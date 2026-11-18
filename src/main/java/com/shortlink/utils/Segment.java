package com.shortlink.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 一个号段：闭区间 [min, max]。
 */
@Data
@AllArgsConstructor
public class Segment {

    private long min;

    private long max;
}
