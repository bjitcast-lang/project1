package com.shortlink.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Base62 短码工具：数字 id 与短码一一对应，左补 0 到 6 位。
 */
public final class ShortCodeUtil {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final int MIN_LENGTH = 6;

    private static final Pattern CUSTOM_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{4,16}$");

    /**
     * 系统保留字，避免和 API 路径冲突。
     */
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
            "api", "admin", "stats", "actuator", "login", "logout", "error"));

    private ShortCodeUtil() {
    }

    public static String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("id 不能为负数");
        }
        if (id == 0) {
            return "000000";
        }
        StringBuilder builder = new StringBuilder();
        long value = id;
        while (value > 0) {
            builder.append(ALPHABET.charAt((int) (value % 62)));
            value /= 62;
        }
        while (builder.length() < MIN_LENGTH) {
            builder.append('0');
        }
        return builder.reverse().toString();
    }

    public static boolean isValidCustom(String code) {
        return code != null && CUSTOM_PATTERN.matcher(code).matches();
    }

    public static boolean isReserved(String code) {
        return code != null && RESERVED.contains(code.toLowerCase());
    }
}
