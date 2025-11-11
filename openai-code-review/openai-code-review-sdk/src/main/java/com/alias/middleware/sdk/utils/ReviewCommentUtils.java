package com.alias.middleware.sdk.utils;

/**
 * Helpers to build review comment content and rank confidence levels.
 */
public final class ReviewCommentUtils {

    private ReviewCommentUtils() {}

    public static String buildTopLevelComment(String summary, String generalReview) {
        StringBuilder sb = new StringBuilder();
        if (summary != null && !summary.isEmpty()) {
            sb.append("### PR 变更摘要\n").append(summary).append("\n\n");
        }
        if (generalReview != null && !generalReview.isEmpty()) {
            sb.append("### 综合审查\n").append(generalReview);
        }
        return sb.length() == 0 ? "AI 代码审查无可用摘要或综合意见。" : sb.toString();
    }

    public static int confidenceRank(String confidence) {
        if (confidence == null) return 1; // unknown/mid
        String c = confidence.trim().toLowerCase();
        if ("high".equals(c) || "high confidence".equals(c)) return 0;
        if ("low".equals(c) || "low confidence".equals(c)) return 2;
        return 1;
    }
}


