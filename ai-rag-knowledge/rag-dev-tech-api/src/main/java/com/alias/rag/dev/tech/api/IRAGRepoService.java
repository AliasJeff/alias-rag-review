package com.alias.rag.dev.tech.api;

import com.alias.rag.dev.tech.api.response.Response;

import java.util.List;

public interface IRAGRepoService {

    Response<String> registerRepo(String repoUrl, String branch) throws Exception;

    Response<String> syncRepo(String repoName);

    Response<String> deleteRepo(String repoName) throws Exception;

    Response<String> codeReviewContext(String repoName, String code);

    Response<List<String>> queryTagList(String repoName);

}
