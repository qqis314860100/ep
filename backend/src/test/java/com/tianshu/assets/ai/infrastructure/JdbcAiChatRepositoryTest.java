package com.tianshu.assets.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.tianshu.assets.ai.domain.AiChatMessage;
import com.tianshu.assets.ai.domain.AiChatMessageRole;
import com.tianshu.assets.ai.domain.AiChatSession;
import com.tianshu.assets.ai.domain.AiCitation;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcAiChatRepositoryTest {

    private JdbcAiChatRepository store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:chat;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE ai_chat_session (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    title VARCHAR(120) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE ai_chat_message (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    session_id BIGINT NOT NULL,
                    role VARCHAR(16) NOT NULL,
                    content TEXT NOT NULL,
                    citations TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        store = new JdbcAiChatRepository(jdbc);
    }

    private AiChatSession session(String userId, String title) {
        var now = Instant.parse("2026-09-09T09:00:00Z");
        return store.saveSession(new AiChatSession(0, userId, title, now, now));
    }

    @Test
    void persistsSessionsMessagesAndCascadesDelete() {
        var session = session("emp-admin", "第一个会话");
        assertThat(session.id()).isPositive();

        var userMessage = store.saveMessage(new AiChatMessage(0, session.id(), AiChatMessageRole.USER,
                "问题", List.of(), Instant.parse("2026-09-09T09:01:00Z")));
        var assistant = store.saveMessage(new AiChatMessage(0, session.id(), AiChatMessageRole.ASSISTANT,
                "回答", List.of(new AiCitation("doc-1", "第3页", "摘录", true)),
                Instant.parse("2026-09-09T09:01:05Z")));

        assertThat(store.findSession(session.id())).isPresent();
        assertThat(store.listSessions("emp-admin")).hasSize(1);
        var messages = store.listMessages(session.id());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).id()).isEqualTo(assistant.id());
        assertThat(messages.get(1).citations().getFirst().docId()).isEqualTo("doc-1");

        var renamed = store.updateSession(new AiChatSession(session.id(), "emp-admin", "改后标题",
                session.createdAt(), Instant.parse("2026-09-09T09:02:00Z")));
        assertThat(store.findSession(session.id()).orElseThrow().title()).isEqualTo("改后标题");
        assertThat(userMessage.content()).isEqualTo("问题");
        assertThat(renamed.updatedAt()).isEqualTo(Instant.parse("2026-09-09T09:02:00Z"));

        store.deleteSession(session.id());
        assertThat(store.findSession(session.id())).isEmpty();
        assertThat(store.listMessages(session.id())).isEmpty();
    }

    @Test
    void listsSessionsByUserInUpdatedDescOrder() {
        var first = session("emp-admin", "会话一");
        var second = session("emp-admin", "会话二");
        session("emp-chen", "别人的会话");
        store.updateSession(new AiChatSession(first.id(), "emp-admin", "会话一",
                first.createdAt(), Instant.parse("2026-09-09T10:00:00Z")));

        var mine = store.listSessions("emp-admin");
        assertThat(mine).extracting(AiChatSession::title)
                .containsExactly("会话一", "会话二");
        assertThat(second.id()).isNotEqualTo(first.id());
    }
}
