package com.alias.domain.controller;

import com.alias.config.AppConfig;
import com.alias.domain.model.ModelEnum;
import com.alias.domain.model.Response;
import com.alias.domain.model.ReviewRequest;
import com.alias.domain.service.IPrSnapshotService;
import com.alias.domain.service.impl.ReviewPullRequestService;
import com.alias.infrastructure.git.GitCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

/**
 * 代码审查Controller
 */
@Slf4j
@Tag(name = "代码审查接口", description = "AI代码审查接口，支持对GitHub PR进行代码审查")
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/code-review")
public class CodeReviewController {

    @Resource
    private ChatClient chatClient;

    @Resource
    private IPrSnapshotService prSnapshotService;

    /**
     * 执行PR代码审查
     *
     * @param request 审查请求，包含PR URL和可选的模型参数
     * @return 审查结果
     */
    @Operation(summary = "审查PR代码", description = "对指定的GitHub PR进行AI代码审查，并将结果发布到PR评论中")
    @RequestMapping(value = "/review-pr", method = RequestMethod.POST)
    public Response<String> reviewPullRequest(@RequestBody ReviewRequest request) {
        try {
            // 参数校验
            if (request == null || request.getPrUrl() == null || request.getPrUrl().isEmpty()) {
                return Response.<String>builder().code("4000").info("PR URL is required").build();
            }

            String prUrl = request.getPrUrl();
            log.info("Starting PR review. prUrl={}", prUrl);

            // 获取配置
            AppConfig cfg = AppConfig.getInstance();
            String githubToken = cfg.requireString("github", "token");

            // 创建依赖
            GitCommand gitCommand = new GitCommand(githubToken);

            // 创建服务并执行审查
            ReviewPullRequestService reviewService = new ReviewPullRequestService(gitCommand, chatClient, prSnapshotService);

            // 设置模型（如果指定）
            if (request.getModel() != null && !request.getModel().isEmpty()) {
                reviewService.setModel(request.getModel());
                log.info("Using model: {}", request.getModel());
            } else {
                // 使用默认模型
                reviewService.setModel(ModelEnum.GPT_4O);
                log.info("Using default model: {}", ModelEnum.GPT_4O.getCode());
            }

            // 执行审查（同步执行，会等待审查完成）
            reviewService.exec(prUrl);

            log.info("PR review completed successfully. prUrl={}", prUrl);

            return Response.<String>builder().code("0000").info("Code review completed successfully").data(prUrl).build();

        } catch (IllegalStateException e) {
            log.error("Configuration error: {}", e.getMessage());
            return Response.<String>builder().code("5001").info("Configuration error: " + e.getMessage()).build();
        } catch (Exception e) {
            log.error("Failed to review PR. prUrl={}, error={}", request != null ? request.getPrUrl() : "null", e.getMessage(), e);
            return Response.<String>builder().code("5000").info("Review failed: " + e.getMessage()).build();
        }
    }

    /**
     * 获取支持的模型列表
     *
     * @return 模型列表
     */
    @Operation(summary = "获取支持的模型列表", description = "返回所有支持的AI模型列表")
    @RequestMapping(value = "/models", method = RequestMethod.GET)
    public Response<ModelEnum[]> getSupportedModels() {
        try {
            ModelEnum[] models = ModelEnum.values();
            return Response.<ModelEnum[]>builder().code("0000").info("Success").data(models).build();
        } catch (Exception e) {
            log.error("Failed to get models: {}", e.getMessage(), e);
            return Response.<ModelEnum[]>builder().code("5000").info("Failed to get models: " + e.getMessage()).build();
        }
    }

}

