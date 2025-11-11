package com.alias.middleware.sdk.utils;

public final class SeverityUtils {

    private SeverityUtils() {}

    public static int severityRank(String severity) {
        if (severity == null) {
            return 2;
        }
        String s = severity.toLowerCase();
        if ("critical".equals(s)) return 0;
        if ("major".equals(s)) return 1;
        if ("minor".equals(s)) return 2;
        if ("suggestion".equals(s)) return 3;
        return 2;
    }
}


