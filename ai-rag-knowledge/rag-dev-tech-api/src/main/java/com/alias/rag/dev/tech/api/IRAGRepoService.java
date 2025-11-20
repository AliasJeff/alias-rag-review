package com.alias.rag.dev.tech.api;

import com.alias.rag.dev.tech.api.dto.RagRepoDTO;
import com.alias.rag.dev.tech.api.response.Response;
import java.io.IOException;
import java.util.List;

public interface IRAGRepoService {

  Response<String> registerRepo(RagRepoDTO ragRepoDTO) throws Exception;

  Response<String> syncRepo(RagRepoDTO ragRepoDTO);

  Response<String> deleteRepo(RagRepoDTO ragRepoDTO) throws IOException;

  Response<String> codeReviewContext(RagRepoDTO ragRepoDTO);

  Response<List<String>> queryTagList(RagRepoDTO ragRepoDTO);
}
