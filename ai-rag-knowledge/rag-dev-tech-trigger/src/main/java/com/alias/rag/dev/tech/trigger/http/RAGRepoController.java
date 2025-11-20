package com.alias.rag.dev.tech.trigger.http;

import com.alias.rag.dev.tech.api.IRAGRepoService;
import com.alias.rag.dev.tech.api.dto.RagRepoDTO;
import com.alias.rag.dev.tech.api.response.Response;
import com.alias.rag.dev.tech.trigger.utils.RepositoryUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "RAG Git 仓库管理接口", description = "RAG Git 仓库的管理接口，包括 Git 仓库查询、更新、分析等功能")
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/repo/")
public class RAGRepoController implements IRAGRepoService {

  @Value("${github.token}")
  private String githubToken;

  @Value("${github.username}")
  private String githubUsername;

  @Value("${repo.base-path}")
  private String repoBasePath;

  @Resource private RepositoryUtils repositoryUtils;

  @RequestMapping(value = "register-repo", method = RequestMethod.POST)
  @Override
  public Response<String> registerRepo(@RequestBody RagRepoDTO ragRepoDTO) throws Exception {

    String repoUrl = ragRepoDTO.getRepoUrl();
    String branch = ragRepoDTO.getBranch();

    // 1. 提取仓库名，例如 chatgpt-framework
    String repoName = RepositoryUtils.extractProjectName(repoUrl);

    // 2. 确定仓库长期保存路径
    Path baseDir = Paths.get(System.getProperty("user.home"), "ai-rag-repos");
    Files.createDirectories(baseDir);
    Path repoPath = baseDir.resolve(repoName);
    File localDir = repoPath.toFile();

    // 若目录已存在，说明已注册
    if (localDir.exists()) {
      return Response.<String>builder()
          .code("0001")
          .info("Repository already exists: " + repoName)
          .build();
    }

    log.info("Cloning repository {} into {}", repoUrl, localDir.getAbsolutePath());

    Git git = null;

    try {
      // 3. Clone repository 到固定路径
      git =
          Git.cloneRepository()
              .setURI(repoUrl)
              .setDirectory(localDir)
              .setBranch(branch)
              .setCredentialsProvider(
                  new UsernamePasswordCredentialsProvider(githubUsername, githubToken))
              .call();

      // 4. 全量构建向量库
      repositoryUtils.indexRepositoryFiles(localDir.toPath(), repoName);

      // 记录当前 HEAD commit
      String headCommit = git.getRepository().resolve("HEAD").name();
      repositoryUtils.saveIndexedCommit(repoName, headCommit);
      log.info("Saved HEAD commit for {}: {}", repoName, headCommit);

      log.info("Repository registered successfully: {}", repoName);

      return Response.<String>builder()
          .code("0000")
          .info("Register success")
          .data(repoName)
          .build();

    } finally {
      if (git != null) git.close();
    }
  }

  @RequestMapping(value = "sync-repo", method = RequestMethod.POST)
  @Override
  public Response<String> syncRepo(@RequestBody RagRepoDTO ragRepoDTO) {
    String repoName = ragRepoDTO.getRepoName();
    if (repoName == null || repoName.isEmpty()) {
      return Response.<String>builder().code("4000").info("Repository name is required").build();
    }

    try {
      // 1. 调用工具方法同步仓库（如果不存在会自动注册）
      RepositoryUtils.SyncResult syncResult = repositoryUtils.syncRepository(ragRepoDTO);
      if (syncResult == null) {
        return Response.<String>builder().code("5000").info("Failed to sync repository").build();
      }

      String currentCommit = syncResult.currentCommit;

      // 2. 检查是否有变化
      if (!syncResult.hasChanges) {
        return Response.<String>builder()
            .code("0002")
            .info("Repository is already up-to-date")
            .data(currentCommit)
            .build();
      }

      log.info("Changes detected, indexing updated files for {}", repoName);

      // 3. 找出新增或修改文件（简单策略：索引所有文件，也可改为 diff）
      Path repoPath = Paths.get(repoBasePath, repoName);
      File localDir = repoPath.toFile();
      Git git = Git.open(localDir);
      try {
        repositoryUtils.indexRepositoryFilesIncremental(repoPath, repoName, git);
      } finally {
        git.close();
      }

      // 4. 更新 commit
      repositoryUtils.saveIndexedCommit(repoName, currentCommit);

      return Response.<String>builder()
          .code("0000")
          .info("Repository synced successfully")
          .data(currentCommit)
          .build();

    } catch (Exception e) {
      log.error("Failed to sync repository {}: {}", repoName, e.getMessage());
      return Response.<String>builder().code("5000").info("Sync failed: " + e.getMessage()).build();
    }
  }

