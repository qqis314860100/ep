package com.tianshu.assets.governance.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads the current versions of the dictionary groups used by governance rules.
 *
 * <p>The dictionary table versions are optimistic-lock versions for individual
 * rows. A governance rule snapshot records the greatest version in each logical
 * group, while the enabled catalog reads this provider again at execution time
 * so changes made after task start are detected.</p>
 */
@Repository
@Profile({"local", "oceanbase"})
@ConditionalOnProperty(name = "asset.governance-schema-enabled", havingValue = "true")
public class JdbcGovernanceDictionaryVersionProvider {

    private static final List<String> SCOPE_CATEGORIES = List.of(
            "PLATFORM_FAMILY", "PLATFORM_VARIANT", "PRODUCT_LINE",
            "BASE", "PRODUCTION_LINE", "PROCESS_SECTION");

    private final JdbcClient jdbc;

    public JdbcGovernanceDictionaryVersionProvider(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Long> currentVersions() {
        var versions = new LinkedHashMap<String, Long>();
        versions.put("specialty", currentVersion(List.of("SPECIALTY")));
        versions.put("scope", currentVersion(SCOPE_CATEGORIES));
        return Map.copyOf(versions);
    }

    private long currentVersion(List<String> categories) {
        return jdbc.sql("SELECT COALESCE(MAX(version), 0) FROM dictionary_item WHERE category_code IN (:categories)")
                .param("categories", categories)
                .query(Long.class)
                .single();
    }
}
