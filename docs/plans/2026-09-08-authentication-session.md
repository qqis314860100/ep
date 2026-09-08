# 真实登录与会话身份（D1/S1 地基，2026-09-08 起草）

> 背景：正式部门多人共用系统；当前无登录，全部请求以 demo-user/自报 `X-User-Roles`
> 头进入，角色/身份不可信（gaps D1/S1）。R2"我的待办"按登录人隔离，依赖本切片。
> 决策（用户拍板）：**真登录：密码 + 会话全量**。

## 1. 范围边界

本切片只搭"人"的地基，不含 S7 数据范围下沉与资产/文档可见性过滤（另立票）。

### 后端
- 认证：`POST /api/v1/auth/login`（userCode + 密码）→ 服务端 HttpSession 会话；
  `POST /api/v1/auth/logout`；`GET /api/v1/auth/me`（当前用户/角色，前端启动即拉）。
- 密码：域内新增 `passwordHash`（BCrypt 或 SHA-256+盐，无外部 IdP）；种子用户带初始密码，文档注明仅供演示。
- 身份来源切换：登录态从 **HttpSession** 解析（`@SessionAttribute`/SessionScope 用户上下文），
  不再信任客户端 `X-User-Id` / `X-User-Roles` 自报头（S1 T1.1）。
- 写接口守卫：`SystemAdminController` 写操作要求 SYSTEM_ADMIN（T1.2）；治理动作保留现有角色判定但改用会话身份。
- 数据模型打通：治理员工目录（emp-*，OFFICE_DIRECTORY）与系统用户（u-*）以 userId 关联，
  登录身份能查到治理员工（避免两套人）。

### 前端
- 登录页：账号 + 密码；未登录进受保护路由 → 重定向登录页。
- 会话保持：请求带 credentials（同源 Cookie），401 统一跳登录。
- 移除 demo-user 硬编码：governance/api.ts `governanceIdentity`、各页面 'demo-user' 常量
  （S1 T1.3）；AppShell 右上角显示真实登录人（不再恒"陈工"）。
- R2 前置基础：登录后首页可按当前用户展示"归我的问题/今日到期/已逾期/待我验收"。

## 2. 测试策略
- 后端：认证 controller 单测（登录成功/密码错/登出/me 未登录 401）、写接口缺会话拒绝。
- 前端：登录页表单测试；受保护路由未登录重定向。
- 浏览器 e2e：登录 → 首页数据按人变化。

## 3. 拆票顺序
- D1a 后端认证（密码域 + login/logout/me + 会话上下文 + 守卫）→ commit
- D1b 身份来源切换 + SystemAdmin 强制角色 → commit
- D1c 前端登录页 + 会话保持 + 移除 demo-user → commit
- D1d R2 我的待办接通（登录人维度）→ commit
