package com.byw.common.core.constant;

public class CommonConstants {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_DELETED = -1;

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public static final long TOKEN_EXPIRE_MS = 24 * 60 * 60 * 1000L;

    private CommonConstants() {}
}
