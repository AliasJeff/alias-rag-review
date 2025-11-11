package com.alias.middleware.sdk.infrastructure.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alias.middleware.sdk.utils.RandomStringUtils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class GitCommand {

    private final Logger logger = LoggerFactory.getLogger(GitCommand.class);

    private final String githubReviewLogUri;

    private final String githubToken;

    private final String project;

    private final String branch;

    private final String author;

    private final String message;

    public GitCommand(String githubReviewLogUri, String githubToken, String project, String branch, String author, String message) {
        this.githubReviewLogUri = githubReviewLogUri;
        this.githubToken = githubToken;
        this.project = project;
        this.branch = branch;
        this.author = author;
        this.message = message;
    }

    public String diff() throws IOException, InterruptedException {
        // openai.itedus.cn
        ProcessBuilder logProcessBuilder = new ProcessBuilder("git", "log", "-1", "--pretty=format:%H");
        logProcessBuilder.directory(new File("."));
        Process logProcess = logProcessBuilder.start();

        BufferedReader logReader = new BufferedReader(new InputStreamReader(logProcess.getInputStream()));
        String latestCommitHash = logReader.readLine();
        logReader.close();
        logProcess.waitFor();

        ProcessBuilder diffProcessBuilder = new ProcessBuilder("git", "diff", latestCommitHash + "^", latestCommitHash);
        diffProcessBuilder.directory(new File("."));
        Process diffProcess = diffProcessBuilder.start();

        StringBuilder diffCode = new StringBuilder();
        BufferedReader diffReader = new BufferedReader(new InputStreamReader(diffProcess.getInputStream()));
        String line;
        while ((line = diffReader.readLine()) != null) {
            diffCode.append(line).append("\n");
        }
        diffReader.close();

        int exitCode = diffProcess.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Failed to get diff, exit code:" + exitCode);
        }

        return diffCode.toString();
    }

    public String commitAndPush(String recommend) throws Exception {
        Git git = Git.cloneRepository()
                .setURI(githubReviewLogUri + ".git")
                .setDirectory(new File("repo"))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubToken, ""))
                .call();

        // 创建分支
        String dateFolderName = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File dateFolder = new File("repo/" + dateFolderName);
        if (!dateFolder.exists()) {
            dateFolder.mkdirs();
        }

        String fileName = project + "-" + branch + "-" + author + System.currentTimeMillis() + "-" + RandomStringUtils.randomNumeric(4) + ".md";
        File newFile = new File(dateFolder, fileName);
        try (FileWriter writer = new FileWriter(newFile)) {
            writer.write(recommend);
        }

        // 提交内容
        git.add().addFilepattern(dateFolderName + "/" + fileName).call();
        git.commit().setMessage("add code review new file" + fileName).call();
        git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubToken, "")).call();

        logger.info("openai-code-review git commit and push done! {}", fileName);

        return githubReviewLogUri + "/blob/main/" + dateFolderName + "/" + fileName;
    }

    public String getProject() {
        return project;
    }

    public String getBranch() {
        return branch;
    }

    public String getAuthor() {
        return author;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Fetch a specific ref from a remote (e.g. fetch 'main' from 'origin').
     * This is used by PR review flow and intentionally named differently
     * from existing 'diff()' to avoid ambiguity with the normal flow.
     */
    public void fetchRemoteRef(String remote, String ref) throws IOException, InterruptedException {
        String[] command = new String[] {"git", "fetch", remote, ref};
        logger.info("Executing git command: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File("."));
        Process p = pb.start();
        try (BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            String line;
            while ((line = err.readLine()) != null) {
                logger.debug(line);
            }
        }
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("Git command failed: " + String.join(" ", command) + ", exit=" + exit);
        }
        logger.info("Git command finished successfully: {}", String.join(" ", command));
    }

    /**
     * Get diff output between two remote refs using three-dot syntax:
     * git diff remote/base...remote/head
     * This is for PR review flow and named distinctly from 'diff()'.
     */
    public String diffRemoteRefsThreeDot(String remote, String baseRef, String headRef) throws IOException, InterruptedException {
        String[] command = new String[] {"git", "diff", remote + "/" + baseRef + "..."+ remote + "/" + headRef};
        logger.info("Executing git command (capture): {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File("."));
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append("\n");
            }
        }
        try (BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            String line;
            while ((line = err.readLine()) != null) {
                logger.debug(line);
            }
        }
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("Git command failed: " + String.join(" ", command) + ", exit=" + exit);
        }
        logger.info("Git command (capture) finished successfully: {}", String.join(" ", command));
        return out.toString();
    }

    /**
     * Get the commit SHA for a fetched remote ref, e.g. origin/main.
     * Prerequisite: the ref should have been fetched already.
     */
    public String getRemoteRefCommitSha(String remote, String ref) throws IOException, InterruptedException {
        String fullRef = remote + "/" + ref;
        String[] command = new String[] {"git", "rev-parse", fullRef};
        logger.info("Executing git command (capture single line): {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File("."));
        Process p = pb.start();
        String sha;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            sha = reader.readLine();
        }
        try (BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            String line;
            while ((line = err.readLine()) != null) {
                logger.debug(line);
            }
        }
        int exit = p.waitFor();
        if (exit != 0 || sha == null || sha.isEmpty()) {
            throw new RuntimeException("Git command failed: " + String.join(" ", command) + ", exit=" + exit);
        }
        logger.info("Resolved {} to commit {}", fullRef, sha);
        return sha;
    }
}
