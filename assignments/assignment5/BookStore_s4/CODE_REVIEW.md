# 书店代码审查

## 修复状态（2026-10-21）

原报告的 15 项问题均已修复。以下表格为当前状态，后面的审查正文保留修复前的问题与证据，其行号对应修复前版本。

| 原报告项目 | 修复内容 |
| --- | --- |
| 核心 1、2 | 订单接口要求认证，全量订单与统计仅限管理员；服务层复核归属；下单用户来自服务端会话。 |
| 核心 3 | 保存 Spring Security 会话，登录时轮换会话 ID；前端恢复会话、携带凭据，退出时使服务端会话失效；启用 CSRF 及受限 CORS。 |
| 核心 4 | DTO 和服务层均拒绝零、负数、空订单项和无效 ID；合并重复商品并检查数量溢出。 |
| 核心 5、6 | 事务覆盖整个订单；按书籍 ID 顺序取得数据库悲观锁；取消时同时锁订单，库存仅恢复一次；管理员取消走相同库存逻辑。 |
| 核心 7 | 销量和消费统计仅计入 PAID、DELIVERED。 |
| 前端 1 | 正确解构 message.useMessage 并渲染 contextHolder。 |
| 前端 2、3 | 购物车提交加入同步防重入和按钮 loading，失败提示并保留购物车供重试。 |
| 前端 4 | 图表 effect 返回完整清理函数，解除 resize 监听并释放实例。 |
| Spec 1 | 推荐不再调用下单；购买与取消只接受明确指令，例如“下单 书籍编号 123”“取消订单 123”；否定式或询问式语句不会执行。 |
| Spec 2、3 | 用户响应使用安全 DTO，实体密码禁止序列化，删除密码哈希日志；AI、技能、规则与 context/create 的身份和角色从当前会话生成。 |
| Spec 4 | 使用 Jackson 解析 Chroma 嵌套结果及集合信息，正确编码带标点/引号的请求。 |

另修复了成功弹窗随机生成订单号的问题，改为显示后端返回的真实订单号和待支付状态；用户切换时清理订单页面和 AI 会话内容。前端 Context hooks 分离后，ESLint 检查通过。

验证结果：后端 **23 个测试通过**，包括真实 Spring Security/MockMvc、H2 数据库事务和并发库存验证；前端 **10 个测试通过**，包括真实组件反馈、重复提交、图表清理、会话和 CSRF 请求；`npm run lint` 与 `npm run build` 均通过。原有密码示例已改为带断言的测试。

测试文件：[BookstoreRegressionTest.java](/Users/xlchen/Documents/BookStore_s4/backend/src/test/java/com/bookstore/BookstoreRegressionTest.java)、[RagResponseTest.java](/Users/xlchen/Documents/BookStore_s4/backend/src/test/java/com/bookstore/RagResponseTest.java)、[前端测试目录](/Users/xlchen/Documents/BookStore_s4/frontend/src/test)。

验证边界：没有连接或修改现有 MySQL 数据；数据库回归使用隔离 H2。Chroma 进行了响应解析测试，未做外部服务联调；前端组件测试使用 jsdom，未运行真实浏览器端到端测试。生产构建仍有依赖包体积较大的非阻塞提示。历史上已经错误创建的订单和库存未自动更改。

## 修复前审查记录（2026-10-20）

审查日期：2026-10-20。范围：当前目录的后端、前端及 AI/技能实现。目录没有 Git 历史，本报告审查当前源码，不判断缺陷由哪次提交引入。P1 表示应优先修复的安全或核心业务问题；P2 表示一般功能缺陷。

业务源码未修改。未连接真实数据库，未启动应用服务。下述动态验证使用真实业务类及 Spring 安全过滤链，外部数据访问由隔离替身提供；不等同于数据库端到端测试。

## 核心业务与安全

1. **[P1] 订单读取、取消和提交允许匿名访问，缺少订单归属检查。**
   [SecurityConfig.java:89](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/config/SecurityConfig.java:89) 对 `/api/orders/**` 全部放行；[OrderController.java:100](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/controller/OrderController.java:100) 取消订单时只传订单 ID，服务也不检查操作者。`/orders/{orderId}`、`/orders/all`、`/orders/user/{userId}` 同样暴露订单数据。通过真实过滤链与控制器、替身订单服务验证，匿名读取和取消请求均返回 200，提交返回 201。应要求认证，并在服务端核验所有权或管理员权限。

2. **[P1] 普通下单接口把所有订单归到用户 4。**
   [OrderController.java:46](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/controller/OrderController.java:46) 固定调用 `submitOrder(orderSubmitRequestDTO, 4L)`。其他账户购买也会记到用户 4；如果用户 4 不存在则所有下单失败。控制器隔离验证捕获到 `submitted_user_id=4`。应从已认证身份取得用户 ID。

