package com.tianshu.assets.system.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.tianshu.assets.system.domain.SystemUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionIdentityFilterTest {

    private SessionIdentityFilter filter;
    private SystemUserRepository users;

    @BeforeEach
    void setUp() {
        users = new InMemorySystemUserRepository();
        filter = new SessionIdentityFilter(users);
    }

    @Test
    void overridesIdentityWithSessionUserAndIgnoresClientClaims() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/governance/tasks");
        request.addHeader("X-User-Id", "fake-user");
        request.addHeader("X-User-Roles", "SYSTEM_ADMIN");
        var session = request.getSession(true);
        session.setAttribute("auth.userId", "emp-li");
        var chain = new MockFilterChain();
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        var wrapped = (HttpServletRequest) chain.getRequest();
        assertThat(wrapped.getHeader("X-User-Id")).isEqualTo("emp-li");
        assertThat(wrapped.getHeader("X-User-Roles")).isEqualTo("CONTENT_ADMIN");
    }

    @Test
    void overridesRolesForSystemAdminSession() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/governance/tasks");
        request.addHeader("X-User-Roles", "");
        var session = request.getSession(true);
        session.setAttribute("auth.userId", "emp-admin");
        var chain = new MockFilterChain();

        filter.doFilter(request, response(), chain);

        var wrapped = (HttpServletRequest) chain.getRequest();
        assertThat(wrapped.getHeader("X-User-Id")).isEqualTo("emp-admin");
        assertThat(wrapped.getHeader("X-User-Roles"))
                .contains("SYSTEM_ADMIN").contains("CONTENT_ADMIN");
    }

    @Test
    void passesAnonymousRequestThroughUntouched() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/governance/tasks");
        request.addHeader("X-User-Id", "emp-wang");
        var chain = new MockFilterChain();

        filter.doFilter(request, response(), chain);

        var wrapped = (HttpServletRequest) chain.getRequest();
        assertThat(wrapped.getHeader("X-User-Id")).isEqualTo("emp-wang");
    }

    @Test
    void skipsAuthEndpoints() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        var chain = new MockFilterChain();

        filter.doFilter(request, response(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }
}
