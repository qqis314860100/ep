-- V1.13 document collaboration (favorites/comments/likes). Additive only.
-- Execute through the controlled migration pipeline; do not run from application startup.

CREATE TABLE IF NOT EXISTS document_favorite (
  id BIGINT NOT NULL AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  user_id VARCHAR(100) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_document_favorite (document_id, user_id)
) COMMENT='知识文档收藏';

CREATE TABLE IF NOT EXISTS document_comment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  version_id BIGINT NOT NULL COMMENT '评论时的文档版本，保留版本上下文',
  author_id VARCHAR(100) NOT NULL,
  author_name VARCHAR(100) NOT NULL,
  content TEXT NOT NULL,
  image_keys_json JSON NULL,
  like_count BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_document_comment (document_id, id)
) COMMENT='知识文档评论（含版本上下文）';

CREATE TABLE IF NOT EXISTS document_comment_like (
  id BIGINT NOT NULL AUTO_INCREMENT,
  comment_id BIGINT NOT NULL,
  user_id VARCHAR(100) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_document_comment_like (comment_id, user_id)
) COMMENT='文档评论点赞';
