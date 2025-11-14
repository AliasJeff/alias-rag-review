package com.alias.rag.dev.tech.trigger.utils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
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
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
                String docId = repoName + ":" + filePath;

                try {
                    TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
                    List<Document> docs = reader.get();
                    List<Document> chunks = tokenTextSplitter.apply(docs);

                    chunks.forEach(d -> {
                        d.getMetadata().put("knowledge", repoName);
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

    public void indexRepositoryFilesIncremental(Path repoPath, String repoName, Git git) throws IOException, GitAPIException {

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

                List<String> idsToDelete = new ArrayList<>();

                for (DiffEntry diff : diffs) {
                    DiffEntry.ChangeType type = diff.getChangeType();
                    String path;

                    switch (type) {
                        case ADD:
                        case MODIFY:
                            path = diff.getNewPath();
                            if (!ALLOWED.stream().anyMatch(path.toLowerCase()::endsWith)) continue;

                            Path filePath = repoPath.resolve(path);
                            log.info("[{}] Indexing {} file: {}", repoName, type, path);
                            try {
                                TikaDocumentReader reader = new TikaDocumentReader(new PathResource(filePath));
                                List<Document> docs = reader.get();
                                List<Document> chunks = tokenTextSplitter.apply(docs);

                                List<String> fileIdList = new ArrayList<>();
                                for (Document d : chunks) {
                                    String docId = repoName + ":" + path.replace("\\", "/");
                                    fileIdList.add(docId);
                                    d.getMetadata().put("id", docId);          // 用 metadata 记录 id
                                    d.getMetadata().put("knowledge", repoName);
                                }

                                pgVectorStore.accept(chunks);

                            } catch (Exception e) {
                                log.error("Failed to index file {}: {}", path, e.getMessage());
                            }
                            break;

                        case DELETE:
                            path = diff.getOldPath();
                            String docId = repoName + ":" + path.replace("\\", "/");
                            idsToDelete.add(docId);
                            log.info("[{}] Scheduled delete vector for removed file: {}", repoName, path);
                            break;

                        default:
                            break;
                    }
                }

                // 删除被删除的文件向量
                if (!idsToDelete.isEmpty()) {
                    // FIXME: idsToDelete 是 metaData 中的值
                    pgVectorStore.delete(idsToDelete);
                    log.info("[{}] Deleted {} vectors for removed files", repoName, idsToDelete.size());
                }
            }
        }

        // 3. 更新 commit
        gitUtils.saveIndexedCommit(repoName, headCommit);
        log.info("Updated last indexed commit for {}: {}", repoName, headCommit);
    }

}
