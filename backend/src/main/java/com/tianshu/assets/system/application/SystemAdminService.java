package com.tianshu.assets.system.application;

import com.tianshu.assets.system.domain.OperationLog;
import com.tianshu.assets.system.domain.OperationLogCriteria;
import com.tianshu.assets.system.domain.OperationLogStore;
import com.tianshu.assets.system.domain.SystemRole;
import com.tianshu.assets.system.domain.SystemUser;
import com.tianshu.assets.system.domain.SystemUserRepository;
import com.tianshu.assets.system.domain.SystemUserScope;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SystemAdminService {

    private final SystemUserRepository users;
    private final OperationLogStore logs;
    private final Clock clock;

    @Autowired
    public SystemAdminService(SystemUserRepository users, OperationLogStore logs) {
        this(users, logs, Clock.systemUTC());
    }

    public SystemAdminService(SystemUserRepository users, OperationLogStore logs, Clock clock) {
        this.users = users;
        this.logs = logs;
        this.clock = clock;
    }

    public List<SystemUser> users() {
        return users.findAll();
    }

    public SystemUser updateRoles(long id, Set<SystemRole> roles, String operator, long expectedVersion) {
        var current = require(id);
        var now = now();
        var updated = users.update(new SystemUser(current.id(), current.userId(), current.name(), current.department(),
                roles, current.scopes(), now, current.version() + 1), expectedVersion);
        logs.append(new OperationLog(0, operator, "ROLE_UPDATE", "USER", id,
                "{\"roles\":[" + roles.stream().map(Enum::name).map(name -> "\"" + name + "\"")
                        .collect(Collectors.joining(",")) + "]}", now));
        return updated;
    }

    public SystemUser updateScopes(long id, List<SystemUserScope> scopes, String operator, long expectedVersion) {
        var current = require(id);
        var now = now();
        var updated = users.update(new SystemUser(current.id(), current.userId(), current.name(), current.department(),
                current.roles(), scopes, now, current.version() + 1), expectedVersion);
        logs.append(new OperationLog(0, operator, "SCOPE_UPDATE", "USER", id, "{}", now));
        return updated;
    }

    public List<OperationLog> logs(OperationLogCriteria criteria) {
        return logs.query(criteria);
    }

    public long logCount(OperationLogCriteria criteria) {
        return logs.count(criteria);
    }

    private SystemUser require(long id) {
        return users.findById(id).orElseThrow(() -> new SystemUserNotFoundException(id));
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }
}
