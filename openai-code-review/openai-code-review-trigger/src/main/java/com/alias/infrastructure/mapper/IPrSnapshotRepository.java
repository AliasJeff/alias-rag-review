package com.alias.infrastructure.mapper;

import com.alias.domain.model.PrSnapshot;
import com.alias.infrastructure.typehandler.JsonbTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PR Snapshot Repository Interface
 * Data access layer for PR snapshots
 */
@Mapper
public interface IPrSnapshotRepository {

    /**
     * Save or update a PR snapshot based on (url, client_identifier)
     *
     * @param snapshot the snapshot to save
     * @return number of rows affected
     */
    @Insert("""
                    INSERT INTO pr_snapshots (id, url, client_identifier, repo_name, pr_number, branch, file_changes, created_at, updated_at)
                    VALUES (
                            #{id, javaType=java.util.UUID, jdbcType=OTHER},
                            #{url, jdbcType=VARCHAR},
                            #{clientIdentifier, javaType=java.util.UUID, jdbcType=OTHER},
                            #{repoName, jdbcType=VARCHAR},
                            #{prNumber, jdbcType=INTEGER},
                            #{branch, jdbcType=VARCHAR},
                            #{fileChanges, jdbcType=OTHER, typeHandler=com.alias.infrastructure.typehandler.JsonbTypeHandler},
                            #{createdAt, jdbcType=TIMESTAMP},
                            #{updatedAt, jdbcType=TIMESTAMP}
                    )
                    ON CONFLICT (url, client_identifier) DO UPDATE SET
                        repo_name = EXCLUDED.repo_name,
                        pr_number = EXCLUDED.pr_number,
                        branch = EXCLUDED.branch,
                        file_changes = EXCLUDED.file_changes,
                        updated_at = CURRENT_TIMESTAMP
            """)
    int save(PrSnapshot snapshot);

    /**
     * Find snapshot by ID
     *
     * @param id the snapshot ID
     * @return optional containing the snapshot
     */
    @Select("SELECT id, url, client_identifier, repo_name, pr_number, branch, file_changes, created_at, updated_at " + "FROM pr_snapshots WHERE id = #{id, javaType=java.util.UUID, jdbcType=OTHER}")
    @Results(id = "prSnapshotResultMap", value = {@Result(column = "id", property = "id", javaType = UUID.class, jdbcType = org.apache.ibatis.type.JdbcType.OTHER), @Result(column = "url", property = "url", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "client_identifier", property = "clientIdentifier", javaType = UUID.class, jdbcType = org.apache.ibatis.type.JdbcType.OTHER), @Result(column = "repo_name", property = "repoName", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "pr_number", property = "prNumber", jdbcType = org.apache.ibatis.type.JdbcType.INTEGER), @Result(column = "branch", property = "branch", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "file_changes", property = "fileChanges", jdbcType = org.apache.ibatis.type.JdbcType.OTHER, typeHandler = JsonbTypeHandler.class), @Result(column = "created_at", property = "createdAt", jdbcType = org.apache.ibatis.type.JdbcType.TIMESTAMP), @Result(column = "updated_at", property = "updatedAt", jdbcType = org.apache.ibatis.type.JdbcType.TIMESTAMP)
    })
    Optional<PrSnapshot> findById(UUID id);

    /**
     * Find all snapshots for a client
     *
     * @param clientIdentifier the client identifier
     * @return list of snapshots
     */
    @Select("SELECT id, url, client_identifier, repo_name, pr_number, branch, file_changes, created_at, updated_at " + "FROM pr_snapshots WHERE client_identifier = #{clientIdentifier, javaType=java.util.UUID, jdbcType=OTHER} " + "ORDER BY updated_at DESC")
    @ResultMap("prSnapshotResultMap")
    List<PrSnapshot> findByClientIdentifier(UUID clientIdentifier);

    /**
     * Find snapshot by PR url
     *
     * @param url the PR url
     * @return optional containing the snapshot
     */
    @Select("SELECT id, url, client_identifier, repo_name, pr_number, branch, file_changes, created_at, updated_at " + "FROM pr_snapshots WHERE url = #{url, jdbcType=VARCHAR} " + "LIMIT 1")
    @ResultMap("prSnapshotResultMap")
    Optional<PrSnapshot> findByUrl(String url);

    /**
     * Find snapshots by repository name and PR number
     *
     * @param repoName the repository name
     * @param prNumber the PR number
     * @return list of snapshots
     */
    @Select("SELECT id, url, client_identifier, repo_name, pr_number, branch, file_changes, created_at, updated_at " + "FROM pr_snapshots WHERE repo_name = #{repoName, jdbcType=VARCHAR} " + "AND pr_number = #{prNumber, jdbcType=INTEGER} " + "ORDER BY updated_at DESC")
    @ResultMap("prSnapshotResultMap")
    List<PrSnapshot> findByRepoNameAndPrNumber(String repoName, Integer prNumber);

    /**
     * Delete snapshot by ID
     *
     * @param id the snapshot ID
     */
    @Delete("DELETE FROM pr_snapshots WHERE id = #{id, javaType=java.util.UUID, jdbcType=OTHER}")
    void deleteById(UUID id);

    /**
     * Delete all snapshots for a client
     *
     * @param clientIdentifier the client identifier
     */
    @Delete("DELETE FROM pr_snapshots WHERE client_identifier = #{clientIdentifier, javaType=java.util.UUID, jdbcType=OTHER}")
    void deleteByClientIdentifier(UUID clientIdentifier);

    /**
     * Count snapshots for a client
     *
     * @param clientIdentifier the client identifier
     * @return the count
     */
    @Select("SELECT COUNT(*) FROM pr_snapshots WHERE client_identifier = #{clientIdentifier, javaType=java.util.UUID, jdbcType=OTHER}")
    long countByClientIdentifier(UUID clientIdentifier);

    /**
     * Search snapshots by keyword for a given client
     *
     * @param clientIdentifier the client identifier
     * @param keyword          the search keyword
     * @return list of snapshots
     */
    @Select("SELECT id, url, client_identifier, repo_name, pr_number, branch, file_changes, created_at, updated_at " + "FROM pr_snapshots WHERE client_identifier = #{clientIdentifier, javaType=java.util.UUID, jdbcType=OTHER} " + "AND (repo_name ILIKE #{keyword, jdbcType=VARCHAR} OR branch ILIKE #{keyword, jdbcType=VARCHAR} OR url ILIKE #{keyword, jdbcType=VARCHAR}) " + "ORDER BY updated_at DESC")
    @ResultMap("prSnapshotResultMap")
    List<PrSnapshot> searchByKeyword(UUID clientIdentifier, String keyword);

    /**
     * Save or update a PR snapshot and return the saved object
     *
     * @param snapshot the snapshot to save
     * @return the saved snapshot
     */
    default PrSnapshot saveAndReturn(PrSnapshot snapshot) {
        save(snapshot);
        return findById(snapshot.getId()).orElse(snapshot);
    }
}
