package com.tianshu.assets.governance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcGovernanceDictionaryVersionProviderTest {

    private JdbcGovernanceDictionaryVersionProvider provider;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("governance-dictionary-version;MODE=MySQL;DB_CLOSE_DELAY=-1")
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("""
                CREATE TABLE dictionary_item (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    category_code VARCHAR(80) NOT NULL,
                    version BIGINT NOT NULL
                )
                """);
        provider = new JdbcGovernanceDictionaryVersionProvider(JdbcClient.create(dataSource));
    }

    @Test
    void computesSpecialtyAndCombinedScopeVersionsFromRealDictionaryRows() {
        jdbcTemplate.update("INSERT INTO dictionary_item(category_code,version) VALUES ('SPECIALTY',5)");
        jdbcTemplate.update("INSERT INTO dictionary_item(category_code,version) VALUES ('SPECIALTY',3)");
        jdbcTemplate.update("INSERT INTO dictionary_item(category_code,version) VALUES ('PRODUCT_LINE',8)");
        jdbcTemplate.update("INSERT INTO dictionary_item(category_code,version) VALUES ('BASE',4)");
        jdbcTemplate.update("INSERT INTO dictionary_item(category_code,version) VALUES ('TAG',99)");

        assertThat(provider.currentVersions())
                .containsEntry("specialty", 5L)
                .containsEntry("scope", 8L)
                .hasSize(2);
    }
}
