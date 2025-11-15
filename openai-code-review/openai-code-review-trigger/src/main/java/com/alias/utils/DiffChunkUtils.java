package com.alias.utils;

import java.util.ArrayList;
import java.util.List;

public final class DiffChunkUtils {

    private DiffChunkUtils() {
    }

    /**
     * 将 unified diff 按文件块拆分，并尽量在 maxPerChunk 限制内进行聚合。
     */
    public static List<String> splitDiffByFileBlocks(String unifiedDiff, int maxPerChunk) {
        List<String> blocks = new ArrayList<>();
        if (unifiedDiff == null || unifiedDiff.isEmpty()) {
            return blocks;
        }
        String marker = "\ndiff --git ";
        List<String> parts = new ArrayList<>();
        int pos = 0;
        int next;
        while ((next = unifiedDiff.indexOf(marker, pos + (pos == 0 ? 0 : 1))) >= 0) {
            String piece = unifiedDiff.substring(pos, next);
            if (!piece.isEmpty()) parts.add(piece);
            pos = next + 1;
        }
        String last = unifiedDiff.substring(pos);
        if (!last.isEmpty()) parts.add(last);
        List<String> fileBlocks = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i);
            if (i == 0) {
                if (p.startsWith("diff --git ") || p.contains("\ndiff --git ")) {
                    fileBlocks.add(p);
                } else {
                    fileBlocks.add(p);
                }
            } else {
                String fixed = p.startsWith("diff --git ") ? p : ("diff --git " + p);
                fileBlocks.add(fixed);
            }
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder curr = new StringBuilder();
        for (String fb : fileBlocks) {
            if (curr.length() == 0) {
                if (fb.length() <= maxPerChunk) {
                    curr.append(fb);
                } else {
                    chunks.add(fb.substring(0, Math.min(fb.length(), maxPerChunk)));
                    int start = Math.min(fb.length(), maxPerChunk);
                    while (start < fb.length()) {
                        int end = Math.min(fb.length(), start + maxPerChunk);
                        chunks.add(fb.substring(start, end));
                        start = end;
                    }
                }
            } else if (curr.length() + fb.length() <= maxPerChunk) {
                curr.append(fb);
            } else {
                chunks.add(curr.toString());
                curr.setLength(0);
                if (fb.length() <= maxPerChunk) {
                    curr.append(fb);
                } else {
                    chunks.add(fb.substring(0, Math.min(fb.length(), maxPerChunk)));
                    int start = Math.min(fb.length(), maxPerChunk);
                    while (start < fb.length()) {
                        int end = Math.min(fb.length(), start + maxPerChunk);
                        chunks.add(fb.substring(start, end));
                        start = end;
                    }
                }
            }
        }
        if (curr.length() > 0) {
            chunks.add(curr.toString());
        }
        return chunks;
    }
}

