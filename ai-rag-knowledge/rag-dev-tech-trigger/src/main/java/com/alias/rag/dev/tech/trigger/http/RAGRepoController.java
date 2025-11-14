package com.alias.rag.dev.tech.trigger.http;

import com.alias.rag.dev.tech.api.response.Response;
import com.alias.rag.dev.tech.trigger.utils.GitUtils;
import com.alias.rag.dev.tech.trigger.utils.RagUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alias.rag.dev.tech.api.IRAGRepoService;

import java.io.File;
import java.nio.file.*;
import java.util.List;


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

    @Resource
    private RagUtils ragUtils;

    @Resource
    private GitUtils gitUtils;

    @Override
    public Response<String> registerRepo(String repoUrl, String branch) throws Exception {

        // 1. 提取仓库名，例如 chatgpt-framework
        String repoName = GitUtils.extractProjectName(repoUrl);

        // 2. 确定仓库长期保存路径
        Path repoPath = Paths.get(repoBasePath, repoName);
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
            git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(localDir)
                    .setBranch(branch)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubUsername, githubToken))
                    .call();

            // 4. 全量构建向量库
            ragUtils.indexRepositoryFiles(localDir.toPath(), repoName);

            // 记录当前 HEAD commit
            String headCommit = git.getRepository().resolve("HEAD").name();
            gitUtils.saveIndexedCommit(repoName, headCommit);
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

    @Override
    public Response<String> syncRepo(String repoName) {
        Path repoPath = Paths.get(repoBasePath, repoName);
        File localDir = repoPath.toFile();

        if (!localDir.exists()) {
            return Response.<String>builder()
                    .code("4001")
                    .info("Repository does not exist: " + repoName)
                    .build();
        }

        Git git = null;
        try {
            git = Git.open(localDir);
            Repository repository = git.getRepository();

            // 1. 获取本地记录的 commit
            String lastIndexedCommit = gitUtils.loadIndexedCommit(repoName);
            log.info("Last indexed commit for {}: {}", repoName, lastIndexedCommit);

            // 2. pull 最新代码
            git.pull()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubUsername, githubToken))
                    .call();

            String currentCommit = repository.resolve("HEAD").name();
            log.info("Current HEAD commit for {}: {}", repoName, currentCommit);

            if (currentCommit.equals(lastIndexedCommit)) {
                return Response.<String>builder()
                        .code("0002")
                        .info("Repository is already up-to-date")
                        .data(currentCommit)
                        .build();
            }

            log.info("Changes detected, indexing updated files for {}", repoName);

            // 3. 找出新增或修改文件（简单策略：索引所有文件，也可改为 diff）
            ragUtils.indexRepositoryFiles(repoPath, repoName);

            // 4. 更新 commit
            gitUtils.saveIndexedCommit(repoName, currentCommit);

            return Response.<String>builder()
                    .code("0000")
                    .info("Repository synced successfully")
                    .data(currentCommit)
                    .build();

        } catch (Exception e) {
            log.error("Failed to sync repository {}: {}", repoName, e.getMessage());
            return Response.<String>builder()
                    .code("5000")
                    .info("Sync failed: " + e.getMessage())
                    .build();
        } finally {
            if (git != null) git.close();
        }
    }

    @Override
    public Response<String> deleteRepo(String repoName) throws Exception {
        String localPath = "./git-cloned-repo/" + repoName;
        FileUtils.deleteDirectory(new File(localPath));
        return null;
    }

    @Override
    public Response<String> codeReviewContext(String repoName, String code) {
        return null;
    }

    @Override
    public Response<List<String>> queryTagList(String repoName) {
        return null;
    }

}
