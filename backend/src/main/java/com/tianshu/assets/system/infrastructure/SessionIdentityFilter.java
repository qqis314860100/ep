package com.tianshu.assets.system.infrastructure;

import com.tianshu.assets.system.api.AuthController;
import com.tianshu.assets.system.domain.SystemUser;
import com.tianshu.assets.system.domain.SystemUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.StringJoiner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 会话身份过滤器（D1/S1）：已登录请求以服务端会话身份为准——
 * 把 X-User-Id / X-User-Roles 覆写为会话用户，客户端自报头失效。
 * 未登录（无会话）请求：写方法（POST/PUT/PATCH/DELETE 等）一律 401 拒绝（S1 匿名写收紧），
 * 只读请求保持原样放行（demo/e2e 兼容的匿名读；数据范围过滤另见 S7）。
 * /api/v1/auth/** 不参与覆写与拦截（登录/登出/me 各自处理会话）。
 */
@Component
@Order(1)
@Profile({"dev", "local"})
public class SessionIdentityFilter extends OncePerRequestFilter {

    private static final String UNAUTHENTICATED_JSON =
            "{\"error\":{\"code\":\"auth_failed\",\"message\":\"请先登录后再操作\",\"details\":[]}}";

    private final SystemUserRepository users;

    public SessionIdentityFilter(SystemUserRepository users) {
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var session = request.getSession(false);
        var userId = session == null
                ? null
                : (String) session.getAttribute(AuthController.SESSION_USER_ID);
        if (userId == null) {
            if (isWriteRequest(request)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(UNAUTHENTICATED_JSON);
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        var user = users.findByUserId(userId).orElse(null);
        if (user == null) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new IdentityRequestWrapper(request, user), response);
    }

    /** 非安全方法（会改变服务端状态）视为写请求；GET/HEAD/OPTIONS/TRACE 放行。 */
    private boolean isWriteRequest(HttpServletRequest request) {
        var method = request.getMethod();
        return !("GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)
                || "TRACE".equalsIgnoreCase(method));
    }

    private static final class IdentityRequestWrapper extends HttpServletRequestWrapper {

        private static final String USER_ID_HEADER = "X-User-Id";
        private static final String USER_ROLES_HEADER = "X-User-Roles";

        private final String userId;
        private final String roles;

        IdentityRequestWrapper(HttpServletRequest request, SystemUser user) {
            super(request);
            this.userId = user.userId();
            var joiner = new StringJoiner(",");
            user.roles().forEach(role -> joiner.add(role.name()));
            this.roles = joiner.toString();
        }

        @Override
        public String getHeader(String name) {
            if (USER_ID_HEADER.equalsIgnoreCase(name)) return userId;
            if (USER_ROLES_HEADER.equalsIgnoreCase(name)) return roles;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (USER_ID_HEADER.equalsIgnoreCase(name) || USER_ROLES_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(getHeader(name)));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            var names = new ArrayList<String>();
            var seen = new java.util.HashSet<String>();
            var delegate = super.getHeaderNames();
            while (delegate != null && delegate.hasMoreElements()) {
                var name = delegate.nextElement();
                if (USER_ID_HEADER.equalsIgnoreCase(name) || USER_ROLES_HEADER.equalsIgnoreCase(name)) {
                    continue;
                }
                if (seen.add(name)) names.add(name);
            }
            if (seen.add(USER_ID_HEADER)) names.add(USER_ID_HEADER);
            if (seen.add(USER_ROLES_HEADER)) names.add(USER_ROLES_HEADER);
            return Collections.enumeration(names);
        }
    }
}
