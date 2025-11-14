package com.alias.rag.dev.tech.trigger.utils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RagUtils {

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private PgVectorStore pgVectorStore;

    @Resource
    private GitUtils gitUtils;

    public void indexRepositoryFiles(Path repoPath, String repoName) throws IOException {

        Set<String> ALLOWED = Set.of(
                ".md", ".txt", ".java", ".js", ".ts", ".go", ".py", ".kt",
                ".xml", ".yaml", ".yml", ".json"
        );

        Files.walkFileTree(repoPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                String filename = file.getFileName().toString().toLowerCase();
                if (ALLOWED.stream().noneMatch(filename::endsWith)) return FileVisitResult.CONTINUE;

                Path relativePath = repoPath.relativize(file);
                String filePath = relativePath.toString().replace("\\", "/");

                try {
                    TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
                    List<Document> docs = reader.get();
                    List<Document> chunks = tokenTextSplitter.apply(docs);

                    chunks.forEach(d -> {
                        d.getMetadata().put("repo", repoName);
                        d.getMetadata().put("id", filePath);
                    });

                    pgVectorStore.accept(chunks);

                } catch (Exception e) {
                    log.error("[{}] Failed to index file {}: {}", repoName, filePath, e.getMessage());
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void indexRepositoryFilesIncremental(Path repoPath, String repoName, Git git) throws IOException {
        // 允许索引的文件类型
        Set<String> ALLOWED = Set.of(
                ".md", ".txt", ".java", ".js", ".ts", ".go", ".py", ".kt",
                ".xml", ".yaml", ".yml", ".json"
        );

        // 1. 获取上一次索引 commit
        String lastIndexedCommit = gitUtils.loadIndexedCommit(repoName);
        if (lastIndexedCommit == null) {
            log.info("No previous commit found, full indexing {}", repoName);
            indexRepositoryFiles(repoPath, repoName);
            return;
        }

        Repository repository = git.getRepository();
        ObjectId headCommitObj = repository.resolve("HEAD");
        String headCommit = headCommitObj.name();

        if (headCommit.equals(lastIndexedCommit)) {
            log.info("No changes detected for {} (commit: {})", repoName, headCommit);
            return;
        }

        log.info("Incremental indexing {} from {} → {}", repoName, lastIndexedCommit, headCommit);

        // 2. 获取 diff
        ObjectId oldCommitObj = repository.resolve(lastIndexedCommit);

        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit oldRev = walk.parseCommit(oldCommitObj);
            RevCommit newRev = walk.parseCommit(headCommitObj);

            try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                df.setRepository(repository);
                List<DiffEntry> diffs = df.scan(oldRev.getTree(), newRev.getTree());

                for (DiffEntry diff : diffs) {
                    DiffEntry.ChangeType type = diff.getChangeType();
                    String path;

                    switch (type) {
                        case ADD:
                            path = diff.getNewPath();
                            if (!ALLOWED.stream().anyMatch(path.toLowerCase()::endsWith)) continue;
                            indexFile(repoPath, repoName, path);
                            break;

                        case MODIFY:
                            path = diff.getNewPath();
                            if (!ALLOWED.stream().anyMatch(path.toLowerCase()::endsWith)) continue;

                            // 先查出对应文档并删除
                            String docId = repoName + ":" + path.replace("\\", "/");
                            deleteDocumentById(docId);

                            // 再插入新向量
                            indexFile(repoPath, repoName, path);
                            break;

                        case DELETE:
                            path = diff.getOldPath();
                            String delDocId = repoName + ":" + path.replace("\\", "/");
                            deleteDocumentById(delDocId);
                            log.info("[{}] Deleted vector for removed file: {}", repoName, path);
                            break;

                        default:
                            break;
                    }
                }
            }
        }

        // 3. 更新 commit
        gitUtils.saveIndexedCommit(repoName, headCommit);
        log.info("Updated last indexed commit for {}: {}", repoName, headCommit);
    }

    /** 删除指定 docId 对应的向量 */
    private void deleteDocumentById(String docId) {
        SearchRequest request = SearchRequest.builder()
                .query("search all")
                .topK(10)
                .filterExpression(new FilterExpressionBuilder().eq("id", docId).build())
                .build();

        List<Document> documents = pgVectorStore.similaritySearch(request);
        if (!documents.isEmpty()) {
            List<String> ids = documents.stream().map(Document::getId).collect(Collectors.toList());
            pgVectorStore.delete(ids);
            log.info("Deleted document by id: {}", docId);
        }
    }

    /** 索引单个文件 */
    private void indexFile(Path repoPath, String repoName, String path) {
        Path filePath = repoPath.resolve(path);
        log.info("[{}] Indexing file: {}", repoName, path);
        try {
            TikaDocumentReader reader = new TikaDocumentReader(new PathResource(filePath));
            List<Document> docs = reader.get();
            List<Document> chunks = tokenTextSplitter.apply(docs);

            for (Document d : chunks) {
                String docId = repoName + ":" + path.replace("\\", "/");
                d.getMetadata().put("id", docId);
                d.getMetadata().put("repo", repoName);
            }

            pgVectorStore.accept(chunks);
        } catch (Exception e) {
            log.error("Failed to index file {}: {}", path, e.getMessage());
        }
    }
    /**
     * 删除指定仓库的最后索引 commit 信息，以及对应向量
     */
    public void deleteIndexedCommit(String repoName) throws IOException {
        // 删除仓库所有向量
        Filter.Expression filter = new FilterExpressionBuilder()
                .eq("repo", repoName)
                .build();
        pgVectorStore.delete(filter);
        log.info("Deleted all vectors for repository {}", repoName);
    }

    /**
     * 根据代码片段返回上下文信息（检索向量库匹配片段）
     */
    public String reviewCodeContext(String repoName, String code) throws IOException {
        // 1. 将代码分块
        Document doc = new Document(code);
        List<Document> chunks = tokenTextSplitter.apply(List.of(doc));

        List<String> results = new ArrayList<>();

        for (Document chunk : chunks) {
            // 2. 构建过滤条件：knowledge == repoName
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            Filter.Expression filter = builder.eq("repo", repoName).build();

            // 3. 构建搜索请求
            SearchRequest request = SearchRequest.builder()
                    .query(chunk.getText())
                    .topK(5)
                    .filterExpression(filter)
                    .build();

            // 4. 执行相似度搜索
            List<Document> matched = pgVectorStore.similaritySearch(request);
            for (Document m : matched) {
                results.add(m.getFormattedContent());
            }
        }

        // 5. 拼接返回上下文
        return String.join("\n---\n", results);
    }

    /**
     * 获取仓库的 Git tag 列表
     */
    public List<String> getRepositoryTags(String repoName) throws IOException {
        List<String> tags = new ArrayList<>();
        return tags;
    }

}
