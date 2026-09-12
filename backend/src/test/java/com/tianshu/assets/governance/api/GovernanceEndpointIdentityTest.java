package com.tianshu.assets.governance.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 结构性闸门：治理域每个 HTTP 处理函数都必须读取 {@code X-User-Id} 身份头。
 *
 * <p><b>为什么需要它</b>：D-006 的成因是「新端点忘了装授权」——14 个 controller 里有 10 个
 * 从未接入授权服务，而没有任何测试会因此变红。<b>自动扫描包内 controller</b>
 * （而不是硬编码清单）是关键：新增一个漏读身份头的治理端点，这个测试立刻红。
 *
 * <p>它只保证「身份可得」，不保证「闸门被调用」——后者由
 * {@link GovernanceAuthorizationServiceTest} 与各 controller 的行为用例守。
 * 两者合起来才覆盖「忘了装」与「装了没用」两种失效形态。
 */
class GovernanceEndpointIdentityTest {

    private static final String GOVERNANCE_API_PACKAGE = "com.tianshu.assets.governance.api";
    private static final String USER_ID_HEADER = "X-User-Id";

    private static final List<Class<? extends Annotation>> MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class,
            PatchMapping.class, DeleteMapping.class, RequestMapping.class);

    @Test
    void everyGovernanceHandlerReadsTheIdentityHeader() throws Exception {
        var offenders = new ArrayList<String>();
        for (var type : governanceControllers()) {
            for (var method : type.getDeclaredMethods()) {
                if (isHandler(method) && !readsIdentityHeader(method)) {
                    offenders.add(type.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(offenders)
                .as("治理端点必须读取 X-User-Id：授权闸门 fail-closed，读不到身份会把所有人挡在门外；"
                        + "而端点若同时漏装闸门，则变成静默放行（D-006）")
                .isEmpty();
    }

    @Test
    void scanActuallyFindsTheGovernanceControllers() throws Exception {
        // 防止「扫描器配错 → 一个类都没扫到 → 上面那条断言空集恒绿」
        assertThat(governanceControllers())
                .as("包扫描必须真的找到治理 controller，否则上面的结构性断言是空转")
                .hasSizeGreaterThanOrEqualTo(14);
    }

    private static List<Class<?>> governanceControllers() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        var controllers = new ArrayList<Class<?>>();
        for (var candidate : scanner.findCandidateComponents(GOVERNANCE_API_PACKAGE)) {
            controllers.add(Class.forName(candidate.getBeanClassName()));
        }
        return controllers;
    }

    private static boolean isHandler(Method method) {
        return MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotationPresent);
    }

    private static boolean readsIdentityHeader(Method method) {
        return Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .anyMatch(header -> header != null && USER_ID_HEADER.equals(header.name()));
    }
}
