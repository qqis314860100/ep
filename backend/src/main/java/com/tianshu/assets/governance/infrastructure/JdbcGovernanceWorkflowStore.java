package com.tianshu.assets.governance.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.task.application.GovernanceWorkflowStore;
import com.tianshu.assets.governance.task.domain.GovernanceScopeItem;
import com.tianshu.assets.governance.task.domain.GovernanceScopeSnapshot;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile({"local", "oceanbase"})
@ConditionalOnProperty(name = "asset.governance-schema-enabled", havingValue = "true")
public class JdbcGovernanceWorkflowStore extends JdbcGovernanceSupport implements GovernanceWorkflowStore {
    public JdbcGovernanceWorkflowStore(JdbcClient jdbc, ObjectMapper json,
            @Value("${asset.database-writes-enabled:false}") boolean writable) {
        super(jdbc, json, writable);
    }

    @Override
    @Transactional
    public FrozenWorkflow freeze(FreezeCommand command) {
        requireWritable();
        if (!items(command.taskId()).isEmpty()) throw new GovernanceConflictException("治理任务范围已经固化");
        var ruleKey = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO governance_rule_snapshot (task_id, payload_json) VALUES (:taskId, :payload)")
                .param("taskId", command.taskId()).param("payload", encode(command.ruleSnapshot()))
                .update(ruleKey, "id");
        long ruleId = ruleKey.getKeyAs(Long.class);
        var rule = command.ruleSnapshot().withId(ruleId);
        var snapshotKey = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO governance_scope_snapshot (task_id, created_by, frozen_at, item_count, payload_json) "
                        + "VALUES (:taskId, :createdBy, :frozenAt, :itemCount, '{}')")
                .param("taskId", command.taskId()).param("createdBy", command.createdBy())
                .param("frozenAt", command.frozenAt()).param("itemCount", command.scopeItems().size())
                .update(snapshotKey, "id");
        long snapshotId = snapshotKey.getKeyAs(Long.class);
        var scopeItems = command.scopeItems().stream().map(item -> new GovernanceScopeItem(
                snapshotId, item.taskId(), item.planId(), item.issueId(), item.assetId(), item.targetField(),
                item.targetPath(), item.originalFactJson(), item.assetVersion(), item.ruleVersion(),
                item.scopeFingerprint(), item.responsibleUserId())).toList();
        var snapshot = new GovernanceScopeSnapshot(snapshotId, command.taskId(), command.claimedIssueIds(),
                command.assetIds(), rule, command.createdBy(), command.frozenAt(), scopeItems.size());
        jdbc.sql("UPDATE governance_scope_snapshot SET payload_json = :payload WHERE id = :id")
                .param("payload", encode(snapshot)).param("id", snapshotId).update();
        for (var item : scopeItems) {
            jdbc.sql("INSERT INTO governance_scope_item (snapshot_id, task_id, issue_id, asset_id, target_field, payload_json) "
                            + "VALUES (:snapshotId,:taskId,:issueId,:assetId,:field,:payload)")
                    .param("snapshotId", snapshotId).param("taskId", item.taskId()).param("issueId", item.issueId())
                    .param("assetId", item.assetId()).param("field", item.targetField().name())
                    .param("payload", encode(item)).update();
        }
        for (var requested : command.items()) {
            var key = new GeneratedKeyHolder();
            jdbc.sql("INSERT INTO governance_item (task_id, issue_id, asset_id, status, governance_round, version, payload_json) "
                            + "VALUES (:taskId,:issueId,:assetId,:status,:round,0,'{}')")
                    .param("taskId", requested.taskId()).param("issueId", requested.issueId())
                    .param("assetId", requested.assetId()).param("status", requested.status().name())
                    .param("round", requested.governanceRound()).update(key, "id");
            long id = key.getKeyAs(Long.class);
            var item = new GovernanceItem(id, requested.taskId(), requested.planId(), requested.issueId(),
                    requested.assetId(), requested.targetField(), requested.actionType(), requested.responsibleUserId(),
                    requested.status(), requested.assetVersion(), requested.governanceRound(), requested.scopeFingerprint(),
                    0, requested.currentResultVersionId(), requested.blockReason(), requested.reworkSourceItemId());
            jdbc.sql("UPDATE governance_item SET payload_json=:payload WHERE id=:id")
                    .param("payload", encode(item)).param("id", id).update();
        }
        return new FrozenWorkflow(snapshotId, ruleId);
    }

    @Override
    @Transactional
    public void discard(long id) {
        requireWritable();
        var taskId = jdbc.sql("SELECT task_id FROM governance_scope_snapshot WHERE id=:id")
                .param("id", id).query(Long.class).optional();
        if (taskId.isEmpty()) return;
        jdbc.sql("DELETE FROM governance_item WHERE task_id=:taskId").param("taskId", taskId.orElseThrow()).update();
        jdbc.sql("DELETE FROM governance_scope_item WHERE snapshot_id=:id").param("id", id).update();
        jdbc.sql("DELETE FROM governance_rule_snapshot WHERE task_id=:taskId").param("taskId", taskId.orElseThrow()).update();
        jdbc.sql("DELETE FROM governance_scope_snapshot WHERE id=:id").param("id", id).update();
    }
    @Override public GovernanceScopeSnapshot scopeSnapshot(long id) { return jdbc.sql("SELECT payload_json FROM governance_scope_snapshot WHERE id=:id").param("id", id).query(String.class).optional().map(v -> decode(v, GovernanceScopeSnapshot.class)).orElseThrow(() -> new IllegalArgumentException("治理范围快照不存在")); }
    @Override public List<GovernanceScopeItem> scopeItems(long id) { return jdbc.sql("SELECT payload_json FROM governance_scope_item WHERE snapshot_id=:id ORDER BY id").param("id", id).query(String.class).list().stream().map(v -> decode(v, GovernanceScopeItem.class)).toList(); }
    @Override public List<GovernanceItem> items(long taskId) { return jdbc.sql("SELECT payload_json FROM governance_item WHERE task_id=:id ORDER BY id").param("id", taskId).query(String.class).list().stream().map(v -> decode(v, GovernanceItem.class)).toList(); }
    @Override public GovernanceItem item(long id) { return jdbc.sql("SELECT payload_json FROM governance_item WHERE id=:id").param("id", id).query(String.class).optional().map(v -> decode(v, GovernanceItem.class)).orElseThrow(() -> new IllegalArgumentException("治理项不存在")); }
    @Override public GovernanceScopeSnapshot scopeSnapshotForTask(long taskId) { return jdbc.sql("SELECT payload_json FROM governance_scope_snapshot WHERE task_id=:id ORDER BY id DESC LIMIT 1").param("id", taskId).query(String.class).optional().map(v -> decode(v, GovernanceScopeSnapshot.class)).orElseThrow(() -> new IllegalArgumentException("治理任务范围快照不存在")); }
    @Override public List<GovernanceScopeItem> scopeItemsForTask(long taskId) { return scopeItems(scopeSnapshotForTask(taskId).id()); }
}
