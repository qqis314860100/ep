package com.tianshu.assets.system.infrastructure;

import com.tianshu.assets.system.domain.OperationLog;
import com.tianshu.assets.system.domain.OperationLogCriteria;
import com.tianshu.assets.system.domain.OperationLogStore;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 本地（local profile）操作日志仓储，写入 tianshu.operation_log_ext。
 *
 * <p>该表是本地镜像专用表（见 scripts/db/prepare_local_db.sh），OceanBase 正本 schema 不含；
 * 因此本实现仅注册于 local profile。资产/文档写操作在真实数据形态下因此可落库审计。</p>
 */
@Repository
@Profile("local")
public class JdbcOperationLogStore implements OperationLogStore {

    private final JdbcTemplate jdbc;

    public JdbcOperationLogStore(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public OperationLog append(OperationLog log) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO operation_log_ext"
                            + " (actor_user_id, action, target_type, target_id, detail_json, created_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, log.actorUserId());
            ps.setString(2, log.action());
            ps.setString(3, log.targetType());
            ps.setLong(4, log.targetId());
            ps.setString(5, log.detailJson());
            ps.setTimestamp(6, Timestamp.from(log.createdAt()));
            return ps;
        }, key);
        var id = key.getKey() == null ? 0L : key.getKey().longValue();
        return new OperationLog(id, log.actorUserId(), log.action(), log.targetType(), log.targetId(),
                log.detailJson(), log.createdAt());
    }

    @Override
    public List<OperationLog> query(OperationLogCriteria criteria) {
        var where = new ArrayList<String>();
        var args = new ArrayList<Object>();
        if (!criteria.actorUserId().isBlank()) {
            where.add("actor_user_id = ?");
            args.add(criteria.actorUserId());
        }
        if (!criteria.action().isBlank()) {
            where.add("action = ?");
            args.add(criteria.action());
        }
        if (!criteria.targetType().isBlank()) {
            where.add("target_type = ?");
            args.add(criteria.targetType());
        }
        if (criteria.from() != null) {
            where.add("created_at >= ?");
            args.add(Timestamp.from(criteria.from()));
        }
        if (criteria.to() != null) {
            where.add("created_at <= ?");
            args.add(Timestamp.from(criteria.to()));
        }
        var sql = "SELECT id, actor_user_id, action, target_type, target_id, detail_json, created_at"
                + " FROM operation_log_ext";
        if (!where.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", where);
        }
        sql += " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        args.add(criteria.perPage());
        args.add((long) (criteria.page() - 1) * criteria.perPage());
        return jdbc.query(sql, (rs, rowNum) -> new OperationLog(
                rs.getLong("id"),
                rs.getString("actor_user_id"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getLong("target_id"),
                nullableJson(rs.getString("detail_json")),
                rs.getTimestamp("created_at").toInstant()), args.toArray());
    }

    @Override
    public long count(OperationLogCriteria criteria) {
        var where = new ArrayList<String>();
        var args = new ArrayList<Object>();
        if (!criteria.actorUserId().isBlank()) {
            where.add("actor_user_id = ?");
            args.add(criteria.actorUserId());
        }
        if (!criteria.action().isBlank()) {
            where.add("action = ?");
            args.add(criteria.action());
        }
        if (!criteria.targetType().isBlank()) {
            where.add("target_type = ?");
            args.add(criteria.targetType());
        }
        if (criteria.from() != null) {
            where.add("created_at >= ?");
            args.add(Timestamp.from(criteria.from()));
        }
        if (criteria.to() != null) {
            where.add("created_at <= ?");
            args.add(Timestamp.from(criteria.to()));
        }
        var sql = "SELECT COUNT(*) FROM operation_log_ext";
        if (!where.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", where);
        }
        var count = jdbc.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private String nullableJson(String value) {
        return value == null ? "{}" : value;
    }
}
