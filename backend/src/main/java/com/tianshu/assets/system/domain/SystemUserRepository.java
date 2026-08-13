package com.tianshu.assets.system.domain;

import java.util.List;
import java.util.Optional;

public interface SystemUserRepository {

    List<SystemUser> findAll();

    Optional<SystemUser> findById(long id);

    SystemUser update(SystemUser user, long expectedVersion);
}
