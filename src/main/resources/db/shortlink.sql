-- ============================================================
-- 分布式短链服务建表脚本
-- mysql -uroot -p123456 -h127.0.0.1 < shortlink.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS shortlink DEFAULT CHARACTER SET utf8mb4;
USE shortlink;

CREATE TABLE IF NOT EXISTS t_short_link (
  id BIGINT UNSIGNED NOT NULL COMMENT '号段生成的主键',
  group_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
  origin_url VARCHAR(2048) NOT NULL,
  short_uri VARCHAR(16) NOT NULL,
  full_short_url VARCHAR(64) NOT NULL,
  create_type TINYINT NOT NULL DEFAULT 0 COMMENT '0自动 1自定义',
  valid_date_type TINYINT NOT NULL DEFAULT 0 COMMENT '0永久 1自定义',
  valid_date DATETIME NULL,
  state TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  del_flag TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_short_uri (short_uri),
  UNIQUE KEY uk_full_short_url (full_short_url),
  KEY idx_group (group_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '短链';

CREATE TABLE IF NOT EXISTS t_link_access_stats (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  full_short_url VARCHAR(64) NOT NULL,
  stat_date DATE NOT NULL,
  pv INT NOT NULL DEFAULT 0,
  uv INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_url_date (full_short_url, stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '访问统计';

CREATE TABLE IF NOT EXISTS t_id_segment (
  biz_tag VARCHAR(64) NOT NULL,
  max_id BIGINT NOT NULL DEFAULT 0,
  step INT NOT NULL DEFAULT 1000,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (biz_tag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '号段表';

INSERT INTO t_id_segment (biz_tag, max_id, step)
VALUES ('short_link', 0, 1000)
ON DUPLICATE KEY UPDATE step = VALUES(step);
