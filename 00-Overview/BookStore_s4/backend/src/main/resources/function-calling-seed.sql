-- 作业2：为 Function Calling 演示补充 ISBN 数据
-- 用法：mysql -uroot -p bookstore_s4 < function-calling-seed.sql

-- init.sql 建的 books 表没有 isbn 列（后端首次启动时 JPA 会自动加），这里提前补上
SET @has_isbn = (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'books' AND column_name = 'isbn');
SET @ddl = IF(@has_isbn = 0, 'ALTER TABLE books ADD COLUMN isbn VARCHAR(20)', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 演示用短编号：作业示例中的 ISBN 12345
UPDATE books SET isbn = '12345'         WHERE title = '深入理解计算机系统';
-- 真实 ISBN-13
UPDATE books SET isbn = '9787115545381' WHERE title = 'JavaScript高级程序设计';
UPDATE books SET isbn = '9787111075752' WHERE title = '设计模式';
-- 以 999 开头：首次调用比价接口时模拟超时，用于演示重试
UPDATE books SET isbn = '99901'         WHERE title = '算法导论';
-- 库存紧张的场景
UPDATE books SET stock = 3              WHERE title = '设计模式';

SELECT id, title, isbn, price, stock FROM books WHERE isbn IS NOT NULL;
