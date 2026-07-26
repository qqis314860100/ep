package com.tianshu.assets.governance.acceptance.application;

import static com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceMetricResult.MetricApplicability.NOT_APPLICABLE;
import static com.tianshu.assets.governance.acceptance.domain.GovernanceQualityMetric.OWNER_COVERAGE;
import static com.tianshu.assets.governance.acceptance.domain.GovernanceQualityMetric.SAMPLE_ACCURACY;
import static com.tianshu.assets.governance.acceptance.domain.GovernanceQualityMetric.STANDARD_DICTIONARY_HIT_RATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.governance.acceptance.application.GovernanceQualityService.MetricObservation;
import com.tianshu.assets.governance.acceptance.application.GovernanceQualityService.QualityFact;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceSample;
import com.tianshu.assets.governance.acceptance.domain.GovernanceQualityMetric;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.support.GovernanceTestFixture;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernanceQualityServiceTest {

    private GovernanceTestFixture fixture;
    private GovernanceQualityService service;

    @BeforeEach
    void setUp() {
        fixture = GovernanceTestFixture.fieldClosure();
        service = fixture.qualityService();
    }

    @Test
    void zeroDenominatorIsNotApplicableInsteadOfOneHundredPercent() {
        var result = service.calculate(
                STANDARD_DICTIONARY_HIT_RATE, List.of(), fixture.policyAllowingNotApplicable());

        assertThat(result.numerator()).isZero();
        assertThat(result.denominator()).isZero();
        assertThat(result.value()).isNull();
        assertThat(result.applicability()).isEqualTo(NOT_APPLICABLE);
        assertThat(result.passed()).isTrue();
    }

    @Test
    void calculatesAllFiveMetricsWithOneNumeratorAndDenominatorContract() {
        var policy = fixture.policyAllowingNotApplicable();
        var facts = List.of(
                new QualityFact(501, true, true, true, true),
                new QualityFact(502, false, true, null, false));

        var results = service.calculateAll(facts, List.of(
                new MetricObservation(501, true), new MetricObservation(502, false)), policy);

        assertThat(results).extracting(result -> result.metric())
                .containsExactlyElementsOf(List.of(GovernanceQualityMetric.values()));
        assertThat(results.stream().filter(result -> result.metric() == OWNER_COVERAGE).findFirst().orElseThrow())
                .satisfies(result -> {
                    assertThat(result.numerator()).isEqualTo(1);
                    assertThat(result.denominator()).isEqualTo(2);
                    assertThat(result.value()).isEqualTo(0.5);
                    assertThat(result.affectedItemIds()).containsExactly(502L);
                });
        assertThat(results.stream().filter(
                        result -> result.metric() == STANDARD_DICTIONARY_HIT_RATE).findFirst().orElseThrow())
                .satisfies(result -> {
                    assertThat(result.numerator()).isEqualTo(1);
                    assertThat(result.denominator()).isEqualTo(1);
                });
        assertThat(results.stream().filter(result -> result.metric() == SAMPLE_ACCURACY).findFirst().orElseThrow())
                .satisfies(result -> assertThat(result.denominator()).isEqualTo(2));
    }

    @Test
    void acceptanceSampleCannotBeRegeneratedAfterAFailedCheck() {
        var round = fixture.openAcceptanceRound();
        var originalIds = round.samples().stream().map(GovernanceAcceptanceSample::itemId).toList();
        var first = round.samples().getFirst();

        service.saveSample(
                round.id(), first.itemId(), false, "功能说明与现场用途不符", "qa-1", first.version());
        var reopened = fixture.openAcceptanceRound();

        assertThat(reopened.id()).isEqualTo(round.id());
        assertThat(reopened.samples()).extracting(GovernanceAcceptanceSample::itemId)
                .containsExactlyElementsOf(originalIds);
        assertThat(reopened.samples().getFirst().passed()).isFalse();
        assertThat(reopened.metricResults().stream()
                .filter(result -> result.metric() == SAMPLE_ACCURACY).findFirst().orElseThrow().passed())
                .isFalse();
    }

    @Test
    void failedSampleRequiresCommentAndUsesOptimisticVersion() {
        var round = fixture.openAcceptanceRound();
        var sample = round.samples().getFirst();

        assertThatThrownBy(() -> service.saveSample(
                round.id(), sample.itemId(), false, " ", "qa-1", sample.version()))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("验收不通过必须填写问题说明");

        var saved = service.saveSample(
                round.id(), sample.itemId(), true, "", "qa-1", sample.version());
        assertThatThrownBy(() -> service.saveSample(
                round.id(), sample.itemId(), true, "", "qa-1", sample.version()))
                .isInstanceOf(GovernanceVersionConflictException.class);
        assertThat(saved.version()).isEqualTo(sample.version() + 1);
    }
}
