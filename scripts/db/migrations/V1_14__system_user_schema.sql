-- V1_14 真实用户与权限表（本机 MySQL 用；供 JdbcSystemUserRepository 使用）
--
-- 背景：此前登录用户来自后端内存中的硬编码演示账号（emp-chen/emp-li/emp-wang/emp-admin，密码 demo123）。
-- 本迁移把用户、角色、数据范围与密码凭证落到真实表，作为唯一身份来源。
--
-- 约定：
--   * sys_user.code = 工号（与遗留 temp_person.code 同口径，例如 emp-chen），登录账号即它；
--   * 子表 user_id 指向 sys_user.id（不是工号）；
--   * 只做 additive：CREATE TABLE IF NOT EXISTS + INSERT ... WHERE NOT EXISTS，可重复执行。
--
-- 注意：下方引导管理员的初始密码仅为「首次进入系统」用，正式环境部署后必须立即改密
--      （改密 SQL 见 docs/local-development.md）。

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(100) NOT NULL COMMENT '工号/登录账号',
  name VARCHAR(100) NOT NULL,
  department VARCHAR(200) NOT NULL DEFAULT '',
  password_hash VARCHAR(200) NOT NULL DEFAULT '' COMMENT 'PBKDF2-HMAC-SHA256：base64(salt):iterations:base64(hash)',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用，0=停用',
  version BIGINT NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Assets system user (real database)';

CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role VARCHAR(40) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_role (user_id, role),
  KEY idx_sys_user_role_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Assets system user role';

CREATE TABLE IF NOT EXISTS sys_user_scope (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  base_name VARCHAR(200) NOT NULL DEFAULT '',
  product_line VARCHAR(100) NOT NULL DEFAULT '',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_scope (user_id, base_name, product_line)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Assets system user data scope';

-- 引导管理员：清库后系统内唯一的登录入口，保证仍有人能进系统导入真实业务数据。
INSERT INTO sys_user (code, name, department, password_hash)
SELECT 'admin', '系统管理员', '信息化部', 'mSTo9CeVE0AuNgeoGqZnPQ==:60000:UbVgR0N9shnJt4Hdf80ZzlmlUfJEUW8J7AZtEAk/7U4='
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE code = 'admin');

INSERT INTO sys_user_role (user_id, role)
SELECT u.id, 'SYSTEM_ADMIN' FROM sys_user u
WHERE u.code = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role r WHERE r.user_id = u.id AND r.role = 'SYSTEM_ADMIN');

INSERT INTO sys_user_role (user_id, role)
SELECT u.id, 'CONTENT_ADMIN' FROM sys_user u
WHERE u.code = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role r WHERE r.user_id = u.id AND r.role = 'CONTENT_ADMIN');
