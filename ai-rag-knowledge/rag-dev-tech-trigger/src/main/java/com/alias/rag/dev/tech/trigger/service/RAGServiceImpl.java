package com.alias.rag.dev.tech.trigger.service;

import com.alias.rag.dev.tech.api.IRAGService;
import com.alias.rag.dev.tech.api.dto.RagRepoDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RAGServiceImpl implements IRAGService {

  @Value("${github.token}")
  private String githubToken;

  @Value("${github.username}")
  private String githubUsername;

  @Resource private TokenTextSplitter tokenTextSplitter;

  @Resource private PgVectorStore pgVectorStore;

  private static final ObjectMapper mapper = new ObjectMapper();

  @Override
  public String extractProjectName(String repoUrl) {
    String[] parts = repoUrl.split("/");
    String projectNameWithGit = parts[parts.length - 1];
    return projectNameWithGit.replace(".git", "");
  }

  @Override
  public void saveIndexedCommit(String repoName, String commit) {
    try {
      Path metaFile =
          Paths.get(System.getProperty("user.home"), "ai-rag-repos", repoName, ".rag-meta.json");

      Map<String, Object> meta = new HashMap<>();
      meta.put("lastIndexedCommit", commit);
      meta.put("updatedAt", System.currentTimeMillis());

      mapper.writerWithDefaultPrettyPrinter().writeValue(metaFile.toFile(), meta);

      log.info("Commit saved for {}: {}", repoName, commit);

    } catch (Exception e) {
      log.error("Failed to save commit for {}: {}", repoName, e.getMessage());
    }
  }

  @Override
  public String loadIndexedCommit(String repoName) {
    try {
      Path metaFile =
          Paths.get(System.getProperty("user.home"), "ai-rag-repos", repoName, ".rag-meta.json");
      if (!Files.exists(metaFile)) return null;

      JsonNode node = mapper.readTree(metaFile.toFile());
      return node.path("lastIndexedCommit").asText(null);

    } catch (Exception e) {
      log.error("Failed to load commit for {}: {}", repoName, e.getMessage());
      return null;
    }
  }

  @Override
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

  private static final Set<String> CODE_EXTENSIONS =
      Set.of(".java", ".kt", ".js", ".ts", ".py", ".go");

  private static final List<CodePattern> JAVA_KOTLIN_PATTERNS =
      List.of(
          new CodePattern(
              Pattern.compile(
                  "(?m)^\\s*(?:public|protected|private|abstract|final|static|sealed|non-sealed|data|record)?\\s*(?:class|interface|enum|record)\\s+([A-Za-z0-9_]+)"),
              "class ",
              1),
          new CodePattern(
              Pattern.compile(
                  "(?m)^\\s*(?:public|protected|private)?\\s*(?:static\\s+)?[\\w<>\\[\\]]+\\s+([A-Za-z0-9_]+)\\s*\\([^)]*\\)\\s*(?:throws [^{]+)?\\s*\\{"),
              "method ",
              1));

  private static final List<CodePattern> JS_TS_PATTERNS =
      List.of(
          new CodePattern(Pattern.compile("(?m)^\\s*class\\s+([A-Za-z0-9_]+)"), "class ", 1),
          new CodePattern(
              Pattern.compile("(?m)^\\s*function\\s+([A-Za-z0-9_]+)\\s*\\("), "function ", 1),
          new CodePattern(
              Pattern.compile(
                  "(?m)^\\s*(?:const|let|var)\\s+([A-Za-z0-9_]+)\\s*=\\s*\\([^)]*\\)\\s*=>"),
              "function ",
              1),
          new CodePattern(
              Pattern.compile("(?m)^\\s*([A-Za-z0-9_]+)\\s*\\([^)]*\\)\\s*\\{"), "method ", 1));

  private static final List<CodePattern> PYTHON_PATTERNS =
      List.of(
          new CodePattern(
              Pattern.compile("(?m)^\\s*class\\s+([A-Za-z0-9_]+)\\s*\\(?.*:"), "class ", 1),
          new CodePattern(Pattern.compile("(?m)^\\s*def\\s+([A-Za-z0-9_]+)\\s*\\("), "def ", 1));

  private static final List<CodePattern> GO_PATTERNS =
      List.of(
          new CodePattern(
              Pattern.compile("(?m)^\\s*type\\s+([A-Za-z0-9_]+)\\s+struct"), "type ", 1),
          new CodePattern(
              Pattern.compile("(?m)^\\s*func\\s+\\(?([A-Za-z0-9_]+)\\)?\\s*\\("), "func ", 1));

  @Override
  public void indexRepositoryFiles(Path repoPath, String repoName) throws IOException {

    Set<String> ALLOWED =
        Set.of(
            ".md",
            ".java",
            ".js",
            ".ts",
            ".go",
            ".py",
            ".kt",
            ".xml",
            ".yaml",
            ".yml",
            ".json",
            ".properties",
            ".gradle",
            ".sql");

    // 需要忽略的目录
    Set<String> IGNORE_DIRS =
        Set.of(
            ".git",
            ".text",
            ".txt",
            ".idea",
            ".vscode",
            "node_modules",
            "dist",
            "build",
            "out",
            "target",
            ".gradle",
            ".mvn",
            "__pycache__",
            "venv",
            ".venv",
            "env",
            ".mypy_cache",
            "pytest_cache",
            "staticfiles",
            "media",
            "bin",
            "obj",
            "coverage",
            ".log",
            ".cache",
            ".eslintcache",
            ".next",
            ".nuxt",
            ".svelte-kit",
            "parcel-cache",
            ".storybook",
            ".cypress",
            "playwright-report",
            ".vitepress",
            ".vuepress");

    // 需要忽略的文件（完全匹配）
    Set<String> IGNORE_FILES =
        Set.of(
            "package-lock.json",
            "yarn.lock",
            "pnpm-lock.yaml",
            "gradle.lockfile",
            "pom.xml.versionsBackup",
            "Pipfile.lock",
            "poetry.lock",
            "requirements.txt.lock",
            ".DS_Store",
            "Thumbs.db",
            "env.json",
            "local.settings.json",
            "application-local.yml",
            "application-local.properties",
            "webpack-stats.json",
            "tsconfig.tsbuildinfo",
            "npm-debug.log",
            "yarn-error.log",
            "pnpm-debug.log");

    long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB，可改

    Files.walkFileTree(
        repoPath,
        new SimpleFileVisitor<>() {

          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            String name = dir.getFileName().toString();
            if (IGNORE_DIRS.contains(name)) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

            String filename = file.getFileName().toString();

            // 忽略指定文件
            if (IGNORE_FILES.contains(filename)) {
              return FileVisitResult.CONTINUE;
            }

            // 忽略文件大小过大的（防止 OOM）
            try {
              if (Files.size(file) > MAX_FILE_SIZE) {
                log.warn(
                    "[{}] Skip large file {} (size={}MB)",
                    repoName,
                    filename,
                    Files.size(file) / 1024 / 1024);
                return FileVisitResult.CONTINUE;
              }
            } catch (IOException ignore) {
            }

            // 只接受白名单后缀
            String lower = filename.toLowerCase();
            if (ALLOWED.stream().noneMatch(lower::endsWith)) {
              return FileVisitResult.CONTINUE;
            }

            Path relativePath = repoPath.relativize(file);
            String filePath = relativePath.toString().replace("\\", "/");
            indexSingleFile(repoPath, repoName, filePath);

            return FileVisitResult.CONTINUE;
          }
        });
  }

  @Override
  public void indexRepositoryFilesIncremental(Path repoPath, String repoName, Object gitObj)
      throws IOException {
    Git git = (Git) gitObj;
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
              indexSingleFile(repoPath, repoName, path);
              break;

            case MODIFY:
              path = diff.getNewPath();
              if (ALLOWED.stream().noneMatch(path.toLowerCase()::endsWith)) continue;

              // 先查出对应文档并删除
              String docId = repoName + ":" + path.replace("\\", "/");
              deleteDocumentById(docId);

              // 再插入新向量
              indexSingleFile(repoPath, repoName, path);
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
  private void indexSingleFile(Path repoPath, String repoName, String path) {
    Path filePath = repoPath.resolve(path);
    log.info("[{}] Indexing file: {}", repoName, path);
    try {
      String normalizedPath = path.replace("\\", "/");
      List<Document> docs = buildDocumentsForFile(filePath, normalizedPath.toLowerCase());
      List<Document> chunks = chunkDocuments(docs);

      for (Document d : chunks) {
        String docId = repoName + ":" + normalizedPath;
        d.getMetadata().put("id", docId);
        d.getMetadata().put("repo", repoName);
      }

      pgVectorStore.accept(chunks);
    } catch (Exception e) {
      log.error("Failed to index file {}: {}", path, e.getMessage());
    }
  }

  private List<Document> buildDocumentsForFile(Path filePath, String lowerPath) throws IOException {
    if (!isCodeFile(lowerPath)) {
      TikaDocumentReader reader = new TikaDocumentReader(new PathResource(filePath));
      return reader.get();
    }
    String content = Files.readString(filePath);
    return splitCodeBlocks(content, lowerPath);
  }

  private List<Document> chunkDocuments(List<Document> docs) {
    List<Document> chunks = new ArrayList<>();
    for (Document doc : docs) {
      List<Document> chunked = tokenTextSplitter.apply(List.of(doc));
      Object segment = doc.getMetadata().get("segment");
      if (segment != null) {
        chunked.forEach(c -> c.getMetadata().put("segment", segment));
      }
      chunks.addAll(chunked);
    }
    return chunks;
  }

  private boolean isCodeFile(String lowerPath) {
    return CODE_EXTENSIONS.stream().anyMatch(lowerPath::endsWith);
  }

  private List<Document> splitCodeBlocks(String content, String lowerPath) {
    List<CodePattern> patterns = patternsForExtension(lowerPath);
    if (patterns.isEmpty()) {
      return List.of(new Document(content));
    }

    Map<Integer, CodeBlockMarker> markers = new HashMap<>();
    for (CodePattern codePattern : patterns) {
      Matcher matcher = codePattern.pattern().matcher(content);
      while (matcher.find()) {
        int start = matcher.start();
        markers.putIfAbsent(
            start,
            new CodeBlockMarker(
                start,
                codePattern.labelPrefix()
                    + Optional.ofNullable(matcher.group(codePattern.nameGroup())).orElse("")));
      }
    }

    if (markers.isEmpty()) {
      Document fallback = new Document(content);
      fallback.getMetadata().put("segment", "file");
      return List.of(fallback);
    }

    List<CodeBlockMarker> sortedMarkers =
        markers.values().stream()
            .sorted(Comparator.comparingInt(CodeBlockMarker::start))
            .collect(Collectors.toList());

    List<Document> documents = new ArrayList<>();
    for (int i = 0; i < sortedMarkers.size(); i++) {
      int start = sortedMarkers.get(i).start();
      int end =
          (i + 1 < sortedMarkers.size()) ? sortedMarkers.get(i + 1).start() : content.length();
      if (end <= start) continue;
      String block = content.substring(start, end).trim();
      if (block.isEmpty()) continue;
      Document document = new Document(block);
      document.getMetadata().put("segment", sortedMarkers.get(i).label());
      documents.add(document);
    }

    if (documents.isEmpty()) {
      Document fallback = new Document(content);
      fallback.getMetadata().put("segment", "file");
      documents.add(fallback);
    }

    return documents;
  }

  private List<CodePattern> patternsForExtension(String lowerPath) {
    if (lowerPath.endsWith(".java") || lowerPath.endsWith(".kt")) {
      return JAVA_KOTLIN_PATTERNS;
    }
    if (lowerPath.endsWith(".js") || lowerPath.endsWith(".ts")) {
      return JS_TS_PATTERNS;
    }
    if (lowerPath.endsWith(".py")) {
      return PYTHON_PATTERNS;
    }
    if (lowerPath.endsWith(".go")) {
      return GO_PATTERNS;
    }
    return List.of();
  }

  private record CodePattern(Pattern pattern, String labelPrefix, int nameGroup) {}

  private record CodeBlockMarker(int start, String label) {}

  @Override
  public void deleteIndexedCommit(String repoName) throws IOException {
    // 删除仓库所有向量
    Filter.Expression filter = new FilterExpressionBuilder().eq("repo", repoName).build();
    pgVectorStore.delete(filter);
    log.info("Deleted all vectors for repository {}", repoName);
  }

  @Override
  public String reviewCodeContext(String repoName, String code) {
    // 1. 将代码分块
    Document doc = new Document(code);
    List<Document> chunks = tokenTextSplitter.apply(List.of(doc));

    List<String> results = new ArrayList<>();

    for (int i = 0; i < chunks.size(); i++) {
      Document chunk = chunks.get(i);
      String chunkText = Objects.requireNonNull(chunk.getText());
      int queryTokens = countTokens(chunkText);
      int queryChars = chunkText.length();

      // 2. 构建过滤条件：knowledge == repoName
      FilterExpressionBuilder builder = new FilterExpressionBuilder();
      Filter.Expression filter = builder.eq("repo", repoName).build();

      // 3. 构建搜索请求
      SearchRequest request =
          SearchRequest.builder().query(chunkText).topK(5).filterExpression(filter).build();

      // 4. 执行相似度搜索
      List<Document> matched = pgVectorStore.similaritySearch(request);
      int resultTokens = 0;
      int resultChars = 0;
      for (Document m : matched) {
        String formatted = m.getFormattedContent();
        if (formatted == null) {
          continue;
        }
        resultTokens += countTokens(formatted);
        resultChars += formatted.length();
        results.add(formatted);
      }

      log.info(
          "[reviewCodeContext] repo={} chunk={} queryTokens={} queryChars={} resultTokens={} resultChars={} matches={}",
          repoName,
          i,
          queryTokens,
          queryChars,
          resultTokens,
          resultChars,
          matched.size());
    }

    // 5. 拼接返回上下文
    return String.join("\n---\n", results);
  }

  @Override
  public List<String> getRepositoryTags(String repoName) throws IOException {
    return new ArrayList<>();
  }

  private int countTokens(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    return text.trim().split("\\s+").length;
  }
}
