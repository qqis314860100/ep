package com.tianshu.assets;

import static org.assertj.core.api.Assertions.assertThat;

import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.infrastructure.OceanBaseAssetRepository;
import com.tianshu.assets.document.domain.DocumentRepository;
import com.tianshu.assets.document.infrastructure.JdbcDocumentRepository;
import com.tianshu.assets.system.domain.SystemUserRepository;
import com.tianshu.assets.system.infrastructure.JdbcSystemUserRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/**
 * 装配回归护栏：真实数据库是唯一数据源。
 *
 * <p>用 H2（MySQL 兼容模式）顶替本机 MySQL 只为了让上下文能起来，不执行任何业务 SQL；
 * 断言的是「容器里装配的数据实现全部是 JDBC，且没有任何 InMemory 实现混入」。
 * 一旦有人把内存 mock 重新装回 Spring 容器，这个测试立刻失败。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:assets-context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "DB_USERNAME=sa",
        "DB_PASSWORD=",
        "asset.file-storage-directory=${java.io.tmpdir}/assets-context-files",
})
class SimulationAssetApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void defaultsToTheRealDatabaseProfile() {
        assertThat(environment.getProperty("spring.profiles.default")).isEqualTo("local");
    }

    @Test
    void dataPortsResolveToJdbcImplementations() {
        assertThat(context.getBean(AssetRepository.class)).isInstanceOf(OceanBaseAssetRepository.class);
        assertThat(context.getBean(DocumentRepository.class)).isInstanceOf(JdbcDocumentRepository.class);
        assertThat(context.getBean(SystemUserRepository.class)).isInstanceOf(JdbcSystemUserRepository.class);
    }

    @Test
    void noInMemoryDataStoreIsWiredIntoTheContainer() {
        List<String> inMemoryBeans = new ArrayList<>();
        for (var name : context.getBeanDefinitionNames()) {
            Class<?> type;
            try {
                type = context.getType(name);
            } catch (RuntimeException unresolved) {
                continue;
            }
            if (type != null && type.getName().contains("InMemory")) inMemoryBeans.add(type.getName());
        }
        assertThat(inMemoryBeans).isEmpty();
    }
}
