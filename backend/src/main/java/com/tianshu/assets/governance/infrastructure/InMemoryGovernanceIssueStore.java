package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryGovernanceIssueStore implements GovernanceIssueStore {

    private final Map<Long, GovernanceIssue> issues = new LinkedHashMap<>();
    private final AtomicLong nextIssueId = new AtomicLong(1);

    public static InMemoryGovernanceIssueStore withFieldSeeds() {
        var store = new InMemoryGovernanceIssueStore();
        store.insertAll(List.of(
                issue(1001, 101, GovernanceField.DESCRIPTION, "MISSING_DESCRIPTION", "/description",
                        "", "HIGH", true),
                issue(1002, 102, GovernanceField.SPECIALTIES, "MISSING_SPECIALTIES", "/specialties",
                        "[]", "HIGH", true),
                issue(1003, 103, GovernanceField.OWNER, "MISSING_OWNER", "/ownerUserId",
                        "null", "MEDIUM", false),
                issue(1004, 104, GovernanceField.SCOPE, "MISSING_SCOPE", "/scopes",
                        "[]", "MEDIUM", true),
                issue(1005, 105, GovernanceField.DESCRIPTION, "MISSING_DESCRIPTION", "/description",
                        "null", "LOW", false)));
        return store;
    }

    @Override
    public synchronized List<GovernanceIssue> find(
            GovernanceField field, GovernanceIssueStatus status, Long assetId) {
        return issues.values().stream()
                .filter(issue -> field == null || issue.targetField() == field)
                .filter(issue -> status == null || issue.status() == status)
                .filter(issue -> assetId == null || issue.assetId() == assetId)
                .sorted(Comparator.comparingLong(GovernanceIssue::id))
                .toList();
    }

    @Override
    public synchronized List<GovernanceIssue> findByIds(List<Long> issueIds) {
        if (issueIds == null) return List.of();
        return issueIds.stream().map(issues::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public synchronized List<GovernanceIssue> insertAll(List<GovernanceIssue> requested) {
        if (requested == null || requested.isEmpty()) return List.of();
        var existingFingerprints = issues.values().stream().map(GovernanceIssue::fingerprint).collect(
                java.util.stream.Collectors.toSet());
        var batchFingerprints = new java.util.HashSet<String>();
        var requestedIds = new java.util.HashSet<Long>();
        for (var issue : requested) {
            if (!batchFingerprints.add(issue.fingerprint()) || existingFingerprints.contains(issue.fingerprint())) {
                throw new GovernanceConflictException("治理问题已存在");
            }
            if (issue.id() > 0 && (!requestedIds.add(issue.id()) || issues.containsKey(issue.id()))) {
                throw new GovernanceConflictException("治理问题已存在");
            }
        }

        var inserted = new ArrayList<GovernanceIssue>(requested.size());
        for (var issue : requested) {
            var id = issue.id() > 0 ? issue.id() : nextIssueId.getAndIncrement();
            var created = copy(issue, id, issue.version());
            issues.put(id, created);
            nextIssueId.accumulateAndGet(id + 1, Math::max);
            inserted.add(created);
        }
        return List.copyOf(inserted);
    }

    @Override
    public synchronized GovernanceIssue upsertScanned(GovernanceIssue issue) {
        var current = issues.values().stream().filter(item -> item.fingerprint().equals(issue.fingerprint())).findFirst();
        if (current.isEmpty()) return insertAll(List.of(issue)).getFirst();
        var existing = current.get();
        if (existing.status() == GovernanceIssueStatus.RESOLVED) {
            var reopened = new GovernanceIssue(existing.id(), issue.assetId(), issue.targetField(), issue.issueType(), issue.targetPath(),
                    issue.ruleCode(), issue.ruleVersion(), issue.originalFactJson(), issue.assetVersion(), issue.scopeFingerprint(), issue.severity(), issue.blocking(),
                    GovernanceIssueStatus.OPEN, null, existing.version() + 1);
            issues.put(existing.id(), reopened); return reopened;
        }
        if (existing.assetVersion() == issue.assetVersion() && existing.originalFactJson().equals(issue.originalFactJson())) return existing;
        var refreshed = new GovernanceIssue(existing.id(), issue.assetId(), issue.targetField(), issue.issueType(), issue.targetPath(), issue.ruleCode(), issue.ruleVersion(), issue.originalFactJson(), issue.assetVersion(), issue.scopeFingerprint(), issue.severity(), issue.blocking(), existing.status(), existing.taskId(), existing.version() + 1);
        issues.put(existing.id(), refreshed); return refreshed;
    }

    @Override
    public synchronized void claimOpen(List<GovernanceIssue> expectedIssues, long taskId) {
        for (var expected : expectedIssues) {
            var current = issues.get(expected.id());
            if (current == null || current.status() != GovernanceIssueStatus.OPEN
                    || current.version() != expected.version()) {
                throw new GovernanceConflictException("问题已被其他治理任务纳入");
            }
        }
        var claimed = expectedIssues.stream().map(issue -> issues.get(issue.id()).claim(taskId)).toList();
        claimed.forEach(issue -> issues.put(issue.id(), issue));
    }

    @Override
    public synchronized List<GovernanceIssue> findClaimedByTask(long taskId) {
        return issues.values().stream()
                .filter(issue -> issue.status() == GovernanceIssueStatus.CLAIMED)
                .filter(issue -> issue.taskId() != null && issue.taskId() == taskId)
                .sorted(Comparator.comparingLong(GovernanceIssue::id))
                .toList();
    }

    @Override
    public synchronized GovernanceIssue resolve(long issueId, long expectedVersion) {
        var current = issues.get(issueId);
        if (current == null) throw new IllegalArgumentException("治理问题不存在");
        if (current.version() != expectedVersion) {
            throw new GovernanceConflictException("治理问题已变化，请刷新后重试");
        }
        if (current.status() == GovernanceIssueStatus.RESOLVED) return current;
        if (current.status() != GovernanceIssueStatus.CLAIMED) {
            throw new GovernanceConflictException("只有已领取问题可以解决");
        }
        var resolved = new GovernanceIssue(
                current.id(), current.assetId(), current.targetField(), current.issueType(), current.targetPath(),
                current.ruleCode(), current.ruleVersion(), current.originalFactJson(), current.assetVersion(),
                current.scopeFingerprint(), current.severity(), current.blocking(), GovernanceIssueStatus.RESOLVED,
                current.taskId(), current.version() + 1);
        issues.put(resolved.id(), resolved);
        return resolved;
    }

    private static GovernanceIssue issue(
            long id, long assetId, GovernanceField field, String issueType, String targetPath,
            String originalFactJson, String severity, boolean blocking) {
        return new GovernanceIssue(
                id, assetId, field, issueType, targetPath, "FIELD_REQUIRED", 1,
                originalFactJson, 0, "", severity, blocking, GovernanceIssueStatus.OPEN, null, 0);
    }

    private GovernanceIssue copy(GovernanceIssue issue, long id, long version) {
        return new GovernanceIssue(
                id, issue.assetId(), issue.targetField(), issue.issueType(), issue.targetPath(),
                issue.ruleCode(), issue.ruleVersion(), issue.originalFactJson(), issue.assetVersion(),
                issue.scopeFingerprint(), issue.severity(), issue.blocking(), issue.status(), issue.taskId(), version);
    }
}
