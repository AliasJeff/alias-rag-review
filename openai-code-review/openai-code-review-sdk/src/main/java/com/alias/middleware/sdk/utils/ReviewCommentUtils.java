package com.alias.middleware.sdk.utils;

/**
 * Helpers to build review comment content and rank confidence levels.
 */
public final class ReviewCommentUtils {

    private ReviewCommentUtils() {}

    public static String buildTopLevelComment(String summary, String generalReview) {
        StringBuilder sb = new StringBuilder();
        if (summary != null && !summary.isEmpty()) {
            sb.append("### PR Change Summary\n").append(summary).append("\n\n");
        }
        if (generalReview != null && !generalReview.isEmpty()) {
            sb.append("### General Review\n").append(generalReview);
        }
        return sb.length() == 0 ? "AI code review has no available summary or general review." : sb.toString();
    }

    public static int confidenceRank(String confidence) {
        if (confidence == null) return 1; // unknown/mid
        String c = confidence.trim().toLowerCase();
        if ("high".equals(c) || "high confidence".equals(c)) return 0;
        if ("low".equals(c) || "low confidence".equals(c)) return 2;
        return 1;
    }
}


