package com.alias.rag.dev.tech.api;

import com.alias.rag.dev.tech.api.dto.RagRepoDTO;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface IRAGService {

  /**
   * 提取仓库名称
   *
   * @param repoUrl 仓库 URL
   * @return 仓库名称
   */
  String extractProjectName(String repoUrl);

  /**
   * 保存已索引的 commit
   *
   * @param repoName 仓库名称
   * @param commit commit hash
   */
  void saveIndexedCommit(String repoName, String commit);

  /**
   * 加载已索引的 commit
   *
   * @param repoName 仓库名称
   * @return commit hash，如果不存在返回 null
   */
  String loadIndexedCommit(String repoName);

  /** 同步结果类 */
  class SyncResult {
    public final String currentCommit;
    public final boolean hasChanges;

    public SyncResult(String currentCommit, boolean hasChanges) {
      this.currentCommit = currentCommit;
      this.hasChanges = hasChanges;
    }
  }

  /**
   * 同步仓库代码，拉取最新更新。如果仓库不存在，则自动注册
   *
   * @param ragRepoDTO 包含仓库信息的 DTO
   * @return SyncResult 包含当前 commit 和是否有变化，如果同步失败返回 null
   */
  SyncResult syncRepository(RagRepoDTO ragRepoDTO) throws IOException;

  /**
   * 全量索引仓库文件
   *
   * @param repoPath 仓库路径
   * @param repoName 仓库名称
   * @throws IOException IO 异常
   */
  void indexRepositoryFiles(Path repoPath, String repoName) throws IOException;

  /**
   * 增量索引仓库文件
   *
   * @param repoPath 仓库路径
   * @param repoName 仓库名称
   * @param git Git 对象（org.eclipse.jgit.api.Git 类型）
   * @throws IOException IO 异常
   */
  void indexRepositoryFilesIncremental(Path repoPath, String repoName, Object git)
      throws IOException;

  /**
   * 删除指定仓库的最后索引 commit 信息，以及对应向量
   *
   * @param repoName 仓库名称
   * @throws IOException IO 异常
   */
  void deleteIndexedCommit(String repoName) throws IOException;

  /**
   * 根据代码片段返回上下文信息（检索向量库匹配片段）
   *
   * @param repoName 仓库名称
   * @param code 代码片段
   * @return 上下文信息
   */
  String reviewCodeContext(String repoName, String code);

  /**
   * 获取仓库的 Git tag 列表
   *
   * @param repoName 仓库名称
   * @return tag 列表
   * @throws IOException IO 异常
   */
  List<String> getRepositoryTags(String repoName) throws IOException;
}
