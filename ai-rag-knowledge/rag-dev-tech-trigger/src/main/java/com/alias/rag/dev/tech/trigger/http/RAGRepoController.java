package com.alias.rag.dev.tech.trigger.http;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;

import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alias.rag.dev.tech.api.IRAGRepoService;

@Tag(name = "RAG Git 仓库管理接口", description = "RAG Git 仓库的管理接口，包括 Git 仓库查询、更新、分析等功能")
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/repo/")
public class RAGRepoController implements IRAGRepoService {
    
    @Resource
    private PgVectorStore pgVectorStore;

}
