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
        long version) {

    public SystemUser {
        userId = userId == null ? "" : userId.trim();
        name = name == null ? "" : name.trim();
        department = department == null ? "" : department.trim();
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
