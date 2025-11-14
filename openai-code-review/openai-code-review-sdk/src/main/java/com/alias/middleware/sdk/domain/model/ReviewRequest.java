package com.alias.middleware.sdk.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PR代码审查请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest implements Serializable {

    /**
     * GitHub PR URL，格式：https://github.com/{owner}/{repo}/pull/{number}
     */
    @Schema(description = "GitHub PR URL", example = "https://github.com/owner/repo/pull/123")
    private String prUrl;

    /**
     * 使用的模型，可选。如果不指定，默认使用 GPT_4O
     * 可选值：gpt-4o, glm-4, glm-4-flash 等（参考 ModelEnum）
     */
    @Schema(description = "使用的模型", example = "gpt-4o")
    private String model;

}

