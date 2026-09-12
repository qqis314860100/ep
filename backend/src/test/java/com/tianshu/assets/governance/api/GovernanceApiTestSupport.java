package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore;
import com.tianshu.assets.governance.infrastructure.InMemoryAssetResponsibilityAdapter;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceExecutionStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceWorkflowStore;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;

/**
 * 治理 API 测试共用脚手架：统一注入<b>真实</b>授权服务，并默认带上管理员身份头。
 *
 * <p><b>为什么需要它</b>：12 个治理 controller 测试此前各自
 * {@code standaloneSetup(new XController(service))}，走的是「不带授权服务」的短构造器——
 * 授权链路整条不在测试射程内，D-002（授权可被静默跳过）与 D-006（治理配置侧无授权调用）
 * 因此长期测不出来。那些短构造器已在本次修复中删除；这里给出一条统一的、带身份的正向路径。
 *
 * <p>负向用例（未登录 / 已登录但非管理员）不套默认头，显式覆盖，
 * 见 {@code GovernanceAuthorizationTest}。
 */
final class GovernanceApiTestSupport {

    /** 治理管理员：同时持内容管理员与系统管理员角色，可穿过所有治理闸门。 */
    static final String ADMIN_ID = "admin";
    static final String ADMIN_ROLES = "CONTENT_ADMIN,SYSTEM_ADMIN";

    /** 已登录的普通员工：能读治理数据，不能改治理配置。 */
    static final String STAFF_ID = "emp-chen";
    static final String STAFF_ROLES = "NORMAL_USER";

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLES_HEADER = "X-User-Roles";

    private GovernanceApiTestSupport() {}

    /** 每个测试自建实例，避免用例间串状态。 */
    static GovernanceAuthorizationService authorization() {
        return authorization(new InMemoryGovernanceExecutionStore(new InMemoryGovernanceWorkflowStore()));
    }

    /**
     * 复用调用方自己的执行存储。
     *
     * <p>必要性：{@code requireExecution} 会先 {@code requireItem(itemId)} 再判角色，
     * 空存储会直接 404 —— 所以执行/确认链的测试必须把 fixture 的存储交给授权服务，
     * 否则测的就不是业务规则而是「治理项不存在」。
     */
    static GovernanceAuthorizationService authorization(GovernanceExecutionStore executionStore) {
        return new GovernanceAuthorizationService(
                executionStore, new InMemoryAssetResponsibilityAdapter());
    }

    /**
     * 以管理员身份构建 MockMvc：所有请求默认带管理员头。
     *
     * <p>用 {@code defaultRequest} 而不是逐个请求加头，是为了让既有正向用例保持原样——
     * 它们要验的是接口出入参与业务规则，不是授权；授权由 {@code GovernanceAuthorizationTest} 专测。
     */
    static MockMvc mockMvcAsAdmin(StandaloneMockMvcBuilder builder) {
        return mockMvc(builder, ADMIN_ID, ADMIN_ROLES);
    }

    /** 构建 MockMvc 并按给定身份设定默认请求头；身份头传空串即模拟「未登录」。 */
    static MockMvc mockMvc(StandaloneMockMvcBuilder builder, String userId, String roles) {
        return builder
                .setControllerAdvice(new ApiExceptionHandler())
                .defaultRequest(get("/")
                        .header(USER_ID_HEADER, userId)
                        .header(USER_ROLES_HEADER, roles))
                .build();
    }

    /** 便捷入口：{@code adminFor(controller)} 等价于 {@code mockMvcAsAdmin(standaloneSetup(controller))}。 */
    static MockMvc adminFor(Object... controllers) {
        return mockMvcAsAdmin(standaloneSetup(controllers));
    }
}
