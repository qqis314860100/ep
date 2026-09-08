package com.tianshu.assets.system.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record SystemUser(
        long id,
        String userId,
        String name,
        String department,
        Set<SystemRole> roles,
        List<SystemUserScope> scopes,
        Instant updatedAt,
        long version,
        String passwordHash) {

    public SystemUser {
        userId = userId == null ? "" : userId.trim();
        name = name == null ? "" : name.trim();
        department = department == null ? "" : department.trim();
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    /** 兼容未引入密码前的调用：无登录能力的用户（hash 为空）。 */
    public SystemUser(
            long id, String userId, String name, String department,
            Set<SystemRole> roles, List<SystemUserScope> scopes,
            Instant updatedAt, long version) {
        this(id, userId, name, department, roles, scopes, updatedAt, version, null);
    }
}
