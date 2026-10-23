# 数据库用户凭证

## 管理员用户
- **用户名**: admin
- **密码**: password
- **邮箱**: admin@bookstore.com
- **角色**: ADMIN
- **状态**: ACTIVE

## 测试用户

### 用户1
- **用户名**: user1
- **密码**: password
- **邮箱**: user1@bookstore.com
- **角色**: CUSTOMER
- **状态**: ACTIVE

### 用户2
- **用户名**: user2
- **密码**: password
- **邮箱**: user2@bookstore.com
- **角色**: CUSTOMER
- **状态**: ACTIVE

## 密码说明
所有用户的密码在数据库中都是使用BCrypt加密存储的，加密后的密码为：
`$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW`

## 登录方式
用户可以通过前端的登录页面使用以上凭证进行登录，登录后可以进行购物车操作和下单。