3. **[P1] 登录成功后没有持久化认证状态。**
   [UserService.java:95](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/service/UserService.java:95) 仅设置当前请求线程的 `SecurityContextHolder`，没有保存到 `SecurityContextRepository`；前端 [api.js:7](/Users/xlchen/Documents/BookStore_s4/frontend/src/services/api.js:7) 的跨端口 fetch 也没有携带会话所需的 `credentials: 'include'`。真实 Spring Security 过滤链隔离验证：登录 200、无持久会话、随后 `/user/me` 为 400、管理员接口为 403。应完整实现会话保存和跨域凭据传递，或完整实现其他认证方案。

4. **[P1] 负数购买数量可增加库存并生成负金额订单。**
   [OrderSubmitRequestDTO.java:52](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/dto/OrderSubmitRequestDTO.java:52) 仅检查非空；[OrderService.java:73](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/service/OrderService.java:73) 只比较库存大小，随后直接减数量。真实校验器和服务类隔离验证：数量 `-2` 无校验错误，库存由 10 变为 12，单价 20 的订单金额为 -40。应使用正整数校验，同时在服务层保护该约束，覆盖 AI 等其他调用入口。

5. **[P1] 多商品订单失败时，已经扣除的库存可能仍提交。**
   [OrderService.java:52](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/service/OrderService.java:52) 使用默认 `@Transactional`，但书籍不存在、库存不足均抛受检 `Exception`。默认规则不会为这些异常回滚。第一本扣库存、第二本不存在时，订单尚未创建，前面的扣库存仍会提交。通过真实 Spring 事务拦截器和记录型事务管理器验证：第一本库存 10→8、commit 被调用 1 次、rollback 为 0。应使用会触发回滚的业务异常，或明确 `rollbackFor = Exception.class`。

6. **[P1] 扣库存没有注释所称的悲观锁，可并发超卖。**
   [OrderService.java:68](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/service/OrderService.java:68) 调用普通 `findById` 后读取、修改库存；BookRepository 无 `@Lock`，Book 无 `@Version`。两个事务可同时读到最后一本，分别创建订单并将库存写成 0，产生两笔成功订单。应使用带条件的原子扣减、数据库行锁或带重试策略的乐观锁。此项基于代码路径分析，未运行真实数据库并发测试。

7. **[P2] 已取消订单仍计入销量和用户消费。**
   [OrderService.java:219](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/service/OrderService.java:219) 与 255 行遍历所有订单，未过滤状态。取消订单虽然恢复库存，销量、金额及用户消费统计仍累计该笔订单。应明确统计口径，至少排除取消状态，并区分待支付与已成交订单。

## Standards：前端实现检查

未发现项目编码规范文件，因此以下是功能缺陷，不标记为违反项目规范；没有列入单纯风格或代码气味建议。

