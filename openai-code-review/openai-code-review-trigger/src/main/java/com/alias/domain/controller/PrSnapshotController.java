package com.alias.domain.controller;

import com.alias.domain.model.PrSnapshot;
import com.alias.domain.model.Response;
import com.alias.domain.service.IPrSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * PR Snapshot Controller
 * Handles PR snapshot management operations
 */
@Slf4j
@Tag(name = "PR快照管理接口", description = "管理PR文件快照的接口")
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/pr-snapshots")
public class PrSnapshotController {

    @Resource
    private IPrSnapshotService prSnapshotService;

    /**
     * Create a new PR snapshot
     *
     * @param snapshot the snapshot to create
     * @return created snapshot
     */
    @Operation(summary = "创建PR快照", description = "为对话创建新的PR文件快照")
    @PostMapping
    public Response<PrSnapshot> createSnapshot(@RequestBody PrSnapshot snapshot) {
        try {
            if (snapshot.getConversationId() == null) {
                return Response.<PrSnapshot>builder().code("4000").info("Conversation ID is required").build();
            }

            if (snapshot.getFilePath() == null || snapshot.getFilePath().isEmpty()) {
                return Response.<PrSnapshot>builder().code("4000").info("File path is required").build();
            }

            log.info("Creating PR snapshot for conversation: {}, filePath: {}", snapshot.getConversationId(), snapshot.getFilePath());
            PrSnapshot created = prSnapshotService.createSnapshot(snapshot);

            return Response.<PrSnapshot>builder().code("0000").info("PR snapshot created successfully").data(created).build();
        } catch (Exception e) {
            log.error("Failed to create PR snapshot", e);
            return Response.<PrSnapshot>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Get PR snapshot by ID
     *
     * @param snapshotId the snapshot ID
     * @return snapshot
     */
    @Operation(summary = "获取PR快照", description = "根据快照ID获取PR快照详情")
    @GetMapping("/{snapshotId}")
    public Response<PrSnapshot> getSnapshot(@PathVariable("snapshotId") UUID snapshotId) {
        try {
            log.info("Getting PR snapshot: {}", snapshotId);
            PrSnapshot snapshot = prSnapshotService.getSnapshotById(snapshotId);

            if (snapshot == null) {
                return Response.<PrSnapshot>builder().code("4004").info("PR snapshot not found").build();
            }

            return Response.<PrSnapshot>builder().code("0000").info("Success").data(snapshot).build();
        } catch (Exception e) {
            log.error("Failed to get PR snapshot: {}", snapshotId, e);
            return Response.<PrSnapshot>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Get all PR snapshots for a conversation
     *
     * @param conversationId the conversation ID
     * @return list of snapshots
     */
    @Operation(summary = "获取对话的所有PR快照", description = "获取指定对话的所有PR快照列表")
    @GetMapping("/conversation/{conversationId}")
    public Response<List<PrSnapshot>> getConversationSnapshots(@PathVariable("conversationId") UUID conversationId) {
        try {
            log.info("Getting PR snapshots for conversation: {}", conversationId);
            List<PrSnapshot> snapshots = prSnapshotService.getSnapshotsByConversationId(conversationId);

            return Response.<List<PrSnapshot>>builder().code("0000").info("Success").data(snapshots).build();
        } catch (Exception e) {
            log.error("Failed to get conversation PR snapshots: {}", conversationId, e);
            return Response.<List<PrSnapshot>>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Get PR snapshot by conversation and file path
     *
     * @param conversationId the conversation ID
     * @param filePath       the file path
     * @return snapshot
     */
    @Operation(summary = "根据文件路径获取快照", description = "根据对话ID和文件路径获取PR快照")
    @GetMapping("/conversation/{conversationId}/file")
    public Response<PrSnapshot> getSnapshotByFilePath(
                                                      @PathVariable("conversationId") UUID conversationId, @RequestParam("filePath") String filePath) {
        try {
            log.info("Getting PR snapshot by file path: conversationId={}, filePath={}", conversationId, filePath);
            PrSnapshot snapshot = prSnapshotService.getSnapshotByConversationAndFilePath(conversationId, filePath);

            if (snapshot == null) {
                return Response.<PrSnapshot>builder().code("4004").info("PR snapshot not found").build();
            }

            return Response.<PrSnapshot>builder().code("0000").info("Success").data(snapshot).build();
        } catch (Exception e) {
            log.error("Failed to get PR snapshot by file path: {}", conversationId, e);
            return Response.<PrSnapshot>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Update PR snapshot
     *
     * @param snapshotId the snapshot ID
     * @param snapshot   the updated snapshot
     * @return updated snapshot
     */
    @Operation(summary = "更新PR快照", description = "更新PR快照的信息")
    @PutMapping("/{snapshotId}")
    public Response<PrSnapshot> updateSnapshot(
                                               @PathVariable("snapshotId") UUID snapshotId, @RequestBody PrSnapshot snapshot) {
        try {
            snapshot.setId(snapshotId);
            log.info("Updating PR snapshot: {}", snapshotId);
            PrSnapshot updated = prSnapshotService.updateSnapshot(snapshot);

            return Response.<PrSnapshot>builder().code("0000").info("PR snapshot updated successfully").data(updated).build();
        } catch (Exception e) {
            log.error("Failed to update PR snapshot: {}", snapshotId, e);
            return Response.<PrSnapshot>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Delete PR snapshot
     *
     * @param snapshotId the snapshot ID
     * @return response
     */
    @Operation(summary = "删除PR快照", description = "删除指定的PR快照")
    @DeleteMapping("/{snapshotId}")
    public Response<String> deleteSnapshot(@PathVariable("snapshotId") UUID snapshotId) {
        try {
            log.info("Deleting PR snapshot: {}", snapshotId);
            prSnapshotService.deleteSnapshot(snapshotId);

            return Response.<String>builder().code("0000").info("PR snapshot deleted successfully").data(snapshotId.toString()).build();
        } catch (Exception e) {
            log.error("Failed to delete PR snapshot: {}", snapshotId, e);
            return Response.<String>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Delete all PR snapshots for a conversation
     *
     * @param conversationId the conversation ID
     * @return response
     */
    @Operation(summary = "清空对话快照", description = "删除指定对话的所有PR快照")
    @DeleteMapping("/conversation/{conversationId}")
    public Response<String> deleteConversationSnapshots(@PathVariable("conversationId") UUID conversationId) {
        try {
            log.info("Deleting all PR snapshots for conversation: {}", conversationId);
            prSnapshotService.deleteSnapshotsByConversationId(conversationId);

            return Response.<String>builder().code("0000").info("PR snapshots deleted successfully").data(conversationId.toString()).build();
        } catch (Exception e) {
            log.error("Failed to delete PR snapshots: {}", conversationId, e);
            return Response.<String>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Search PR snapshots by file path pattern
     *
     * @param conversationId  the conversation ID
     * @param filePathPattern the file path pattern
     * @return list of matching snapshots
     */
    @Operation(summary = "搜索PR快照", description = "根据文件路径模式搜索PR快照")
    @GetMapping("/conversation/{conversationId}/search")
    public Response<List<PrSnapshot>> searchSnapshots(
                                                      @PathVariable("conversationId") UUID conversationId, @RequestParam("filePathPattern") String filePathPattern) {
        try {
            log.info("Searching PR snapshots: conversationId={}, pattern={}", conversationId, filePathPattern);
            List<PrSnapshot> snapshots = prSnapshotService.searchSnapshotsByFilePath(conversationId, filePathPattern);

            return Response.<List<PrSnapshot>>builder().code("0000").info("Success").data(snapshots).build();
        } catch (Exception e) {
            log.error("Failed to search PR snapshots: {}", conversationId, e);
            return Response.<List<PrSnapshot>>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }
}
