package com.tianshu.assets.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.ai.domain.AiProposedFields;
import com.tianshu.assets.ai.domain.AiSuggestion;
import com.tianshu.assets.ai.domain.AiSuggestionStatus;
import com.tianshu.assets.ai.domain.AiSuggestionTargetType;
import com.tianshu.assets.ai.domain.AiTargetScope;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcAiSuggestionRepositoryTest {

    private JdbcAiSuggestionRepository repository;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:ai;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE ai_suggestion (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    target_type VARCHAR(32) NOT NULL,
                    target_id BIGINT NOT NULL,
                    target_title VARCHAR(200) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    source VARCHAR(32) NOT NULL DEFAULT 'AI',
                    proposed VARCHAR(2000) NOT NULL,
                    evidence VARCHAR(2000) NOT NULL,
                    confidence DOUBLE NULL,
                    scopes VARCHAR(4000) NOT NULL,
                    created_by VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    resolved_by VARCHAR(64) NOT NULL,
                    resolved_at TIMESTAMP NULL,
                    version BIGINT NOT NULL
                )
                """);
        repository = new JdbcAiSuggestionRepository(jdbc);
    }

    private AiSuggestion sample() {
        return new AiSuggestion(
                0,
                AiSuggestionTargetType.ASSET,
                103,
                "输送模块布置数模",
                AiSuggestionStatus.PENDING,
                "AI",
                new AiProposedFields("输送模块布置数模（AI 整理）", "AI 描述", "THREE_DIMENSIONAL_MODEL",
                        List.of("输送", "模块"), null, null),
                List.of("证据片段一", "证据片段二"),
                0.93,
                List.of(new AiTargetScope("乘用车", "底部水冷", "H03", "宁德基地", "A 拉线", "焊接段")),
                "ai-service",
                Instant.parse("2026-09-09T08:00:00Z"),
                "",
                null,
                0);
    }

    @Test
    void savesFindsAndUpdatesSuggestion() {
        var saved = repository.save(sample());
        assertThat(saved.id()).isPositive();
        assertThat(saved.status()).isEqualTo(AiSuggestionStatus.PENDING);
        assertThat(saved.version()).isZero();

        var found = repository.findById(saved.id()).orElseThrow();
        assertThat(found.targetId()).isEqualTo(103);
        assertThat(found.proposed().name()).isEqualTo("输送模块布置数模（AI 整理）");
        assertThat(found.proposed().tags()).containsExactlyInAnyOrder("输送", "模块");
        assertThat(found.evidence()).containsExactly("证据片段一", "证据片段二");
        assertThat(found.scopes().getFirst().base()).isEqualTo("宁德基地");

        var resolved = repository.update(
                found.asResolved(AiSuggestionStatus.CONFIRMED, "emp-admin",
                        Instant.parse("2026-09-09T08:01:00Z")),
                found.version());
        assertThat(resolved.status()).isEqualTo(AiSuggestionStatus.CONFIRMED);
        assertThat(resolved.version()).isEqualTo(1);
        assertThat(repository.findById(saved.id()).orElseThrow().resolvedBy()).isEqualTo("emp-admin");
    }

    @Test
    void updateWithStaleVersionFails() {
        var saved = repository.save(sample());
        repository.update(saved.asResolved(AiSuggestionStatus.REJECTED, "emp-admin", Instant.now()), saved.version());
        assertThatThrownBy(() -> repository.update(saved, saved.version()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findsByTarget() {
        var saved = repository.save(sample());
        var found = repository.findByTarget(AiSuggestionTargetType.ASSET, 103);
        assertThat(found).extracting(AiSuggestion::id).containsExactly(saved.id());
        assertThat(repository.findByTarget(AiSuggestionTargetType.ASSET, 999)).isEmpty();
    }
}