  @RequestMapping(value = "delete-repo", method = RequestMethod.POST)
  @Override
  public Response<String> deleteRepo(@RequestBody RagRepoDTO ragRepoDTO) throws IOException {
    String repoName = ragRepoDTO.getRepoName();
    if (repoName == null || repoName.isEmpty()) {
      return Response.<String>builder().code("4000").info("Repository name is required").build();
    }

    // 2. 确定仓库长期保存路径
    Path baseDir = Paths.get(System.getProperty("user.home"), "ai-rag-repos");
    Files.createDirectories(baseDir);
    Path repoPath = baseDir.resolve(repoName);
    File dir = repoPath.toFile();

    if (!dir.exists()) {
      return Response.<String>builder()
          .code("4001")
          .info("Repository does not exist: " + repoName)
          .build();
    }

    try {
      FileUtils.deleteDirectory(dir);
      log.info("Deleted repository: {}", repoName);

      // 同时删除索引记录
      repositoryUtils.deleteIndexedCommit(repoName);
      return Response.<String>builder()
          .code("0000")
          .info("Repository deleted successfully")
          .build();
    } catch (IOException e) {
      log.error("Failed to delete repository {}: {}", repoName, e.getMessage());
      return Response.<String>builder()
          .code("5000")
          .info("Delete failed: " + e.getMessage())
          .build();
    }
  }

  @RequestMapping(value = "review-context", method = RequestMethod.POST)
  @Override
  public Response<String> codeReviewContext(@RequestBody RagRepoDTO ragRepoDTO) {
    String repoName = ragRepoDTO.getRepoName();
    String code = ragRepoDTO.getCode();

    if (repoName == null || repoName.isEmpty()) {
      return Response.<String>builder().code("4000").info("Repository name is required").build();
    }

    if (code == null || code.isEmpty()) {
      return Response.<String>builder().code("4000").info("Code is required").build();
    }

    try {
      // 1. 先同步仓库代码（如果不存在会自动注册）
      log.info("Syncing repository before code review: {}", repoName);
      RepositoryUtils.SyncResult syncResult = repositoryUtils.syncRepository(ragRepoDTO);
      if (syncResult == null) {
        log.warn("Failed to sync repository {}, proceeding with code review anyway", repoName);
      } else {
        log.info("Repository synced successfully, current commit: {}", syncResult.currentCommit);
      }

      // 2. 执行代码分析
      String reviewResult = repositoryUtils.reviewCodeContext(repoName, code);

      return Response.<String>builder()
          .code("0000")
          .info("Code review completed")
          .data(reviewResult)
          .build();
    } catch (Exception e) {
      log.error("Failed to review code in {}: {}", repoName, e.getMessage());
      return Response.<String>builder()
          .code("5000")
          .info("Code review failed: " + e.getMessage())
          .build();
    }
  }

  @RequestMapping(value = "tag-list", method = RequestMethod.POST)
  @Override
  public Response<List<String>> queryTagList(@RequestBody RagRepoDTO ragRepoDTO) {
    String repoName = ragRepoDTO.getRepoName();
    if (repoName == null || repoName.isEmpty()) {
      return Response.<List<String>>builder()
          .code("4000")
          .info("Repository name is required")
          .build();
    }

    Path repoPath = Paths.get(repoBasePath, repoName);
    File localDir = repoPath.toFile();

    if (!localDir.exists()) {
      return Response.<List<String>>builder()
          .code("4001")
          .info("Repository does not exist: " + repoName)
          .build();
    }

    try {
      // 简单示例：从 repositoryUtils 获取 tag 列表
      List<String> tags = repositoryUtils.getRepositoryTags(repoName);

      return Response.<List<String>>builder()
          .code("0000")
          .info("Tag list retrieved")
          .data(tags)
          .build();
    } catch (Exception e) {
      log.error("Failed to query tags for {}: {}", repoName, e.getMessage());
      return Response.<List<String>>builder()
          .code("5000")
          .info("Query tag list failed: " + e.getMessage())
          .build();
    }
  }
}
