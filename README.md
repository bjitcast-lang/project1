# 分布式短链服务

独立 Spring Boot 项目（端口 8082，库 `shortlink`），作为主项目之外的副项目，展示
**号段发号、布隆过滤器、缓存穿透 / 击穿、PV/UV 统计、令牌桶限流** 等后端能力。

## 技术栈

Java 8 · Spring Boot 2.3.12 · MyBatis-Plus 3.4.3 · Spring Data Redis 2.3.9 · MySQL 8 · Redis Bitmap

## 核心设计

| 能力 | 实现 |
|---|---|
| 短码生成 | Base62 编码，号段模式发号器（双 buffer + 后台预取） |
| 发号不重复 | `UPDATE t_id_segment SET max_id=max_id+step` 行锁 + 唯一索引兜底 |
| 跳转 | `GET /{shortUri}` → 缓存 → 空值缓存 → 布隆 → DB → 302 |
| 缓存穿透 | 空值缓存 2 分钟 |
| 缓存击穿 | 逻辑过期 + `SETNX` 锁异步重建，旧值兜底 |
| 布隆过滤器 | 自研 Redis Bitmap，双缓冲 + 每日重建 |
| 访问统计 | 异步写 Redis，每分钟聚合 upsert 到 `t_link_access_stats` |
| 限流 | Lua 令牌桶，20 次/分钟/IP |

## 数据库

```bash
mysql -uroot -p123456 -h127.0.0.1 < src/main/resources/db/shortlink.sql
```

## 编译 & 启动

```bash
mvn clean package -DskipTests
mvn spring-boot:run
```

> 依赖 Redis 与 MySQL。连接信息见 `src/main/resources/application.yaml`，
> 账号密码、Redis 地址请改成自己的环境。

## 接口清单

管理接口统一校验 `X-Api-Key: shortlink-api-key`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/shortlink/create` | 创建，body `{originUrl, groupId?, customCode?, validDate?}` |
| GET | `/api/shortlink/page?current=&size=&groupId=` | 分页 |
| POST | `/api/shortlink/update` | 更新，body `{id, originUrl?, groupId?, validDate?, state?}` |
| DELETE | `/api/shortlink/{id}` | 逻辑删除 |
| GET | `/api/shortlink/stats?code=&start=&end=` | 统计 |
| GET | `/{shortUri}` | 302 / 404 / 410 |

## 验收（curl / Apifox）

```bash
BASE=http://127.0.0.1:8082
KEY="X-Api-Key: shortlink-api-key"

# 1. 创建自动短码
curl -X POST "$BASE/api/shortlink/create" -H "$KEY" -H "Content-Type: application/json" \
  -d '{"originUrl":"https://www.bilibili.com"}'

# 2. 创建自定义短码
curl -X POST "$BASE/api/shortlink/create" -H "$KEY" -H "Content-Type: application/json" \
  -d '{"originUrl":"https://www.baidu.com","customCode":"myblog"}'

# 3. 跳转（302，Location 正确）
curl -i "http://127.0.0.1:8082/myblog"

# 4. 统计（等 1 分钟落库）
curl "$BASE/api/shortlink/stats?code=myblog" -H "$KEY"

# 5. 重复自定义短码 → 失败
# 6. 不存在短码 → 404；过期链接 → 410
# 7. 连续创建超过 20 次/分钟 → 429
```

## 关键文件

- 发号：`utils/SegmentIdGenerator.java`、`utils/IdSegmentAllocator.java`
- 短码：`utils/ShortCodeUtil.java`
- 布隆：`utils/BloomFilterService.java`、`job/BloomRebuildJob.java`
- 跳转 / 缓存：`service/impl/ShortLinkServiceImpl.java`、`controller/RedirectController.java`
- 统计：`service/impl/StatsServiceImpl.java`、`job/StatsFlushJob.java`
- 限流：`utils/TokenBucketLimiter.java`、`lua/token_bucket.lua`