1. **[P2] 登录、注册提示抛异常。**
   [LoginModal.jsx:7](/Users/xlchen/Documents/BookStore_s4/frontend/src/components/LoginModal.jsx:7)、[RegisterModal.jsx:7](/Users/xlchen/Documents/BookStore_s4/frontend/src/components/RegisterModal.jsx:7)、[AIAssistant.jsx:11](/Users/xlchen/Documents/BookStore_s4/frontend/src/components/AIAssistant.jsx:11) 将 `message.useMessage()` 返回数组按对象解构，导致 `msg` 为 undefined。登录/注册成功和失败都会在提示处抛 TypeError，AI 请求失败时也一样。应使用 `[messageApi, contextHolder]` 并渲染 holder。参见 [Ant Design 官方 API](https://ant.design/components/message/)。抽取原始登录处理函数并模拟官方返回形状，已复现成功、失败两个分支的异常。

2. **[P2] 连点“一键购买”会重复提交订单。**
   [CartModal.jsx:29](/Users/xlchen/Documents/BookStore_s4/frontend/src/components/CartModal.jsx:29) 只在空购物车时禁用按钮，没有使用 Context 中的 loading；[CartContext.jsx:61](/Users/xlchen/Documents/BookStore_s4/frontend/src/context/CartContext.jsx:61) 也没有进行中的请求保护。首个请求完成前连续点击会重复发送同一购物车，后端每次创建订单并扣库存。以延迟响应运行原始处理函数，连续调用两次产生两次提交。应禁用提交中的按钮、增加同步防重入保护，并考虑服务端幂等控制。

3. **[P2] 下单失败没有界面反馈。**
   [CartModal.jsx:29](/Users/xlchen/Documents/BookStore_s4/frontend/src/components/CartModal.jsx:29) 直接把 handleCheckout 当点击回调；它返回的 `{ success, message }` 没有被消费。库存不足、服务不可达时不会展示错误。应等待返回结果并提示失败原因。

4. **[P2] 图表页面未清理 resize 监听。**
   [StatisticsPage.jsx:15](/Users/xlchen/Documents/BookStore_s4/frontend/src/pages/StatisticsPage.jsx:15) 丢弃初始化函数返回的清理函数，只 dispose 图表；[DashboardPage.jsx:17](/Users/xlchen/Documents/BookStore_s4/frontend/src/pages/DashboardPage.jsx:17) 同样如此。反复切页或数据更新会累积监听，仍引用已释放的图表实例。应在 effect cleanup 中调用初始化函数返回的清理函数。

此轴 4 项，最高优先级 P2；登录和注册反馈异常最直接影响使用。

## Spec：AI、技能与 RAG 行为

规格来源为 AI_ARCHITECTURE.md、prompts-RAG.md、prompts-MCP.md 与 SCRIPTS_README.md。未来路线图未作为当前必须实现的要求。

1. **[P1] 只请求推荐也会自动下单。**
   [BookRecommendationAgent.java:70](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/agent/BookRecommendationAgent.java:70) 自动选择首本书，76 行无条件转交订单智能体；订单智能体将“推荐几本Java编程的书籍”走创建分支并提交订单。提供用户 ID 时，真实 Coordinator→agents 源码隔离验证得到 `submitOrder calls=1, creationReported=true`。实际接入数据库会创建订单并扣库存。[prompts-RAG.md:159](/Users/xlchen/Documents/BookStore_s4/prompts-RAG.md:159) 要求的是“生成包含书名、作者、价格的推荐列表”。应移除推荐后的自动创建，只在明确购买动作中执行。

2. **[P1] 技能接口返回密码哈希。**
   [UserInfoSkill.java:70](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/agent/skill/impl/UserInfoSkill.java:70) 将完整 User 放入 context，SkillController 原样返回，User 的 getPassword 未屏蔽。由于接口匿名开放且接受客户端身份，传入 `context.userId` 为其他用户 ID 字符串即可进入查询路径。隔离 Jackson 序列化验证确认包含 password 字段；登录响应也复现了同一字段泄露。违反架构中“保护敏感数据”的要求。应使用仅含公开字段的 DTO，并修复身份校验；单独增加鉴权不能解决不必要的哈希返回。

3. **[P1] AI 和技能接口信任客户端伪造身份。**
   [AIAssistantController.java:28](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/controller/AIAssistantController.java:28) 直接使用请求体 userId；[SkillController.java:123](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/controller/SkillController.java:123) 信任客户端 context。安全配置默认放行这些接口，因此匿名请求可以冒用其他用户查询资料和订单，取消路径也不核验订单归属。违反架构的“身份认证”和“权限控制”要求。应从服务端认证上下文取得身份，并在业务服务中检查授权。本项与核心业务第 1 项相关，但属于额外入口。

4. **[P2] Chroma 查询响应解析会丢弃有效结果。**
   [RAGService.java:331](/Users/xlchen/Documents/BookStore_s4/backend/src/main/java/com/bookstore/service/RAGService.java:331) 使用字符串截断解析嵌套数组。对于 `distances:[[0.1]]`，最终数值字符串残留 `[0.1`，数值转换失败，返回空结果。原样提取解析方法的隔离验证确认合法嵌套结果返回空列表，影响文档承诺的语义搜索。应使用 JSON 解析器按查询批次读取 distances 和 metadatas。

此轴 4 项，最高优先级 P1；推荐触发非预期下单、伪造身份及密码哈希暴露均需优先处理。

## 验证范围

- `mvn -o test`：构建成功，执行 1 个测试，失败 0。测试仅打印密码匹配结果，没有断言，也没有订单、认证或权限覆盖。运行时出现沙箱临时目录权限警告，但 Maven 最终报告 BUILD SUCCESS。
- Spring MockMvc 与真实安全过滤链：确认登录状态不持久化、登录 JSON 包含 password、匿名订单请求可达、提交用户 ID 固定为 4。
- 真实 DTO 校验与 OrderService：确认负数数量被接受且库存增加、总金额为负。
- 真实 Spring 事务拦截器与替身事务管理器：确认第二项失败后走 commit 而非 rollback；未连接真实数据库。
- 真实 Coordinator 与 agent 源码、替身数据服务：确认推荐请求触发创建订单。
- Jackson 及 RAG 解析方法隔离验证：确认敏感字段序列化与嵌套响应解析错误。
- 前端原始函数隔离执行：确认消息 API 解构错误、连续点击重复提交。
- 前端没有 node_modules，未安装依赖，未执行完整前端构建和浏览器端到端测试；数据库并发及外部 Chroma/Ollama 集成未运行。
