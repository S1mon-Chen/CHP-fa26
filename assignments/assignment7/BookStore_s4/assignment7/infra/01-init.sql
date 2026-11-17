USE bookstore_cache;
CREATE TABLE IF NOT EXISTS books (
  id BIGINT PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  author VARCHAR(100) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  stock INT NOT NULL
);
INSERT IGNORE INTO books VALUES
  (1, '深入理解计算机系统', 'Randal E. Bryant', 139.00, 100),
  (2, '设计数据密集型应用', 'Martin Kleppmann', 128.00, 80),
  (3, 'Redis 设计与实现', '黄健宏', 79.00, 60);
