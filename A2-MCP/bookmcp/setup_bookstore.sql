-- ==============================================
-- 书籍查询系统 - 数据库初始化脚本
-- ==============================================
-- 执行方式: mysql -u root -p < setup_bookstore.sql
-- ==============================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS bookstore DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE bookstore;

-- 创建书籍表
DROP TABLE IF EXISTS book;
CREATE TABLE book (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '书籍ID',
    name VARCHAR(200) NOT NULL COMMENT '书名',
    author VARCHAR(100) NOT NULL COMMENT '作者',
    price DECIMAL(10, 2) NOT NULL COMMENT '价格',
    type VARCHAR(50) NOT NULL COMMENT '类型',
    inventory INT NOT NULL DEFAULT 0 COMMENT '库存数量',
    description TEXT COMMENT '简介',
    image_url VARCHAR(500) COMMENT '封面图片URL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_name (name),
    INDEX idx_author (author),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书籍表';

-- 插入示例数据
INSERT INTO book (name, author, price, type, inventory, description, image_url) VALUES
(
    'Java核心技术卷II',
    '凯S.霍斯特曼',
    95.20,
    '编程',
    1000,
    '本书是Java领域有影响力和价值的著作之一，由拥有20多年教学与研究经验的Java技术专家撰写（获Jolt大奖），与《Java编程思想》齐名，10余年全球畅销不衰，广受好评。第10版根据JavaSE8全面更新，同时修正了第9版中的不足，系统全面讲解了Java语言的核心概念、语法、重要特性和开发方法，包含大量案例，实践性强。',
    'http://img3m9.ddimg.cn/12/36/1546133799-1_w_1.jpg'
),
(
    '深入理解计算机系统',
    '兰德尔·E·布莱恩特',
    136.90,
    '编程',
    1200,
    '程序员必读经典著作！理解计算机系统*书目，10万程序员共同选择。第二版销售突破100000册，第三版重磅上市！',
    'http://img3m7.ddimg.cn/48/0/24106647-1_w_6.jpg'
),
(
    'Effective C++',
    '梅耶',
    51.30,
    '编程',
    1000,
    '大师名著纵横二十载，稳居任一荐书单三甲；称职程序员傍身绝学，通向C++精微奥妙之门。',
    'http://img3m6.ddimg.cn/96/25/21000966-1_u_12.jpg'
),
(
    '小王子',
    '圣-埃克苏佩里',
    8.89,
    '儿童文学',
    1000,
    '豆瓣9.7高分推荐！旅法翻译家梅子涵之女梅思繁法文直译，舒朗大开本，央美教授高精度还原原作插画。首次收录全球舞台剧、音乐会、电影、动画片等对《小王子》的精彩诠释，通晓名作的前世今生。',
    'http://img3m9.ddimg.cn/75/6/25067469-1_u_2.jpg'
),
(
    'Java编程思想',
    'Bruce Eckel',
    91.20,
    '编程',
    9096,
    'Java学习必读经典,殿堂级著作！赢得了全球程序员的广泛赞誉。',
    'http://img3m0.ddimg.cn/4/24/9317290-1_w_5.jpg'
),
(
    '魔兽世界编年史套装(全三卷)',
    '克里斯˙梅森',
    449.20,
    '魔幻小说',
    123,
    '暴雪官方历时二十年编纂而成的史料！三卷《魔兽世界编年史》将呈现大量从未公布的精美原画和插图，读者在阅读故事之余，更能享受一次视觉上的饕餮盛宴，是魔兽粉丝收藏的优选。',
    'http://img3m7.ddimg.cn/43/9/25352557-1_w_3.jpg'
),
(
    '三体：全三册',
    '刘慈欣',
    50.20,
    '科幻小说',
    14414,
    '刘慈欣代表作，亚洲首部"雨果奖"获奖作品！',
    'http://img3m4.ddimg.cn/32/35/23579654-1_u_3.jpg'
),
(
    '悲惨世界（上中下）（精装版）',
    '雨果',
    104.00,
    '世界名著',
    388,
    '《悲惨世界》是雨果在流亡期间写的长篇小说，是他的代表作，也是世界文学宝库的珍品之一。通过冉阿让等人的悲惨遭遇以及冉阿让被卞福汝主教感化后一系列令人感动的事迹，深刻揭露和批判了19世纪法国封建专制社会的腐朽本质及其罪恶现象，对穷苦人民在封建重压下所遭受的剥削欺诈和残酷迫害表示了悲悯和同情。',
    'http://img3m7.ddimg.cn/13/15/27912667-1_u_1.jpg'
),
(
    '动物农场',
    '乔治·奥威尔',
    20.40,
    '社会小说',
    123,
    '也译"动物庄园"，是"一代人的冷峻良知"乔治·奥威尔经典的讽喻之作。虽然这一场荒诞的动物革命走向歧途，但正是因为这样我们才了解"把权力关进制度的笼子"的重要性。',
    'http://img3m1.ddimg.cn/82/3/25229341-1_w_2.jpg'
),
(
    '机器学习',
    '周志华',
    61.60,
    '编程',
    2525,
    '击败AlphaGo的武林秘籍，赢得人机大战的必由之路：人工智能大牛周志华教授巨著，全面揭开机器学习的奥秘。',
    'http://img3m0.ddimg.cn/20/24/23898620-1_w_3.jpg'
);

-- 显示插入结果
SELECT COUNT(*) AS '插入书籍数量' FROM book;

-- 查询所有书籍
SELECT id, name, author, price, type, inventory FROM book;

-- ==============================================
-- 脚本执行完成
-- ==============================================