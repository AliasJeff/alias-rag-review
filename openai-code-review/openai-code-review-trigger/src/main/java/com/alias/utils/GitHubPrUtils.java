package com.alias.utils;

import java.net.MalformedURLException;
import java.net.URL;

public class GitHubPrUtils {

    public static class PrInfo {
        public String owner;
        public String repo;
        public String repository; // owner/repo
        public String prNumber;

        @Override
        public String toString() {
            return "PrInfo{owner='" + owner + '\'' + ", repo='" + repo + '\'' + ", repository='" + repository + '\'' + ", prNumber='" + prNumber + '\'' + '}';
        }
    }

    /**
     * 传入 GitHub PR URL，自动解析出 owner、repo、prNumber。
     * 支持格式：
     * - https://github.com/{owner}/{repo}/pull/{number}
     * - https://github.com/{owner}/{repo}/pull/{number}/files
     * - https://github.com/{owner}/{repo}/pull/{number}?param=xxx
     */
    public static PrInfo parsePrUrl(String prUrl) {
        if (prUrl == null || prUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("prUrl is empty");
        }
        try {
            URL url = new URL(prUrl);
            String host = url.getHost();
            if (!"github.com".equalsIgnoreCase(host)) {
                throw new IllegalArgumentException("Only github.com is supported, got: " + host);
            }
            String path = url.getPath(); // /{owner}/{repo}/pull/{number}[...]
            if (path == null) {
                throw new IllegalArgumentException("Invalid PR url path");
            }
            String[] parts = path.split("/");
            // 期望: ["", owner, repo, "pull", number, ...]
            if (parts.length < 5) {
                throw new IllegalArgumentException("Invalid PR url path: " + path);
            }
            String owner = parts[1];
            String repo = parts[2];
            String pullLiteral = parts[3];
            if (!"pull".equalsIgnoreCase(pullLiteral)) {
                throw new IllegalArgumentException("Not a pull request url: " + path);
            }
            String number = parts[4];
            // 剥离 number 中可能的额外片段（? 或 #）
            int extraIdx = number.indexOf('?');
            if (extraIdx >= 0) {
                number = number.substring(0, extraIdx);
            }
            extraIdx = number.indexOf('#');
            if (extraIdx >= 0) {
                number = number.substring(0, extraIdx);
            }
            // 确保 number 只保留数字
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < number.length(); i++) {
                char c = number.charAt(i);
                if (Character.isDigit(c)) digits.append(c);
                else break;
            }
            if (digits.length() == 0) {
                throw new IllegalArgumentException("PR number not found in url: " + prUrl);
            }

            PrInfo info = new PrInfo();
            info.owner = owner;
            info.repo = repo;
            info.repository = owner + "/" + repo;
            info.prNumber = digits.toString();
            return info;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed PR url: " + prUrl, e);
        }
    }
}

