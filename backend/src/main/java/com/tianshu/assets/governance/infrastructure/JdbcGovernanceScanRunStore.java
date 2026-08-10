package com.tianshu.assets.governance.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.scan.application.GovernanceScanRunStore;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRun;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "oceanbase"})
@ConditionalOnProperty(name = "asset.governance-schema-enabled", havingValue = "true")
public class JdbcGovernanceScanRunStore extends JdbcGovernanceSupport implements GovernanceScanRunStore {
    public JdbcGovernanceScanRunStore(JdbcClient jdbc, ObjectMapper json, @Value("${asset.database-writes-enabled:false}") boolean writable) { super(jdbc, json, writable); }

    @Override public List<GovernanceScanRun> findAll() {
        return jdbc.sql("SELECT payload_json FROM governance_scan_run ORDER BY id DESC").query(String.class).list().stream().map(value -> decode(value, GovernanceScanRun.class)).toList();
    }
    @Override public Optional<GovernanceScanRun> findById(long id) {
        return jdbc.sql("SELECT payload_json FROM governance_scan_run WHERE id=:id").param("id", id).query(String.class).optional().map(value -> decode(value, GovernanceScanRun.class));
    }
    @Override public GovernanceScanRun start(GovernanceScanRun run) {
        requireWritable(); var key = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO governance_scan_run(trigger_type,status,version,payload_json) VALUES(:trigger,:status,0,:payload)").param("trigger", run.triggerType().name()).param("status", run.status().name()).param("payload", encode(run)).update(key, "id");
        var created = copy(run, key.getKeyAs(Long.class), 0); jdbc.sql("UPDATE governance_scan_run SET payload_json=:payload WHERE id=:id").param("payload", encode(created)).param("id", created.id()).update(); return created;
    }
    @Override public GovernanceScanRun succeed(long id, long expectedVersion, Counts counts, Instant finishedAt) {
        return finish(id, expectedVersion, GovernanceScanRunStatus.SUCCEEDED, counts, "", finishedAt);
    }
    @Override public GovernanceScanRun fail(long id, long expectedVersion, Counts counts, String errorMessage, Instant finishedAt) {
        return finish(id, expectedVersion, GovernanceScanRunStatus.FAILED, counts, errorMessage, finishedAt);
    }
    private GovernanceScanRun finish(long id, long expectedVersion, GovernanceScanRunStatus status, Counts counts, String error, Instant finishedAt) {
        requireWritable(); var current = findById(id).orElseThrow(() -> new GovernanceConflictException("扫描运行不存在"));
        if (current.version() != expectedVersion) throw new GovernanceVersionConflictException("扫描运行已被其他用户更新，请刷新后重试");
        var updated = new GovernanceScanRun(id, current.triggerType(), status, current.startedAt(), finishedAt, counts.scannedAssetCount(), counts.createdIssueCount(), counts.reopenedIssueCount(), counts.unchangedIssueCount(), error, current.retryOfRunId(), expectedVersion + 1);
        int rows = jdbc.sql("UPDATE governance_scan_run SET status=:status,version=version+1,payload_json=:payload,finished_at=:finishedAt WHERE id=:id AND version=:version").param("status", status.name()).param("payload", encode(updated)).param("finishedAt", finishedAt).param("id", id).param("version", expectedVersion).update();
        if (rows != 1) throw new GovernanceVersionConflictException("扫描运行已被其他用户更新，请刷新后重试"); return updated;
    }
    private GovernanceScanRun copy(GovernanceScanRun source, long id, long version) { return new GovernanceScanRun(id, source.triggerType(), source.status(), source.startedAt(), source.finishedAt(), source.scannedAssetCount(), source.createdIssueCount(), source.reopenedIssueCount(), source.unchangedIssueCount(), source.errorMessage(), source.retryOfRunId(), version); }
}
