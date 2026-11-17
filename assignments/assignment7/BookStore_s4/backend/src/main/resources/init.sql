-- 智能在线书店数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS bookstore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE bookstore;

-- 清除已存在的表（如果存在）
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS books;
DROP TABLE IF EXISTS users;

-- 用户表
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    role ENUM('CUSTOMER', 'ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    status ENUM('BANNED', 'ACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 书籍表
CREATE TABLE books (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(100) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    cover_image VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 订单表
CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status ENUM('PENDING', 'PAID', 'DELIVERED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 订单项表
CREATE TABLE order_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_books_title ON books(title);
CREATE INDEX idx_books_author ON books(author);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_book_id ON order_items(book_id);

-- 插入初始数据

-- 插入管理员用户
INSERT INTO users (username, password, email, role, status) VALUES
('admin', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'admin@bookstore.com', 'ADMIN', 'ACTIVE');

-- 插入测试用户
INSERT INTO users (username, password, email, role, status) VALUES
('user1', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'user1@bookstore.com', 'CUSTOMER', 'ACTIVE'),
('user2', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'user2@bookstore.com', 'CUSTOMER', 'ACTIVE');

-- 插入测试书籍
INSERT INTO books (title, author, description, price, stock, cover_image) VALUES
('深入理解计算机系统', 'Randal E. Bryant', '本书从程序员的视角详细阐述了计算机系统的本质', 99.00, 100, 'book1.jpg'),
('JavaScript高级程序设计', 'Nicholas C. Zakas', 'JavaScript权威指南', 88.00, 150, 'book2.jpg'),
('算法导论', 'Thomas H. Cormen', '算法领域的经典教材', 77.00, 80, 'book3.jpg'),
('设计模式', 'Erich Gamma', '设计模式的权威指南', 66.00, 120, 'book4.jpg'),
('Java核心技术', 'Cay S. Horstmann', 'Java编程的经典教程', 95.00, 90, 'book5.jpg');

-- 插入测试订单
INSERT INTO orders (user_id, total_amount, status) VALUES
(2, 187.00, 'PAID'),
(3, 143.00, 'PENDING');

-- 插入测试订单项
INSERT INTO order_items (order_id, book_id, quantity, price) VALUES
(1, 1, 1, 99.00),
(1, 2, 1, 88.00),
(2, 3, 1, 77.00),
(2, 4, 1, 66.00);

-- 查看创建的表结构
SHOW TABLES;

-- 查看各表数据
SELECT * FROM users;
SELECT * FROM books;
SELECT * FROM orders;
SELECT * FROM order_items;