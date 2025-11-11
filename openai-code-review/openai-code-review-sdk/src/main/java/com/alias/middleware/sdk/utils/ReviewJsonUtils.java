package com.alias.middleware.sdk.utils;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Utilities for safe JSON field access and payload extraction/serialization.
 */
public final class ReviewJsonUtils {

    private ReviewJsonUtils() {}

    public static String safeText(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        String s = n.asText();
        return (s != null && !s.trim().isEmpty()) ? s : null;
    }

    public static Integer safeInt(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        try {
            return n.asInt();
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractJsonPayload(String text) {
        if (text == null) return "";
        String s = text.trim();
        // Handle fenced code blocks ```json ... ```
        int fenceStart = s.indexOf("```");
        if (fenceStart >= 0) {
            int fenceEnd = s.indexOf("```", fenceStart + 3);
            if (fenceEnd > fenceStart) {
                String fenced = s.substring(fenceStart + 3, fenceEnd);
                // Remove optional language hint like "json" or "JSON"
                String trimmed = fenced.trim();
                if (trimmed.regionMatches(true, 0, "json", 0, Math.min(4, trimmed.length()))) {
                    trimmed = trimmed.substring(Math.min(4, trimmed.length())).trim();
                }
                return sliceFirstJsonObject(trimmed);
            }
        }
        // No fences; try to slice first JSON object
        return sliceFirstJsonObject(s);
    }

    public static String sliceFirstJsonObject(String s) {
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            return s.substring(start, end + 1).trim();
        }
        // As a last resort, return original trimmed string
        return s.trim();
    }

    public static String toJsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}


