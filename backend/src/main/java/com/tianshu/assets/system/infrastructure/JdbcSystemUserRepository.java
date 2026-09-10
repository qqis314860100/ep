package com.tianshu.assets.system.infrastructure;

import com.tianshu.assets.system.application.SystemUserConflictException;
import com.tianshu.assets.system.domain.SystemRole;
import com.tianshu.assets.system.domain.SystemUser;
import com.tianshu.assets.system.domain.SystemUserRepository;
import com.tianshu.assets.system.domain.SystemUserScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 真实数据库（{@code sys_user} / {@code sys_user_role} / {@code sys_user_scope}）用户仓储。
 *
 * <p>登录凭证、角色与数据范围全部来自库表；不再有任何硬编码演示账号。
 * 写操作遵循 {@code asset.database-writes-enabled} 总开关（OceanBase 只读部署下拒绝写入）。
 */
@Repository
@Profile({"local", "oceanbase"})
public class JdbcSystemUserRepository implements SystemUserRepository {

    private final JdbcTemplate jdbcTemplate;
    private final boolean databaseWritesEnabled;

    public JdbcSystemUserRepository(JdbcTemplate jdbcTemplate,
            @Value("${asset.database-writes-enabled:false}") boolean databaseWritesEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseWritesEnabled = databaseWritesEnabled;
    }

    @Override
    public List<SystemUser> findAll() {
        var roles = rolesByUser();
        var scopes = scopesByUser();
        return jdbcTemplate.query("""
                SELECT id, code, name, department, password_hash, version, updated_at
                FROM sys_user
                WHERE status = 1
                ORDER BY id
                """, (rs, rowNum) -> mapUser(rs, roles, scopes));
    }

    @Override
    public Optional<SystemUser> findById(long id) {
        return findAll().stream().filter(user -> user.id() == id).findFirst();
    }

    @Override
    public Optional<SystemUser> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        var normalized = userId.trim();
        return findAll().stream().filter(user -> user.userId().equals(normalized)).findFirst();
    }

    @Override
    @Transactional
    public SystemUser update(SystemUser user, long expectedVersion) {
        requireWritable();
        var updated = jdbcTemplate.update("""
                UPDATE sys_user
                SET name = ?, department = ?, password_hash = ?,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND version = ?
                """, user.name(), user.department(), user.passwordHash() == null ? "" : user.passwordHash(),
                user.id(), expectedVersion);
        if (updated != 1) {
            throw new SystemUserConflictException("用户权限已被其他管理员修改，请刷新后重试");
        }
        replaceRoles(user.id(), user.roles());
        replaceScopes(user.id(), user.scopes());
        return findById(user.id())
                .orElseThrow(() -> new SystemUserConflictException("更新用户后读取失败"));
    }

    private void replaceRoles(long userId, Set<SystemRole> roles) {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        for (var role : roles) {
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role) VALUES (?, ?)", userId, role.name());
        }
    }

    private void replaceScopes(long userId, List<SystemUserScope> scopes) {
        jdbcTemplate.update("DELETE FROM sys_user_scope WHERE user_id = ?", userId);
        for (var scope : scopes) {
            jdbcTemplate.update("""
                    INSERT INTO sys_user_scope (user_id, base_name, product_line) VALUES (?, ?, ?)
                    """, userId, scope.base(), scope.productLine());
        }
    }

    private Map<Long, Set<SystemRole>> rolesByUser() {
        Map<Long, Set<SystemRole>> grouped = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT user_id, role FROM sys_user_role ORDER BY user_id, role", rs -> {
            var roles = grouped.computeIfAbsent(rs.getLong("user_id"),
                    ignored -> new java.util.LinkedHashSet<>());
            try {
                roles.add(SystemRole.valueOf(rs.getString("role")));
            } catch (IllegalArgumentException unknownRole) {
                // 库中出现未知角色枚举：忽略而不是让整个登录链路炸掉。
            }
        });
        return grouped;
    }

    private Map<Long, List<SystemUserScope>> scopesByUser() {
        Map<Long, List<SystemUserScope>> grouped = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT id, user_id, base_name, product_line FROM sys_user_scope ORDER BY user_id, id
                """, rs -> {
            grouped.computeIfAbsent(rs.getLong("user_id"), ignored -> new ArrayList<>())
                    .add(new SystemUserScope(rs.getLong("id"),
                            rs.getString("base_name") == null ? "" : rs.getString("base_name"),
                            rs.getString("product_line") == null ? "" : rs.getString("product_line")));
        });
        return grouped;
    }

    private SystemUser mapUser(ResultSet rs, Map<Long, Set<SystemRole>> roles,
            Map<Long, List<SystemUserScope>> scopes) throws SQLException {
        var id = rs.getLong("id");
        return new SystemUser(
                id,
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("department") == null ? "" : rs.getString("department"),
                roles.getOrDefault(id, Set.of()),
                scopes.getOrDefault(id, List.of()),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"),
                rs.getString("password_hash"));
    }

    private void requireWritable() {
        if (!databaseWritesEnabled) throw new UnsupportedOperationException("当前数据库适配器为只读模式");
    }
}
