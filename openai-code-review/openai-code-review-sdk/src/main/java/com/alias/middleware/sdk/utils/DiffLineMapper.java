package com.alias.middleware.sdk.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.TreeSet;

/**
 * Utilities to parse unified diffs and validate/fix head-side (RIGHT) line numbers.
 *
 * Supports typical git unified diff format with file headers and hunk headers:
 *   diff --git a/path/File.java b/path/File.java
 *   --- a/path/File.java
 *   +++ b/path/File.java
 *   @@ -a,b +c,d @@ optional heading
 *    context line
 *   -removed line
 *   +added line
 *
 * Semantics:
 * - Head/new file valid lines are those present in the new file after patch is applied:
 *   context ' ' lines and added '+' lines. Removed '-' lines do not exist in head.
 * - Deleted files will have "+++ /dev/null" and therefore no valid head lines.
 *
 * Typical usage:
 *   DiffLineMapper.Index idx = DiffLineMapper.index(diffText);
 *   boolean valid = DiffLineMapper.isValidHeadLine(idx, "src/A.java", 42);
 *   int fixed = DiffLineMapper.fixHeadLineOrOriginal(idx, "src/A.java", 42);
 */
public final class DiffLineMapper {

    private DiffLineMapper() {}

    /**
     * Immutable index that stores, per-path, the set of valid head-side line numbers.
     */
    public static final class Index {
        private final Map<String, NavigableSet<Integer>> pathToNewValidLines;

        private Index(Map<String, NavigableSet<Integer>> pathToNewValidLines) {
            this.pathToNewValidLines = pathToNewValidLines;
        }

        public NavigableSet<Integer> getValidLines(String path) {
            return pathToNewValidLines.getOrDefault(normalizePath(path), new TreeSet<>());
        }

        public List<Integer> getValidLinesList(String path) {
            NavigableSet<Integer> set = getValidLines(path);
            return new ArrayList<>(set);
        }
    }

    /**
     * Build an index from a unified diff string.
     */
    public static Index index(String unifiedDiff) {
        Objects.requireNonNull(unifiedDiff, "unifiedDiff");
        Map<String, NavigableSet<Integer>> pathToValid = new HashMap<>();

        String currentNewPath = null;
        boolean currentFileDeleted = false;

        int cursorNew = -1;
        int cursorOld = -1;
        boolean inHunk = false;

        String[] lines = unifiedDiff.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Detect file header: +++ b/..., +++ /dev/null
            if (line.startsWith("+++ ")) {
                String pathRaw = line.substring(4).trim();
                if ("/dev/null".equals(pathRaw)) {
                    currentNewPath = null;
                    currentFileDeleted = true;
                } else {
                    currentNewPath = stripPrefix(pathRaw, "b/");
                    currentFileDeleted = false;
                    pathToValid.computeIfAbsent(currentNewPath, k -> new TreeSet<>());
                }
                // Reset hunk state on new file
                inHunk = false;
                cursorNew = -1;
                cursorOld = -1;
                continue;
            }

            // Detect hunk header: @@ -a,b +c,d @@
            if (line.startsWith("@@ ")) {
                inHunk = false;
                cursorNew = -1;
                cursorOld = -1;
                int plusIdx = line.indexOf('+');
                int secondAt = line.indexOf("@@", 3); // skip initial "@@ "
                if (plusIdx >= 0 && secondAt > plusIdx) {
                    String afterPlus = line.substring(plusIdx + 1, secondAt).trim(); // c,d
                    String[] parts = afterPlus.split(",");
                    int startNew = safeParseInt(parts[0], 1);
                    cursorNew = startNew;
                    // old part for completeness
                    int minusIdx = line.indexOf('-', 3);
                    if (minusIdx >= 0) {
                        String oldRange = line.substring(minusIdx + 1, line.indexOf(' ', minusIdx + 1)).trim(); // a,b
                        String[] oldParts = oldRange.split(",");
                        int startOld = safeParseInt(oldParts[0], 1);
                        cursorOld = startOld;
                    }
                    // Enter hunk parsing if we have a current file and not deleted
                    inHunk = (currentNewPath != null && !currentFileDeleted);
                    // If lenNew == 0, the hunk introduces no lines into new file; we still step through
                }
                continue;
            }

            // Parse hunk body
            if (inHunk && currentNewPath != null) {
                NavigableSet<Integer> validSet = pathToValid.computeIfAbsent(currentNewPath, k -> new TreeSet<>());
                if (line.startsWith(" ")) {
                    // Context line: exists in new and old
                    if (cursorNew >= 1) {
                        validSet.add(cursorNew);
                        cursorNew++;
                    }
                    if (cursorOld >= 1) {
                        cursorOld++;
                    }
                } else if (line.startsWith("+")) {
                    // Added line: exists only in new
                    if (cursorNew >= 1) {
                        validSet.add(cursorNew);
                        cursorNew++;
                    }
                } else if (line.startsWith("-")) {
                    // Removed line: does not exist in new
                    if (cursorOld >= 1) {
                        cursorOld++;
                    }
                } else if (line.startsWith("\\ No newline at end of file")) {
                    // ignore
                } else {
                    // Non-standard content; end of hunk or new headers may follow
                }
            }
        }

        return new Index(pathToValid);
    }

    /**
     * Check whether a given head-side line number is valid for a path in the diff.
     */
    public static boolean isValidHeadLine(Index index, String path, int headLine) {
        if (index == null || path == null || headLine < 1) {
            return false;
        }
        NavigableSet<Integer> set = index.getValidLines(path);
        return set.contains(headLine);
    }

    /**
     * Find the nearest valid head-side line number to the requested line in the same file.
     * Preference: exact -> lower -> higher.
     */
    public static OptionalInt nearestValidHeadLine(Index index, String path, int requestedHeadLine) {
        if (index == null || path == null || requestedHeadLine < 1) {
            return OptionalInt.empty();
        }
        NavigableSet<Integer> set = index.getValidLines(path);
        if (set.isEmpty()) {
            return OptionalInt.empty();
        }
        if (set.contains(requestedHeadLine)) {
            return OptionalInt.of(requestedHeadLine);
        }
        Integer lower = set.floor(requestedHeadLine);
        if (lower != null) {
            return OptionalInt.of(lower);
        }
        Integer higher = set.ceiling(requestedHeadLine);
        if (higher != null) {
            return OptionalInt.of(higher);
        }
        return OptionalInt.empty();
    }

    /**
     * If the given head line is invalid, return the nearest valid one; otherwise return original.
     * If none found, returns -1.
     */
    public static int fixHeadLineOrOriginal(Index index, String path, int requestedHeadLine) {
        OptionalInt maybe = nearestValidHeadLine(index, path, requestedHeadLine);
        return maybe.isPresent() ? maybe.getAsInt() : -1;
    }

    /**
     * Get all valid head-side line numbers for a path (sorted ascending).
     */
    public static List<Integer> listValidHeadLines(Index index, String path) {
        if (index == null || path == null) {
            return Collections.emptyList();
        }
        return index.getValidLinesList(path);
    }

    private static String normalizePath(String path) {
        if (path == null) return null;
        String p = path.trim();
        if (p.startsWith("./")) {
            p = p.substring(2);
        }
        p = stripPrefix(p, "a/");
        p = stripPrefix(p, "b/");
        return p;
    }

    private static String stripPrefix(String s, String prefix) {
        if (s == null) return null;
        if (s.startsWith(prefix)) {
            return s.substring(prefix.length());
        }
        return s;
    }

    private static int safeParseInt(String s, int defaultValue) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}


