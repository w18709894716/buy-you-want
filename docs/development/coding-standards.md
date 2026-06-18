# 编码规范

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
