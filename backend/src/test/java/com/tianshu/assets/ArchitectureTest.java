package com.tianshu.assets;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * 分层依赖方向的机器强制（AGENTS.md「低耦合高内聚」的可判定化）。
 *
 * <p>四条规则在引入时对全仓库 275 个生产类实测**零违规**，因此不做冻结：一旦出现新违规
 * 立即失败，不需要任何存量重构。方向是：domain 在最内，只被依赖；application 与 api
 * 只经 domain 端口访问数据，不认识 infrastructure 实现。
 *
 * <p>模块之间的依赖（谁可以引用谁的 application/api）尚未纳入：实测 48 处跨模块非
 * domain 依赖，其中 22 处来自 {@code common/api/ApiExceptionHandler} —— 它是全局异常到
 * 错误码的翻译器，引用各模块的异常正是其职责。定这条规则需要先决定哪些跨模块入口是
 * 有意为之，属于设计决策，留待单独讨论。
 */
class ArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.tianshu.assets");

    @Test
    void domainDoesNotDependOnOuterLayers() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..api..", "..application..", "..infrastructure..")
                .because("domain 是核心，不能反向依赖任何外层")
                .check(PRODUCTION);
    }

    @Test
    void domainDoesNotDependOnFrameworkOrJdbc() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "java.sql..", "javax.sql..")
                .because("domain 是纯领域模型，不绑定框架与数据访问技术")
                .check(PRODUCTION);
    }

    @Test
    void applicationDoesNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("应用层只依赖 domain 端口，实现由 infrastructure 装配注入")
                .check(PRODUCTION);
    }

    @Test
    void apiDoesNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("接口层经应用层访问领域，不直接触碰适配器实现")
                .check(PRODUCTION);
    }
}
