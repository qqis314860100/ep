package com.tianshu.assets.system.application;

import com.tianshu.assets.system.domain.SystemUser;
import com.tianshu.assets.system.domain.SystemUserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 登录认证：按工号（userId）校验密码。会话由 AuthController 以 HttpSession 承载，
 * 本服务只负责身份与凭证校验（无状态）。
 */
@Service
public class AuthService {

    private final SystemUserRepository users;

    public AuthService(SystemUserRepository users) {
        this.users = users;
    }

    public Optional<SystemUser> authenticate(String userId, String rawPassword) {
        if (userId == null || userId.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return Optional.empty();
        }
        return users.findByUserId(userId.trim())
                .filter(user -> PasswordHasher.matches(rawPassword, user.passwordHash()));
    }

    public Optional<SystemUser> current(String userId) {
        return userId == null || userId.isBlank()
                ? Optional.empty()
                : users.findByUserId(userId);
    }
}
