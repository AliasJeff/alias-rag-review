package com.alias.rag.dev.tech.trigger.utils;

import com.alias.rag.dev.tech.api.dto.RagRepoDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RepositoryUtils {

  @Value("${repo.base-path}")
  private String repoBasePath;

  @Value("${github.token}")
  private String githubToken;

  @Value("${github.username}")
  private String githubUsername;

  @Resource private TokenTextSplitter tokenTextSplitter;

  @Resource private PgVectorStore pgVectorStore;

  private static final ObjectMapper mapper = new ObjectMapper();

  // ==================== Git Utils Methods ====================

  public static String extractProjectName(String repoUrl) {
    String[] parts = repoUrl.split("/");
    String projectNameWithGit = parts[parts.length - 1];
    return projectNameWithGit.replace(".git", "");
  }

  public void saveIndexedCommit(String repoName, String commit) {
    try {
      Path metaFile = Paths.get(repoBasePath, repoName, ".rag-meta.json");

      Map<String, Object> meta = new HashMap<>();
      meta.put("lastIndexedCommit", commit);
      meta.put("updatedAt", System.currentTimeMillis());

      mapper.writerWithDefaultPrettyPrinter().writeValue(metaFile.toFile(), meta);

      log.info("Commit saved for {}: {}", repoName, commit);

    } catch (Exception e) {
      log.error("Failed to save commit for {}: {}", repoName, e.getMessage());
    }
  }

  public String loadIndexedCommit(String repoName) {
    try {
      Path metaFile = Paths.get(repoBasePath, repoName, ".rag-meta.json");
      if (!Files.exists(metaFile)) return null;

      JsonNode node = mapper.readTree(metaFile.toFile());
      return node.path("lastIndexedCommit").asText(null);

    } catch (Exception e) {
      log.error("Failed to load commit for {}: {}", repoName, e.getMessage());
      return null;
    }
  }

  /**
   * 同步仓库代码，拉取最新更新。如果仓库不存在，则自动注册
   *
   * @param ragRepoDTO 包含仓库信息的 DTO
   * @return SyncResult 包含当前 commit 和是否有变化，如果同步失败返回 null
   */
  public SyncResult syncRepository(RagRepoDTO ragRepoDTO) throws IOException {
    String repoName = ragRepoDTO.getRepoName();
    Path baseDir = Paths.get(System.getProperty("user.home"), "ai-rag-repos");
    Files.createDirectories(baseDir);
    Path repoPath = baseDir.resolve(repoName);
    File localDir = repoPath.toFile();

    // 如果仓库不存在，先自动注册
    if (!localDir.exists()) {
      log.info("Repository does not exist, auto-registering: {}", repoName);
      if (!autoRegisterRepository(ragRepoDTO)) {
        log.error("Failed to auto-register repository: {}", repoName);
        return null;
      }
    }

    Git git = null;
    try {
      git = Git.open(localDir);
      Repository repository = git.getRepository();

      // 获取本地记录的 commit
      String lastIndexedCommit = loadIndexedCommit(repoName);
      log.info("Last indexed commit for {}: {}", repoName, lastIndexedCommit);

      // pull 最新代码
      git.pull()
          .setCredentialsProvider(
              new UsernamePasswordCredentialsProvider(githubUsername, githubToken))
          .call();

      String currentCommit = repository.resolve("HEAD").name();
      log.info("Current HEAD commit for {}: {}", repoName, currentCommit);

      boolean hasChanges = !currentCommit.equals(lastIndexedCommit);
      return new SyncResult(currentCommit, hasChanges);

    } catch (Exception e) {
      log.error("Failed to sync repository {}: {}", repoName, e.getMessage());
      return null;
    } finally {
      if (git != null) {
        try {
          git.close();
        } catch (Exception e) {
          log.error("Failed to close git repository: {}", e.getMessage());
        }
      }
    }
  }

  /**
   * 自动注册仓库
   *
   * @param ragRepoDTO 包含仓库信息的 DTO
   * @return 是否注册成功
   */
  private boolean autoRegisterRepository(RagRepoDTO ragRepoDTO) throws IOException {
    String repoUrl = ragRepoDTO.getRepoUrl();
    String branch = ragRepoDTO.getBranch();
    String repoName = ragRepoDTO.getRepoName();

    if (repoUrl == null || repoUrl.isEmpty()) {
      log.error("Repository URL is required for auto-registration");
      return false;
    }

    Path baseDir = Paths.get(System.getProperty("user.home"), "ai-rag-repos");
    Files.createDirectories(baseDir);

    Path repoPath = baseDir.resolve(repoName);
    Files.createDirectories(repoPath);

    log.info("Auto-registering repository {} from {}", repoName, repoUrl);

    Git git = null;
    try {
      // Clone repository 到固定路径
      git =
          Git.cloneRepository()
              .setURI(repoUrl)
              .setDirectory(repoPath.toFile())
              .setBranch(branch != null ? branch : "main")
              .setCredentialsProvider(
                  new UsernamePasswordCredentialsProvider(githubUsername, githubToken))
              .call();

      // 全量构建向量库
      indexRepositoryFiles(repoPath.toAbsolutePath(), repoName);

      // 记录当前 HEAD commit
      String headCommit = git.getRepository().resolve("HEAD").name();
      saveIndexedCommit(repoName, headCommit);
      log.info("Auto-registered repository successfully: {}", repoName);

      return true;

    } catch (Exception e) {
      log.error("Failed to auto-register repository {}: {}", repoName, e.getMessage());
      return false;
    } finally {
      if (git != null) {
        try {
          git.close();
        } catch (Exception e) {
          log.error("Failed to close git repository: {}", e.getMessage());
        }
      }
    }
  }

  // ==================== Rag Utils Methods ====================

  public void indexRepositoryFiles(Path repoPath, String repoName) throws IOException {

    Set<String> ALLOWED =
        Set.of(
            ".md", ".txt", ".java", ".js", ".ts", ".go", ".py", ".kt", ".xml", ".yaml", ".yml",
            ".json");

    Files.walkFileTree(
        repoPath,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

            String filename = file.getFileName().toString().toLowerCase();
            if (ALLOWED.stream().noneMatch(filename::endsWith)) return FileVisitResult.CONTINUE;

            Path relativePath = repoPath.relativize(file);
            String filePath = relativePath.toString().replace("\\", "/");

            try {
              TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
              List<Document> docs = reader.get();
              List<Document> chunks = tokenTextSplitter.apply(docs);

              chunks.forEach(
                  d -> {
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

  public void indexRepositoryFilesIncremental(Path repoPath, String repoName, Git git)
      throws IOException {
    // 允许索引的文件类型
    Set<String> ALLOWED =
        Set.of(
            ".md", ".txt", ".java", ".js", ".ts", ".go", ".py", ".kt", ".xml", ".yaml", ".yml",
            ".json");

    // 1. 获取上一次索引 commit
    String lastIndexedCommit = loadIndexedCommit(repoName);
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
              if (ALLOWED.stream().noneMatch(path.toLowerCase()::endsWith)) continue;
              indexFile(repoPath, repoName, path);
              break;

            case MODIFY:
              path = diff.getNewPath();
              if (ALLOWED.stream().noneMatch(path.toLowerCase()::endsWith)) continue;

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
    saveIndexedCommit(repoName, headCommit);
    log.info("Updated last indexed commit for {}: {}", repoName, headCommit);
  }

  /** 删除指定 docId 对应的向量 */
  private void deleteDocumentById(String docId) {
    SearchRequest request =
        SearchRequest.builder()
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

  /** 删除指定仓库的最后索引 commit 信息，以及对应向量 */
  public void deleteIndexedCommit(String repoName) throws IOException {
    // 删除仓库所有向量
    Filter.Expression filter = new FilterExpressionBuilder().eq("repo", repoName).build();
    pgVectorStore.delete(filter);
    log.info("Deleted all vectors for repository {}", repoName);
  }

  /** 根据代码片段返回上下文信息（检索向量库匹配片段） */
  public String reviewCodeContext(String repoName, String code) {
    // 1. 将代码分块
    Document doc = new Document(code);
    List<Document> chunks = tokenTextSplitter.apply(List.of(doc));

    List<String> results = new ArrayList<>();

    for (Document chunk : chunks) {
      // 2. 构建过滤条件：knowledge == repoName
      FilterExpressionBuilder builder = new FilterExpressionBuilder();
      Filter.Expression filter = builder.eq("repo", repoName).build();

      // 3. 构建搜索请求
      SearchRequest request =
          SearchRequest.builder()
              .query(Objects.requireNonNull(chunk.getText()))
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

  /** 获取仓库的 Git tag 列表 */
  public List<String> getRepositoryTags(String repoName) throws IOException {
    return new ArrayList<>();
  }

  /** 同步结果类 */
  public static class SyncResult {
    public final String currentCommit;
    public final boolean hasChanges;

    public SyncResult(String currentCommit, boolean hasChanges) {
      this.currentCommit = currentCommit;
      this.hasChanges = hasChanges;
    }
  }
}
