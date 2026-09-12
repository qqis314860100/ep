---
name: java-backend-conventions
description: 本仓库后端（Java 21 / Spring Boot / Spring JDBC / OceanBase MySQL 兼容模式）的编码约定与常见失败模式。Use when 改动 backend/ 下的 Java 代码：新增或修改 controller、application service、domain 实体与仓储接口、infrastructure 适配器、事务、异常与错误码、SQL 映射、参数校验时。
metadata:
  author: tianshu-assets
  version: "1.0.0"
---

# 后端编码约定（本仓库）

适用 `backend/src/main/java/com/tianshu/assets/`。测试怎么写看 `docs/testing-strategy.md`，
验证跑什么看根 `AGENTS.md` 的 Verification。本技能只讲 `src/main` 的代码形态。

**总原则**：先读相邻实现，按你看到的写。下面是本仓库已确立的形态；若代码与本文冲突，
以代码为准，并把冲突报告给人类——不要默默选一边。

## 分层：每个东西住哪

| 层 | 放什么 | 先例 |
| --- | --- | --- |
| `<module>/api/` | Controller + 请求/响应 DTO + `PageResponse` | `asset/api/AssetResponse.java` |
| `<module>/application/` | 应用服务（用例编排）+ 应用层异常 | `asset/application/AssetQueryService.java`、`asset/application/AssetNotFoundException.java` |
| `<module>/domain/` | 实体、值对象、仓储**接口**、查询条件 | `asset/domain/Asset.java`、`asset/domain/AssetRepository.java`、`asset/domain/AssetScope.java` |
| `<module>/infrastructure/` | 仓储/存储的**实现**、SQL、外部适配器、只读适配器 | `asset/infrastructure/OceanBaseAssetRepository.java`、`asset/infrastructure/ReadOnlyAssetCollaborationStore.java` |

- **端口与适配器**：`domain/XxxRepository` 定义接口，`infrastructure/OceanBaseXxxRepository` 实现。
  应用服务只依赖 domain 接口，不认识 `OceanBase*`。命名沿用 `OceanBase*` / `Jdbc*` 前缀。
- **SQL 只能在 `infrastructure/`**。应用层、领域层出现 `SELECT/INSERT/UPDATE/DELETE` 即为违规。
- 应用层异常与用例同住在 `application/`，不要下沉到 domain，也不要上浮到 api。
- 不要为了"补齐层"而建空包；层按需存在（例如 `search/` 只有 `api` + `application`）。

## 数据访问：Spring JDBC，不是 JPA

- 用 `JdbcTemplate` / `JdbcClient` 直接写 SQL。**行映射写行内 lambda**，本仓库没有
  `RowMapper` 实现类，保持一致。
- **禁止 `BeanPropertyRowMapper`**：它对不上的列会静默忽略，字段改名后不报错。本仓库当前
  零使用，别引入。
- 多参数查询目前用 `JdbcClient.params(Map)` 的命名参数。**值一律走参数绑定**，
  只有静态 SQL 片段可以字符串拼接（参见 `interconnect/infrastructure/JdbcEquipmentInterconnectionRepository`
  的 `String.join(" AND ", where)`——拼的是片段，不是值）。
- 多表 JOIN 必须写列别名；`SELECT *` 禁止。

## 数据源：真实库是唯一来源，缺了就失败，不许静默降级

这是本仓库的明文设计意图（见 `governance/infrastructure/GovernanceStoreConfiguration`
的类注释）：**库结构或依赖未就绪时应启动失败，不得静默退化成内存数据。**

- `src/main` **禁止**内存仓储、种子数据、演示身份、硬编码兜底列表。
- **不要用 `ObjectProvider.getIfAvailable()` + 本地假数据兜底**。这个组合会让接口在
  依赖缺失时返回 HTTP 200 和**编造的数据**——正确做法是让装配失败。
- 需要可选行为时用 `@ConditionalOnMissingBean` / `@Profile` 显式装配真实现，
  而不是在业务类里 `if (dep != null) {...} else {返回假的}`。
- 内存实现只允许放 `src/test` 作测试替身。

## 事务

- `@Transactional` 放在 **application 服务的用例入口**，不要放在 Controller（事务会过长），
  也不要只放在单个仓储方法上（多步业务会失去原子性）。
- 本仓库所有自定义异常都是 `RuntimeException` 子类，默认回滚已生效，因此没有 `rollbackFor`。
  **若你新增的是 checked exception，必须显式声明 `rollbackFor`**，否则失败不会回滚。
- 不要吞异常后再提交；`REQUIRES_NEW` 不要用在热路径。

## 异常与错误码

- 业务失败 → 在 `application/` 新建具体异常类（如 `DocumentStateConflictException`），
  携带面向用户的消息。
- 在 `common/api/ApiExceptionHandler` 里为该异常加一个 `@ExceptionHandler`，**映射到
  `ErrorCode` 枚举里的常量**。
- **绝不允许把错误码写成字符串字面量**。错误码只在
  `common/api/ErrorCode.java` 声明；唯一性由 `ErrorCodeTest` 断言，重复会让 `mvn test` 红。
- 多个异常的面向客户端含义相同时，**共用一个 `ErrorCode` 常量**（如
  `GOVERNANCE_STATE_CONFLICT` 服务两个异常），不要为对称性造新码。
- 错误响应体只有一种形状：`ApiError`。

## 参数校验：三层各管一段

1. **格式**（非空、长度、格式、范围）→ DTO 上的 Bean Validation + Controller `@Valid`，
   落到 `MethodArgumentNotValidException`。`@PathVariable`/`@RequestParam` 校验用 `@Validated`。
2. **业务规则**（唯一性、状态流转、鉴权）→ 应用服务内判断，抛自定义异常。
3. **数据库约束** → 唯一索引/外键/非空作为并发安全的最后防线；捕获冲突转成业务异常。

不要把业务规则塞进自定义 `ConstraintValidator` 里再注入仓储——耦合且难以复用。

## 领域

- 实体不要注入 Spring Bean（不是容器管理的）；需要协作者就从方法参数传入或走 Domain Service。
- `AssetScope` 的成品过滤与产线过滤**必须在同一个 scope 内匹配**，不要跨 scope 取并集。
- 资产生命周期是 `草稿 -> 待整理 -> 已标准化 -> 已停用`，状态流转写在 domain/application，
  不要在 Controller 里判断。
- 不得修改 legacy 主键，不得覆盖 legacy 源值。

## 命名（Google Java Style 的实用子集）

类型 `UpperCamelCase`、方法与字段 `lowerCamelCase`、常量 `UPPER_SNAKE_CASE`、包全小写、
布尔用 `is*` / `has*` / `can*`、缩写按驼峰（`XmlUtil` 而非 `XMLUtil`）。
**不要加 `m_` 或下划线前缀。** 与相邻文件保持一致优先于本清单。

## 通用 Java 硬规则（来源：《阿里巴巴 Java 开发手册》）

只列与本仓库相关且当前无机器保障的几条：

- **禁止用 `Executors` 创建线程池**（无界队列会 OOM）；需要时用 `ThreadPoolExecutor` 并指定有界队列。
- `ThreadLocal` 用完必须 `remove()`（`try/finally`），否则线程池下内存泄漏。
- 日志用 SLF4J 占位符，不要字符串拼接；异常日志把 `e` 作为最后一个参数以带出堆栈。
- 集合遍历中删除用 `Iterator.remove()` 或 `removeIf`，不要 `for-each` 里 `remove`。
- 覆写方法加 `@Override`；不要空 `catch` 块；不要在 `finally` 里 `return`。
