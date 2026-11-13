-- Dedicated local assignment databases; no existing BookStore schema is modified.
CREATE DATABASE inventory_db CHARACTER SET utf8mb4;
CREATE DATABASE order_db CHARACTER SET utf8mb4;
CREATE USER 'inventory_app'@'%' IDENTIFIED BY 'assignment6-local';
CREATE USER 'order_app'@'%' IDENTIFIED BY 'assignment6-local';
GRANT ALL PRIVILEGES ON inventory_db.* TO 'inventory_app'@'%';
GRANT ALL PRIVILEGES ON order_db.* TO 'order_app'@'%';
USE inventory_db;
CREATE TABLE inventory (
  book_id BIGINT PRIMARY KEY,
  title VARCHAR(128) NOT NULL,
  stock INT NOT NULL,
  CONSTRAINT nonnegative_stock CHECK (stock >= 0)
) ENGINE=InnoDB;
INSERT INTO inventory VALUES (1,'深入理解计算机系统',100),(2,'设计数据密集型应用',50),(3,'并发测试专用图书',1);
CREATE TABLE undo_log (
  branch_id BIGINT NOT NULL,
  xid VARCHAR(128) NOT NULL,
  context VARCHAR(128) NOT NULL,
  rollback_info LONGBLOB NOT NULL,
  log_status INT NOT NULL,
  log_created DATETIME(6) NOT NULL,
  log_modified DATETIME(6) NOT NULL,
  UNIQUE KEY ux_undo_log (xid, branch_id),
  KEY ix_log_created (log_created)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
USE order_db;
CREATE TABLE purchase_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  book_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  xid VARCHAR(128) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_request_id(request_id),
  CONSTRAINT positive_quantity CHECK (quantity > 0)
) ENGINE=InnoDB;
CREATE TABLE undo_log (
  branch_id BIGINT NOT NULL,
  xid VARCHAR(128) NOT NULL,
  context VARCHAR(128) NOT NULL,
  rollback_info LONGBLOB NOT NULL,
  log_status INT NOT NULL,
  log_created DATETIME(6) NOT NULL,
  log_modified DATETIME(6) NOT NULL,
  UNIQUE KEY ux_undo_log (xid, branch_id),
  KEY ix_log_created (log_created)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
