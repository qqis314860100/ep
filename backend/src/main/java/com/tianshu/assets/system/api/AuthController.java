package com.tianshu.assets.system.api;

import com.tianshu.assets.system.application.AuthException;
import com.tianshu.assets.system.application.AuthService;
import com.tianshu.assets.system.domain.SystemRole;
import com.tianshu.assets.system.domain.SystemUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 登录/会话端点：真实密码 + 服务端 HttpSession（D1）。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String SESSION_USER_ID = "auth.userId";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public SessionUserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        var user = authService.authenticate(request.userId(), request.password())
                .orElseThrow(() -> new AuthException("账号或密码错误"));
        servletRequest.getSession(true).setAttribute(SESSION_USER_ID, user.userId());
        return SessionUserResponse.from(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) {
        if (session != null) session.invalidate();
    }

    @GetMapping("/me")
    public SessionUserResponse me(HttpSession session) {
        var userId = session == null ? null : (String) session.getAttribute(SESSION_USER_ID);
        if (userId == null) throw new AuthException("未登录或会话已过期");
        return authService.current(userId)
                .map(SessionUserResponse::from)
                .orElseThrow(() -> new AuthException("会话用户不存在"));
    }

    public record LoginRequest(@NotBlank String userId, @NotBlank String password) {}

    /** 会话内当前用户的公开信息（不含密码哈希）。 */
    public record SessionUserResponse(
            String userId, String name, String department, List<String> roles) {
        static SessionUserResponse from(SystemUser user) {
            Set<String> roleNames = new TreeSet<>();
            for (SystemRole role : user.roles()) roleNames.add(role.name());
            return new SessionUserResponse(user.userId(), user.name(), user.department(),
                    List.copyOf(roleNames));
        }
    }
}
