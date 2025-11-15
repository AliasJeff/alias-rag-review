package com.alias.rag.dev.tech.api;

import com.alias.rag.dev.tech.api.response.Response;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface IRAGService {

  Response<List<String>> queryRagTagList();

  Response<String> uploadFile(String ragTag, List<MultipartFile> files);

  Response<String> analyzeGitRepository(String repoUrl, String userName, String token)
      throws Exception;
}
