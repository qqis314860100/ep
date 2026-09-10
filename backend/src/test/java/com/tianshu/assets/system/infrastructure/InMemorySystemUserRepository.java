package com.tianshu.assets.system.infrastructure;

import com.tianshu.assets.system.application.PasswordHasher;
import com.tianshu.assets.system.application.SystemUserConflictException;
import com.tianshu.assets.system.domain.SystemRole;
import com.tianshu.assets.system.domain.SystemUser;
import com.tianshu.assets.system.domain.SystemUserRepository;
import com.tianshu.assets.system.domain.SystemUserScope;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class InMemorySystemUserRepository implements SystemUserRepository {

    private final List<SystemUser> users;

    public InMemorySystemUserRepository() {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        // 演示账号与治理员工目录共用工号（emp-*），初始密码统一 demo123。
        var demoPassword = PasswordHasher.hash("demo123");
        users = new ArrayList<>(List.of(
                new SystemUser(1, "emp-chen", "陈工", "设备工程部",
                        Set.of(SystemRole.UPLOADER),
                        List.of(new SystemUserScope(1, "宁德基地", "H03")), now, 1, demoPassword),
                new SystemUser(2, "emp-li", "李工", "标准化小组",
                        Set.of(SystemRole.CONTENT_ADMIN), List.of(), now, 1, demoPassword),
                new SystemUser(3, "emp-wang", "王工", "资料管理组",
                        Set.of(SystemRole.DOCUMENT_MAINTAINER), List.of(), now, 1, demoPassword),
                new SystemUser(4, "emp-admin", "管理员", "信息化部",
                        Set.of(SystemRole.SYSTEM_ADMIN, SystemRole.CONTENT_ADMIN), List.of(), now, 1, demoPassword)));
    }

    @Override
    public synchronized List<SystemUser> findAll() {
        return List.copyOf(users);
    }

    @Override
    public synchronized Optional<SystemUser> findById(long id) {
        return users.stream().filter(user -> user.id() == id).findFirst();
    }

    @Override
    public synchronized Optional<SystemUser> findByUserId(String userId) {
        return users.stream().filter(user -> user.userId().equals(userId)).findFirst();
    }

    @Override
    public synchronized SystemUser update(SystemUser user, long expectedVersion) {
        for (var index = 0; index < users.size(); index++) {
            var current = users.get(index);
            if (current.id() == user.id()) {
                if (current.version() != expectedVersion) {
                    throw new SystemUserConflictException("用户权限已被其他管理员修改，请刷新后重试");
                }
                users.set(index, user);
                return user;
            }
        }
        throw new SystemUserConflictException("用户不存在或不可访问");
    }
}
