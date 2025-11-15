package com.alias.domain.service.impl;

import com.alias.domain.model.PrSnapshot;
import com.alias.domain.service.IPrSnapshotService;
import com.alias.infrastructure.mapper.IPrSnapshotRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PR Snapshot Service Implementation
 */
@Slf4j
@Service
public class PrSnapshotService implements IPrSnapshotService {

    @Resource
    private IPrSnapshotRepository prSnapshotRepository;

    @Override
    public PrSnapshot createSnapshot(PrSnapshot snapshot) {
        log.debug("Creating PR snapshot for conversation: {}, filePath: {}", snapshot.getConversationId(), snapshot.getFilePath());

        if (snapshot.getId() == null) {
            snapshot.setId(UUID.randomUUID());
        }

        if (snapshot.getCreatedAt() == null) {
            snapshot.setCreatedAt(LocalDateTime.now());
        }

        return prSnapshotRepository.save(snapshot);
    }

    @Override
    public PrSnapshot getSnapshotById(UUID snapshotId) {
        log.debug("Getting PR snapshot by ID: {}", snapshotId);
        return prSnapshotRepository.findById(snapshotId).orElse(null);
    }

    @Override
    public List<PrSnapshot> getSnapshotsByConversationId(UUID conversationId) {
        log.debug("Getting all PR snapshots for conversation: {}", conversationId);
        return prSnapshotRepository.findByConversationId(conversationId);
    }

    @Override
    public PrSnapshot getSnapshotByConversationAndFilePath(UUID conversationId, String filePath) {
        log.debug("Getting PR snapshot by conversation and file path: conversationId={}, filePath={}", conversationId, filePath);
        return prSnapshotRepository.findByConversationIdAndFilePath(conversationId, filePath).orElse(null);
    }

    @Override
    public PrSnapshot updateSnapshot(PrSnapshot snapshot) {
        log.info("Updating PR snapshot: {}", snapshot.getId());
        return prSnapshotRepository.save(snapshot);
    }

    @Override
    public void deleteSnapshot(UUID snapshotId) {
        log.info("Deleting PR snapshot: {}", snapshotId);
        prSnapshotRepository.deleteById(snapshotId);
    }

    @Override
    public void deleteSnapshotsByConversationId(UUID conversationId) {
        log.info("Deleting all PR snapshots for conversation: {}", conversationId);
        prSnapshotRepository.deleteByConversationId(conversationId);
    }

    @Override
    public List<PrSnapshot> searchSnapshotsByFilePath(UUID conversationId, String filePathPattern) {
        log.debug("Searching PR snapshots by file path pattern: conversationId={}, pattern={}", conversationId, filePathPattern);
        return prSnapshotRepository.findByConversationIdAndFilePathLike(conversationId, filePathPattern);
    }
}
