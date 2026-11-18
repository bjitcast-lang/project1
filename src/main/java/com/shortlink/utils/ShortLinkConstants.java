package com.shortlink.utils;

public final class ShortLinkConstants {

    private ShortLinkConstants() {
    }

    public static final String GOTO_CACHE_KEY = "shortlink:goto:";
    public static final long CACHE_NULL_TTL_MINUTES = 2L;
    public static final long CACHE_TTL_MINUTES = 30L;
    public static final long CACHE_LOGICAL_EXPIRE_MILLIS = 25 * 60 * 1000L;
    public static final String GOTO_LOCK_KEY = "lock:shortlink:goto:";

    public static final String PV_KEY = "shortlink:pv:";
    public static final String UV_KEY = "shortlink:uv:";
    public static final String STATS_DIRTY_KEY = "shortlink:stats:dirty";
    public static final long STATS_KEY_TTL_HOURS = 48L;

    public static final String LIMIT_CREATE_KEY = "shortlink:limit:create:";
}
