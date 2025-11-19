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
        log.debug("Creating PR snapshot for url: {}, client: {}", snapshot.getUrl(), snapshot.getClientIdentifier());

        if (snapshot.getId() == null) {
            snapshot.setId(UUID.randomUUID());
        }

        if (snapshot.getCreatedAt() == null) {
            snapshot.setCreatedAt(LocalDateTime.now());
        }

        if (snapshot.getUpdatedAt() == null) {
            snapshot.setUpdatedAt(LocalDateTime.now());
        }

        return prSnapshotRepository.saveAndReturn(snapshot);
    }

    @Override
    public PrSnapshot getSnapshotById(UUID snapshotId) {
        log.debug("Getting PR snapshot by ID: {}", snapshotId);
        return prSnapshotRepository.findById(snapshotId).orElse(null);
    }

    @Override
    public List<PrSnapshot> getSnapshotsByClientIdentifier(UUID clientIdentifier) {
        log.debug("Getting all PR snapshots for client: {}", clientIdentifier);
        return prSnapshotRepository.findByClientIdentifier(clientIdentifier);
    }

    @Override
    public PrSnapshot getSnapshotByUrl(String url) {
        log.debug("Getting PR snapshot by url: {}", url);
        return prSnapshotRepository.findByUrl(url).orElse(null);
    }

    @Override
    public List<PrSnapshot> getSnapshotsByRepoNameAndPrNumber(String repoName, Integer prNumber) {
        log.debug("Getting PR snapshots by repo: {}, prNumber: {}", repoName, prNumber);
        return prSnapshotRepository.findByRepoNameAndPrNumber(repoName, prNumber);
    }

    @Override
    public PrSnapshot updateSnapshot(PrSnapshot snapshot) {
        log.info("Updating PR snapshot: {}", snapshot.getId());
        snapshot.setUpdatedAt(LocalDateTime.now());
        return prSnapshotRepository.saveAndReturn(snapshot);
    }

    @Override
    public void deleteSnapshot(UUID snapshotId) {
        log.info("Deleting PR snapshot: {}", snapshotId);
        prSnapshotRepository.deleteById(snapshotId);
    }

    @Override
    public void deleteSnapshotsByClientIdentifier(UUID clientIdentifier) {
        log.info("Deleting all PR snapshots for client: {}", clientIdentifier);
        prSnapshotRepository.deleteByClientIdentifier(clientIdentifier);
    }

    @Override
    public List<PrSnapshot> searchSnapshots(UUID clientIdentifier, String keyword) {
        log.debug("Searching PR snapshots by client: {}, keyword={}", clientIdentifier, keyword);
        String pattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return prSnapshotRepository.searchByKeyword(clientIdentifier, pattern);
    }
}
