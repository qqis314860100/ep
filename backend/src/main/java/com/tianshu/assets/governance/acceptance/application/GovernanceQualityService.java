package com.tianshu.assets.governance.acceptance.application;

import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceMetricResult;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceMetricResult.MetricApplicability;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceSample;
import com.tianshu.assets.governance.acceptance.domain.GovernanceQualityMetric;
import com.tianshu.assets.governance.acceptance.domain.GovernanceQualityPolicySnapshot;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceQualityService {

    private final GovernanceAcceptanceStore store;
    private final Clock clock;

    public GovernanceQualityService(GovernanceAcceptanceStore store) {
        this(store, Clock.systemUTC());
    }

    public GovernanceQualityService(GovernanceAcceptanceStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public GovernanceAcceptanceMetricResult calculate(
            GovernanceQualityMetric metric,
            List<MetricObservation> observations,
            GovernanceQualityPolicySnapshot policy) {
        if (metric == null || observations == null || policy == null) {
            throw new GovernanceValidationException("质量指标、事实和策略不能为空");
        }
        if (observations.stream().map(MetricObservation::itemId).distinct().count() != observations.size()) {
            throw new GovernanceValidationException("同一质量指标不能重复计算治理项");
        }
        var denominator = observations.size();
        var numerator = (int) observations.stream().filter(MetricObservation::passed).count();
        var affected = observations.stream().filter(observation -> !observation.passed())
                .map(MetricObservation::itemId).toList();
        var applicability = denominator == 0
                ? MetricApplicability.NOT_APPLICABLE : MetricApplicability.APPLICABLE;
        var value = denominator == 0 ? null : (double) numerator / denominator;
        var passed = denominator == 0
                ? policy.notApplicablePasses() : value >= policy.threshold(metric);
        return new GovernanceAcceptanceMetricResult(
                0, 0, metric, numerator, denominator, value, policy.threshold(metric),
                applicability, passed, affected, 0);
    }

    public List<GovernanceAcceptanceMetricResult> calculateAll(
            List<QualityFact> facts,
            List<MetricObservation> sampleObservations,
            GovernanceQualityPolicySnapshot policy) {
        if (facts == null || sampleObservations == null) {
            throw new GovernanceValidationException("质量事实不能为空");
        }
        if (facts.stream().map(QualityFact::itemId).distinct().count() != facts.size()) {
            throw new GovernanceValidationException("质量事实治理项不能重复");
        }
        return List.of(
                calculate(GovernanceQualityMetric.REQUIRED_FIELD_COMPLETENESS,
                        facts.stream().map(fact -> new MetricObservation(
                                fact.itemId(), fact.requiredFieldsComplete())).toList(), policy),
                calculate(GovernanceQualityMetric.ASSET_SCOPE_VALIDITY,
                        facts.stream().map(fact -> new MetricObservation(
                                fact.itemId(), fact.scopeValid())).toList(), policy),
                calculate(GovernanceQualityMetric.STANDARD_DICTIONARY_HIT_RATE,
                        facts.stream().filter(fact -> fact.standardDictionaryHit() != null)
                                .map(fact -> new MetricObservation(
                                        fact.itemId(), fact.standardDictionaryHit())).toList(), policy),
                calculate(GovernanceQualityMetric.OWNER_COVERAGE,
                        facts.stream().map(fact -> new MetricObservation(
                                fact.itemId(), fact.ownerCovered())).toList(), policy),
                calculate(GovernanceQualityMetric.SAMPLE_ACCURACY, sampleObservations, policy));
    }

    @Transactional
    public GovernanceAcceptanceRound openRound(
            long taskId,
            int governanceRound,
            GovernanceQualityPolicySnapshot policy,
            List<QualityFact> facts) {
        if (taskId <= 0 || governanceRound <= 0 || policy == null || facts == null || facts.isEmpty()) {
            throw new GovernanceValidationException("验收轮次范围和质量事实不能为空");
        }
        var current = store.currentRound(taskId);
        if (current.filter(round -> round.governanceRound() == governanceRound).isPresent()) {
            var existing = current.orElseThrow();
            if (!existing.policy().equals(policy)) {
                throw new GovernanceConflictException("当前验收轮次的质量策略已经固化");
            }
            return existing;
        }
        if (current.filter(round -> round.governanceRound() > governanceRound).isPresent()) {
            throw new GovernanceConflictException("不能创建早于当前轮次的验收记录");
        }
        var metrics = calculateAll(facts, List.of(), policy);
        var samples = fixedSampleItemIds(taskId, governanceRound, policy, facts).stream()
                .map(itemId -> new GovernanceAcceptanceSample(
                        0, 0, itemId, null, "", "", null, 0))
                .toList();
        return store.createRound(new GovernanceAcceptanceRound(
                0, taskId, governanceRound, policy, metrics, samples,
                GovernanceAcceptanceRound.Status.OPEN, Instant.now(clock), null, 0));
    }

    public GovernanceAcceptanceRound currentRound(long taskId) {
        return store.currentRound(taskId)
                .orElseThrow(() -> new GovernanceConflictException("治理任务尚未创建验收轮次"));
    }

    @Transactional
    public GovernanceAcceptanceSample saveSample(
            long roundId,
            long itemId,
            boolean passed,
            String issueDescription,
            String reviewerUserId,
            long expectedVersion) {
        if (!passed && (issueDescription == null || issueDescription.isBlank())) {
            throw new GovernanceValidationException("验收不通过必须填写问题说明");
        }
        if (reviewerUserId == null || reviewerUserId.isBlank()) {
            throw new GovernanceValidationException("样本检查人不能为空");
        }
        var round = store.round(roundId);
        if (round.status() != GovernanceAcceptanceRound.Status.OPEN) {
            throw new GovernanceConflictException("验收轮次已经完成");
        }
        var existing = round.samples().stream().filter(sample -> sample.itemId() == itemId)
                .findFirst().orElseThrow(() -> new GovernanceValidationException("治理项不属于固定验收样本"));
        if (existing.version() != expectedVersion) {
            throw new GovernanceVersionConflictException("验收样本已变化，请刷新后重试");
        }
        var saved = new GovernanceAcceptanceSample(
                existing.id(), existing.roundId(), existing.itemId(), passed, issueDescription,
                reviewerUserId, Instant.now(clock), existing.version() + 1);
        var samples = round.samples().stream().map(sample ->
                sample.itemId() == itemId ? saved : sample).toList();
        var observations = samples.stream().filter(sample -> sample.passed() != null)
                .map(sample -> new MetricObservation(sample.itemId(), sample.passed())).toList();
        var sampleMetric = calculate(GovernanceQualityMetric.SAMPLE_ACCURACY, observations, round.policy());
        var metrics = round.metricResults().stream().map(result ->
                result.metric() == GovernanceQualityMetric.SAMPLE_ACCURACY
                        ? copyMetricIdentity(result, sampleMetric) : result).toList();
        var updated = store.updateRound(new GovernanceAcceptanceRound(
                round.id(), round.taskId(), round.governanceRound(), round.policy(), metrics, samples,
                round.status(), round.createdAt(), round.completedAt(), round.version()), round.version());
        return updated.samples().stream().filter(sample -> sample.itemId() == itemId).findFirst().orElseThrow();
    }

    private GovernanceAcceptanceMetricResult copyMetricIdentity(
            GovernanceAcceptanceMetricResult identity, GovernanceAcceptanceMetricResult result) {
        return new GovernanceAcceptanceMetricResult(
                identity.id(), identity.roundId(), identity.metric(), result.numerator(), result.denominator(),
                result.value(), result.threshold(), result.applicability(), result.passed(),
                result.affectedItemIds(), identity.version() + 1);
    }

    private List<Long> fixedSampleItemIds(
            long taskId,
            int governanceRound,
            GovernanceQualityPolicySnapshot policy,
            List<QualityFact> facts) {
        if (!policy.samplingRequired()) return List.of();
        var candidates = new ArrayList<>(facts.stream().map(QualityFact::itemId).distinct().sorted().toList());
        Collections.shuffle(candidates, new Random(Objects.hash(taskId, governanceRound, policy.version())));
        return List.copyOf(candidates.subList(0, Math.min(policy.sampleSize(), candidates.size())));
    }

    public record MetricObservation(long itemId, boolean passed) {
        public MetricObservation {
            if (itemId <= 0) throw new IllegalArgumentException("质量指标事实治理项不合法");
        }
    }

    public record QualityFact(
            long itemId,
            boolean requiredFieldsComplete,
            boolean scopeValid,
            Boolean standardDictionaryHit,
            boolean ownerCovered) {
        public QualityFact {
            if (itemId <= 0) throw new IllegalArgumentException("质量事实治理项不合法");
        }
    }
}
