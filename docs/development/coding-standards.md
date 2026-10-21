# 编码规范

## 改动原则（优先级最高）

**1. 只修漏洞，不删业务场景。**

发现问题时一律**修**，不**删**。哪怕某个功能当前没有前端入口、没有调用者、看起来"用不到"，
它也可能是以后要用的，必须保留：

- 未被引用的**常量 / topic / 事件生产者 / 接口 / 页面**同样适用 —— 保留声明，只把漏洞堵上；
- 判定"死代码"不是删除的理由，除非**项目负责人明确同意**；
- 历史上的反例：拼团功能曾因"是空壳"被整体删除，后按本原则完整恢复并改为加固。

**2. 副作用不得占用业务请求的关键路径。**

远程调用、消息投递、审计落库这类"旁路"动作，允许失败、但**不允许拖慢或打断主流程**：
用 `asyncSend`/`afterCommit` 而非在请求线程里同步等待；失败只告警。反例：审计切面曾用 `syncSend`，
broker 抖动 3 秒就把调用方的 Feign 读超时（5s）打爆，业务接口直接 500。

**3. Sa-Token 的原始类型 getter 缺省返回 0，不要用来判断"是否为空"。**

`SaSession.getLong(key)` / `getInt(key)` 返回原始类型，键不存在时给的是 **0** 而不是 `null`。
读可空字段要用 `session.get(key)` 再自行判空，否则会把"没有这个值"变成"值是 0"，
并被写回会话、注入请求头，最终让下游的店铺级校验（`canAccessShop`）把平台管理员的
平台级资源操作全部误判为越权（真实踩过）。

**4. 能用数据库约束兜底的，不要只靠代码纪律。**

唯一键、条件更新（CAS）、生成列等由数据库强制的约束，不依赖任何人记得把代码写对，
是"防重复 / 防超发 / 防丢更新"的首选手段。

## 包结构规范
每个微服务的标准包结构：
```
com.byw.xxx
├── BywXxxApplication.java    # 启动类
├── controller/               # REST控制器
├── service/                  # 业务接口
│   └── impl/                 # 业务实现
├── mapper/                   # MyBatis Mapper接口
├── entity/                   # 数据库实体类
├── dto/                      # 数据传输对象
├── vo/                       # 视图对象（返回给前端）
├── config/                   # 模块配置
└── listener/                 # 消息监听器（可选）
```

## 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 包名 | 全小写 | com.byw.order |
| 类名 | 大驼峰 | OrderServiceImpl |
| 方法名 | 小驼峰 | createOrder() |
| 常量 | 全大写下划线 | MAX_RETRY_COUNT |
| 数据库表 | t_ 前缀小写下划线 | t_order |
| 数据库字段 | 小写下划线 | created_at |
| REST路径 | 小写中划线 | /api/order-items |
| Controller | XxxController | OrderController |
| Service接口 | XxxService | OrderService |
| Service实现 | XxxServiceImpl | OrderServiceImpl |
| Mapper | XxxMapper | OrderMapper |
| Entity | 与表名对应 | Order |
| DTO | XxxDTO | OrderCreateDTO |
| VO | XxxVO | OrderDetailVO |

## 异常处理

### 全局异常处理
byw-common-core 提供 GlobalExceptionHandler，统一捕获：
- **BusinessException** — 业务异常，返回对应错误码
- **MethodArgumentNotValidException** — 参数校验失败
- **ConstraintViolationException** — 约束校验失败
- **Exception** — 未知异常，返回 SYSTEM_ERROR

### 业务异常使用
```java
// 抛出业务异常
throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
```

## 自动配置机制
各 common 模块使用 Spring Boot 3.x 的 `@AutoConfiguration` 机制：
- 每个模块在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册自动配置类
- 引入 Maven 依赖即自动生效，无需 `@ComponentScan`

## 数据库规范
- **主键**：BIGINT AUTO_INCREMENT
- **逻辑删除**：deleted 字段（0未删除 1已删除），配合 MyBatis-Plus `@TableLogic`
- **审计字段**：created_at / updated_at，自动填充
- **索引命名**：`idx_字段名` 或 `uk_字段名`（唯一索引）
- **字符集**：utf8mb4

## 日志规范
```java
@Slf4j
public class OrderServiceImpl implements OrderService {
    // 关键操作记录日志
    log.info("创建订单: userId={}, orderNo={}", userId, orderNo);
    log.error("创建订单失败: userId={}", userId, e);
}
```
