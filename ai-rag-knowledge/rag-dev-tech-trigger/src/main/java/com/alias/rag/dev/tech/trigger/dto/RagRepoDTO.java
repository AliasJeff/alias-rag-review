package com.alias.rag.dev.tech.trigger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class RagRepoDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Git 仓库 URL", example = "https://github.com/user/repo.git")
    private String repoUrl;

    @Schema(description = "分支名称", example = "main")
    private String branch;

    @Schema(description = "仓库名称", example = "repoName")
    private String repoName;

    @Schema(description = "搜索代码", example = "String name = \"name\";")
    private String searchCode;

}
