package com.alias.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VCSUtils {
    private static final Logger LOG = Logger.getLogger(VCSUtils.class.getName());
    private static final Pattern HUNK_HEADER = Pattern.compile("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");
    private static final int CONTEXT_LIMIT = 20;
    private static final String DIFF_FILE_PREFIX = "diff --git ";

    /**
     * 解析单个文件的 unified diff 文本为结构化对象。
     *
     * @param diffText    该文件的 unified diff 文本（只包含该文件的 diff）
     * @param filePath    新文件路径
     * @param oldFilePath 旧文件路径（可能为 null）
     * @return FileChanges 结构化变更对象
     */
    public static FileChanges parseSingleFileDiff(String diffText, String filePath, String oldFilePath) {
        FileChanges fileChanges = new FileChanges(filePath, oldFilePath);

        int oldLineNumStart = 0;
        int newLineNumStart = 0;
        int oldLineNumCurrent = 0;
        int newLineNumCurrent = 0;

        List<String> hunkContextLines = new ArrayList<>();

        String[] lines = diffText == null ? new String[0] : diffText.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("--- ") || line.startsWith("+++ ")) {
                continue;
            } else if (line.startsWith("@@ ")) {
                Matcher matcher = HUNK_HEADER.matcher(line);
                if (matcher.find()) {
                    oldLineNumStart = parseInt(matcher.group(1), 0);
                    newLineNumStart = parseInt(matcher.group(3), 0);
                    oldLineNumCurrent = oldLineNumStart;
                    newLineNumCurrent = newLineNumStart;
                    if (!hunkContextLines.isEmpty()) {
                        fileChanges.context.oldTextLines.addAll(hunkContextLines);
                        fileChanges.context.newTextLines.addAll(hunkContextLines);
                        hunkContextLines = new ArrayList<>();
                    }
                } else {
                    LOG.warning("警告: 无法解析 " + filePath + " 中的 hunk 标头: " + line);
                    oldLineNumStart = 0;
                    newLineNumStart = 0;
                    oldLineNumCurrent = 0;
                    newLineNumCurrent = 0;
                }
            } else if (line.startsWith("+")) {
                fileChanges.changes.add(Change.add(newLineNumCurrent, slice(line, 1)));
                newLineNumCurrent += 1;
            } else if (line.startsWith("-")) {
                fileChanges.changes.add(Change.delete(oldLineNumCurrent, slice(line, 1)));
                oldLineNumCurrent += 1;
            } else if (line.startsWith(" ")) {
                hunkContextLines.add(oldLineNumCurrent + " -> " + newLineNumCurrent + ": " + slice(line, 1));
                oldLineNumCurrent += 1;
                newLineNumCurrent += 1;
            } else {
                // 其他行（例如 \ No newline at end of file），忽略
            }
        }

        if (!hunkContextLines.isEmpty()) {
            fileChanges.context.oldTextLines.addAll(hunkContextLines);
            fileChanges.context.newTextLines.addAll(hunkContextLines);
        }

        // 限制上下文行数，并拼接为字符串
        fileChanges.context.oldText = joinTail(fileChanges.context.oldTextLines, CONTEXT_LIMIT);
        fileChanges.context.newText = joinTail(fileChanges.context.newTextLines, CONTEXT_LIMIT);

        // 变更行数统计（仅统计 add/delete）
        int count = 0;
        for (Change c : fileChanges.changes) {
            if (c.type == ChangeType.ADD || c.type == ChangeType.DELETE) {
                count += 1;
            }
        }
        fileChanges.linesChanged = count;

        return fileChanges;
    }

    private static int parseInt(String text, int defaultValue) {
        try {
            return Integer.parseInt(text);
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private static String slice(String s, int from) {
        if (s == null) {
            return "";
        }
        if (from <= 0) {
            return s;
        }
        return s.length() > from ? s.substring(from) : "";
    }

    private static String joinTail(List<String> lines, int limit) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        int fromIdx = Math.max(0, lines.size() - Math.max(0, limit));
        StringBuilder sb = new StringBuilder();
        for (int i = fromIdx; i < lines.size(); i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    /**
     * 解析完整 unified diff（可能包含多个文件），按文件切分并生成结构化结果。
     *
     * @param unifiedDiff 完整 unified diff 文本
     * @return 每个文件对应一个 FileChanges
     */
    public static List<FileChanges> parseUnifiedDiff(String unifiedDiff) {
        List<FileChanges> results = new ArrayList<>();
        if (unifiedDiff == null || unifiedDiff.isEmpty()) {
            return results;
        }
        String[] lines = unifiedDiff.split("\\R", -1);

        StringBuilder currentBuffer = new StringBuilder();
        String currentOldPath = null;
        String currentNewPath = null;
        boolean inFile = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith(DIFF_FILE_PREFIX)) {
                // flush previous file
                if (inFile) {
                    results.add(flushOne(currentBuffer, currentNewPath, currentOldPath));
                    currentBuffer = new StringBuilder();
                    currentOldPath = null;
                    currentNewPath = null;
                }
                inFile = true;
                currentBuffer.append(line).append('\n');
                continue;
            }
            if (!inFile) {
                // skip headers before first file
                continue;
            }
            // track paths
            if (line.startsWith("--- ")) {
                currentOldPath = normalizePathFromMarker(line.substring(4));
            } else if (line.startsWith("+++ ")) {
                currentNewPath = normalizePathFromMarker(line.substring(4));
            }
            currentBuffer.append(line).append('\n');
        }
        if (inFile) {
            results.add(flushOne(currentBuffer, currentNewPath, currentOldPath));
        }
        return results;
    }

    private static FileChanges flushOne(StringBuilder buf, String newPath, String oldPath) {
        String fp = newPath != null ? newPath : (oldPath != null ? oldPath : "");
        return parseSingleFileDiff(buf.toString(), fp, oldPath);
    }

    private static String normalizePathFromMarker(String markerPath) {
        if (markerPath == null) {
            return null;
        }
        // markerPath examples: a/src/Main.java, /dev/null, "a/file with space.java"
        String p = markerPath.trim();
        // strip quotes if present
        if (p.length() >= 2 && ((p.charAt(0) == '"' && p.charAt(p.length() - 1) == '"') || (p.charAt(0) == '\'' && p.charAt(p.length() - 1) == '\''))) {
            p = p.substring(1, p.length() - 1);
        }
        if ("/dev/null".equals(p)) {
            return null;
        }
        if (p.startsWith("a/") || p.startsWith("b/")) {
            return p.substring(2);
        }
        return p;
    }

    // ===== 数据结构 =====

    public enum ChangeType {
        ADD, DELETE
    }

    public static final class Change {
        public final ChangeType type;
        public final Integer oldLine; // 对于 ADD 为 null
        public final Integer newLine; // 对于 DELETE 为 null
        public final String content;

        private Change(ChangeType type, Integer oldLine, Integer newLine, String content) {
            this.type = Objects.requireNonNull(type, "type");
            this.oldLine = oldLine;
            this.newLine = newLine;
            this.content = content == null ? "" : content;
        }

        public static Change add(Integer newLine, String content) {
            return new Change(ChangeType.ADD, null, newLine, content);
        }

        public static Change delete(Integer oldLine, String content) {
            return new Change(ChangeType.DELETE, oldLine, null, content);
        }

        @Override
        public String toString() {
            return "Change{" + "type='" + type + '\'' + ", oldLine='" + oldLine + '\'' + ", newLine='" + newLine + '\'' + ", content='" + content + '\'' + '}';
        }
    }

    public static final class Context {
        private final List<String> oldTextLines = new ArrayList<>();
        private final List<String> newTextLines = new ArrayList<>();
        public String oldText = "";
        public String newText = "";

        @Override
        public String toString() {
            return "Context{" + "oldTextLines='" + oldTextLines + '\'' + ", newTextLines='" + newTextLines + '\'' + ", oldText='" + oldText + '\'' + ", newText='" + newText + '\'' + '}';
        }
    }

    public static final class FileChanges {
        public final String path;
        public final String oldPath;
        public final List<Change> changes = new ArrayList<>();
        public final Context context = new Context();
        public int linesChanged = 0;

        public FileChanges(String path, String oldPath) {
            this.path = path;
            this.oldPath = oldPath;
        }


        @Override
        public String toString() {
            return "FileChanges{" + "path='" + path + '\'' + ", oldPath='" + oldPath + '\'' + ", changes='" + changes.toString() + '\'' + ", context='" + context + '\'' + ", linesChanged'" + linesChanged + '\'' + '}';
        }
    }
}
