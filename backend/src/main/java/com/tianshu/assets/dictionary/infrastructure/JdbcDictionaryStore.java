package com.tianshu.assets.dictionary.infrastructure;

import com.tianshu.assets.dictionary.application.DictionaryConflictException;
import com.tianshu.assets.dictionary.application.DictionaryStore;
import com.tianshu.assets.dictionary.domain.DictionaryItem;
import com.tianshu.assets.dictionary.domain.DictionaryStatus;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "oceanbase"})
public class JdbcDictionaryStore implements DictionaryStore {

    private final JdbcTemplate jdbcTemplate;
    private final boolean databaseWritesEnabled;

    public JdbcDictionaryStore(JdbcTemplate jdbcTemplate,
            @Value("${asset.database-writes-enabled:false}") boolean databaseWritesEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseWritesEnabled = databaseWritesEnabled;
    }

    @Override
    public List<DictionaryItem> findAll() {
        return jdbcTemplate.query("""
                SELECT id, category_code, item_code, item_name, parent_id, status, sort_order, usage_count,
                       version, description, forward_name, reverse_name, directional, allow_duplicate,
                       merge_target_id, updated_at
                FROM dictionary_item
                """, (rs, rowNum) -> new DictionaryItem(rs.getLong("id"), rs.getString("category_code"),
                rs.getString("item_code"), rs.getString("item_name"), nullableLong(rs, "parent_id"),
                DictionaryStatus.valueOf(rs.getString("status")), rs.getInt("sort_order"),
                rs.getLong("usage_count"), rs.getLong("version"), rs.getString("description"),
                rs.getString("forward_name"), rs.getString("reverse_name"), rs.getBoolean("directional"),
                rs.getBoolean("allow_duplicate"), nullableLong(rs, "merge_target_id"),
                rs.getTimestamp("updated_at").toLocalDateTime()));
    }

    @Override
    public Optional<DictionaryItem> findById(long id) {
        return findAll().stream().filter(item -> item.id() == id).findFirst();
    }

    @Override
    public DictionaryItem create(DictionaryItem item) {
        requireWritable();
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO dictionary_item
                        (category_code, item_code, item_name, parent_id, status, sort_order, usage_count,
                         version, description, forward_name, reverse_name, directional, allow_duplicate)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, item.category());
            statement.setString(2, item.code());
            statement.setString(3, item.name());
            if (item.parentId() == null) statement.setNull(4, java.sql.Types.BIGINT); else statement.setLong(4, item.parentId());
            statement.setString(5, item.status().name());
            statement.setInt(6, item.sortOrder());
            statement.setLong(7, item.usageCount());
            statement.setString(8, item.description());
            statement.setString(9, item.forwardName());
            statement.setString(10, item.reverseName());
            statement.setBoolean(11, item.directional());
            statement.setBoolean(12, item.allowDuplicate());
            return statement;
        }, keyHolder);
        var id = keyHolder.getKey().longValue();
        return findById(id).orElseThrow(() -> new DictionaryConflictException("新增字典项后读取失败"));
    }

    @Override
    public DictionaryItem update(DictionaryItem item, long expectedVersion) {
        requireWritable();
        var updated = jdbcTemplate.update("""
                UPDATE dictionary_item
                SET item_code = ?, item_name = ?, parent_id = ?, status = ?, sort_order = ?, description = ?,
                    forward_name = ?, reverse_name = ?, directional = ?, allow_duplicate = ?, merge_target_id = ?,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND version = ?
                """, item.code(), item.name(), item.parentId(), item.status().name(), item.sortOrder(),
                item.description(), item.forwardName(), item.reverseName(), item.directional(),
                item.allowDuplicate(), item.mergeTargetId(), item.id(), expectedVersion);
        if (updated != 1) throw new DictionaryConflictException("字典项已被其他用户更新，请刷新后重试");
        return findById(item.id()).orElseThrow(() -> new DictionaryConflictException("更新字典项后读取失败"));
    }

    private Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        var value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private void requireWritable() {
        if (!databaseWritesEnabled) throw new UnsupportedOperationException("当前数据库适配器为只读模式");
    }
}
