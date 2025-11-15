package com.alias.domain.controller;

import com.alias.domain.model.ClientUser;
import com.alias.domain.model.Response;
import com.alias.domain.service.IClientUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Client User Controller
 * Handles client user management operations
 */
@Slf4j
@Tag(name = "客户端用户接口", description = "管理浏览器客户端用户的接口")
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/client-users")
public class ClientUserController {

    @Resource
    private IClientUserService clientUserService;

    /**
     * Get or create client user by client identifier
     *
     * @param clientIdentifier the client identifier UUID
     * @return client user
     */
    @Operation(summary = "获取或创建客户端用户", description = "根据客户端标识符获取或创建客户端用户")
    @PostMapping("/get-or-create/{clientIdentifier}")
    public Response<ClientUser> getOrCreateClientUser(@PathVariable UUID clientIdentifier) {
        try {
            log.info("Getting or creating client user: {}", clientIdentifier);
            ClientUser clientUser = clientUserService.getOrCreateClientUser(clientIdentifier);
            return Response.<ClientUser>builder().code("0000").info("Success").data(clientUser).build();
        } catch (Exception e) {
            log.error("Failed to get or create client user: {}", clientIdentifier, e);
            return Response.<ClientUser>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Get client user by ID
     *
     * @param id the user ID
     * @return client user
     */
    @Operation(summary = "根据ID获取客户端用户", description = "根据用户ID获取客户端用户信息")
    @GetMapping("/{id}")
    public Response<ClientUser> getClientUserById(@PathVariable UUID id) {
        try {
            log.info("Getting client user by ID: {}", id);
            ClientUser clientUser = clientUserService.getClientUserById(id);

            if (clientUser == null) {
                return Response.<ClientUser>builder().code("4004").info("Client user not found").build();
            }

            return Response.<ClientUser>builder().code("0000").info("Success").data(clientUser).build();
        } catch (Exception e) {
            log.error("Failed to get client user: {}", id, e);
            return Response.<ClientUser>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Update GitHub token
     *
     * @param clientIdentifier the client identifier
     * @param githubToken      the encrypted GitHub token
     * @return response
     */
    @Operation(summary = "更新GitHub Token", description = "为客户端用户更新加密的GitHub Token")
    @PutMapping("/{clientIdentifier}/github-token")
    public Response<String> updateGithubToken(
                                              @PathVariable UUID clientIdentifier, @RequestParam String githubToken) {
        try {
            log.info("Updating GitHub token for client user: {}", clientIdentifier);
            clientUserService.updateGithubToken(clientIdentifier, githubToken);
            return Response.<String>builder().code("0000").info("GitHub token updated successfully").data(clientIdentifier.toString()).build();
        } catch (Exception e) {
            log.error("Failed to update GitHub token: {}", clientIdentifier, e);
            return Response.<String>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Update OpenAI API key
     *
     * @param clientIdentifier the client identifier
     * @param openaiApiKey     the encrypted OpenAI API key
     * @return response
     */
    @Operation(summary = "更新OpenAI API Key", description = "为客户端用户更新加密的OpenAI API Key")
    @PutMapping("/{clientIdentifier}/openai-api-key")
    public Response<String> updateOpenaiApiKey(
                                               @PathVariable UUID clientIdentifier, @RequestParam String openaiApiKey) {
        try {
            log.info("Updating OpenAI API key for client user: {}", clientIdentifier);
            clientUserService.updateOpenaiApiKey(clientIdentifier, openaiApiKey);
            return Response.<String>builder().code("0000").info("OpenAI API key updated successfully").data(clientIdentifier.toString()).build();
        } catch (Exception e) {
            log.error("Failed to update OpenAI API key: {}", clientIdentifier, e);
            return Response.<String>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Delete client user
     *
     * @param clientIdentifier the client identifier
     * @return response
     */
    @Operation(summary = "删除客户端用户", description = "删除指定的客户端用户及其所有关联数据")
    @DeleteMapping("/{clientIdentifier}")
    public Response<String> deleteClientUser(@PathVariable UUID clientIdentifier) {
        try {
            log.info("Deleting client user: {}", clientIdentifier);
            clientUserService.deleteClientUser(clientIdentifier);
            return Response.<String>builder().code("0000").info("Client user deleted successfully").data(clientIdentifier.toString()).build();
        } catch (Exception e) {
            log.error("Failed to delete client user: {}", clientIdentifier, e);
            return Response.<String>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }
}
