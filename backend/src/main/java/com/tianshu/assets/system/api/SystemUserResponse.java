package com.tianshu.assets.system.api;

import com.tianshu.assets.system.domain.SystemRole;
import com.tianshu.assets.system.domain.SystemUser;
import com.tianshu.assets.system.domain.SystemUserScope;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record SystemUserResponse(
        long id,
        String userId,
        String name,
        String department,
        Set<SystemRole> roles,
        List<ScopeResponse> scopes,
        Instant updatedAt,
        long version) {

    public static SystemUserResponse from(SystemUser user) {
        return new SystemUserResponse(user.id(), user.userId(), user.name(), user.department(), user.roles(),
                user.scopes().stream().map(ScopeResponse::from).toList(), user.updatedAt(), user.version());
    }

    public record ScopeResponse(long id, String base, String productLine) {
        static ScopeResponse from(SystemUserScope scope) {
            return new ScopeResponse(scope.id(), scope.base(), scope.productLine());
        }
    }
}
