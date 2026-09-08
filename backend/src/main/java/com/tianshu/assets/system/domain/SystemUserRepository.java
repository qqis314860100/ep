package com.tianshu.assets.system.domain;

import java.util.List;
import java.util.Optional;

public interface SystemUserRepository {

    List<SystemUser> findAll();

    Optional<SystemUser> findById(long id);

    Optional<SystemUser> findByUserId(String userId);

    SystemUser update(SystemUser user, long expectedVersion);
}
