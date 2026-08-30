# API 概览

## 1. 通用约定

- 本地网关：`http://localhost:8080`；以下路径均包含 `/api` 前缀。
- 除账号申请、登录和内部令牌状态校验外，业务请求携带 `Authorization: Bearer <JWT>`。
- JSON 使用 UTF-8；业务时间使用 Asia/Shanghai 本地 ISO 8601 格式（如 `2026-08-18T09:00:00`），页码从 1 开始。
- 主要列表在数据库侧分页；用户列表 `size` 限制为 1～100，仓储列表限制为 1～200（管理端当前提供到 100）。
- 浏览器应访问网关或前端反向代理。仓储服务直连 8082 也会验证 JWT 和角色，但生产网络仍不应公开内部服务端口。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

分页响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "page": 1,
    "size": 20
  }
}
```

典型 HTTP 状态：

| 状态 | 含义 |
| ---: | --- |
| `400` | 参数或业务前置条件错误 |
| `401` | 未登录、令牌签名/签发方/受众/有效期/`jti` 无效 |
| `403` | 当前角色无权执行操作 |
| `404` | 资源不存在 |
| `409` | 状态冲突、库存不足或幂等键冲突 |
| `429` | 登录失败次数达到锁定阈值 |
| `500` | 未预期服务错误 |

## 2. JWT 与登录保护

### 登录

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<your-password>"}'
```

`data` 包含 `accessToken`、`tokenType`、`expiresIn` 和 `user`。JWT 使用 HS256，包含 `sub`、`uid`、`role`、`iss`、`aud`、`iat`、`exp` 和随机 `jti`；认证服务、网关和仓储服务必须配置完全一致的 `JWT_SECRET`、`JWT_ISSUER` 和 `JWT_AUDIENCE`。

默认连续 5 次密码失败后锁定 15 分钟，分别由 `LOGIN_MAX_FAILURES` 和 `LOGIN_LOCK_MINUTES` 配置。被停用用户与不存在用户均返回通用凭据错误；锁定用户返回 `429`。本地演示账号 `admin` / `Firefly@123` 禁止用于共享或生产环境。

### 认证接口

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/login` | 登录并签发 JWT | 匿名 |
| `POST` | `/api/auth/register` | 提交待管理员启用的收货员账号申请 | 匿名 |
| `POST` | `/api/auth/logout` | 撤销当前 JWT | 已认证 |
| `GET` | `/api/auth/me` | 读取当前数据库用户资料与角色 | 已认证 |

## 3. 用户与角色 API

这些接口是真实持久化能力，仅 `ADMIN` 可调用。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/auth/users` | 按关键字、状态、角色分页查询用户 |
| `POST` | `/api/auth/users` | 新增用户 |
| `PATCH` | `/api/auth/users/{id}` | 修改姓名、密码、角色或状态 |
| `GET` | `/api/auth/roles` | 查询角色编码、名称和职责范围 |
| `GET` | `/api/auth/audit-events` | 分页查询认证审计 |

用户列表参数：`page`、`size`、`keyword`、`status=PENDING|ACTIVE|DISABLED|LOCKED`、`role`。当前每个用户只能拥有一个角色；请求既兼容 `role`，也兼容仅含一个元素的 `roles`。

新增用户：

```json
{
  "username": "receiver01",
  "password": "Strong@123",
  "displayName": "一号收货员",
  "roles": ["RECEIVER"],
  "status": "ACTIVE"
}
```

更新用户：

```json
{
  "displayName": "一号收货员",
  "roles": ["WAREHOUSE_MANAGER"],
  "status": "ACTIVE",
  "password": "NewStrong@123"
}
```

更新字段均可选。密码必须为 8～72 位并同时包含大小写字母、数字和特殊字符。管理员不能停用自己或移除自己的 `ADMIN` 角色，系统也拒绝停用最后一名启用管理员。

每个用户管理请求都会按 JWT 的用户 ID 重新核对数据库操作者状态和当前角色；停用、锁定或已降级管理员的旧令牌返回 `403`。管理员集合变更由固定数据库锁串行化，并发操作也必须至少保留一名启用管理员。

## 4. 角色矩阵

| API 能力 | `ADMIN` | `WAREHOUSE_MANAGER` | `RECEIVER` | `PICKER` |
| --- | :---: | :---: | :---: | :---: |
| `/api/auth/users*`、`/api/auth/roles`、`/api/auth/audit-events` | 读写 |  |  |  |
| 一般仓储与聚合订单 `GET` 查询 | ✓ | ✓ | ✓ | ✓ |
| 快递账号与同步日志查询 | ✓ | ✓ |  |  |
| 基础资料 `POST` | ✓ | ✓ |  |  |
| `/api/inventory/adjustments`、`/transfers`、`/stocktakes` | ✓ | ✓ |  |  |
| 入库创建、收货 | ✓ | ✓ | ✓ |  |
| 出库创建、分配、拣货、包装、取消、发运、退货 | ✓ | ✓ |  | ✓ |

`WAREHOUSE_ADMIN` 作为历史兼容角色会被规范化为 `ADMIN`。未知或不受支持的签名角色没有查询权限。Actuator 的公开面仅为 health/info，其余 `/actuator/**` 仅管理员可经网关访问。

仓储服务会再次执行本矩阵，并从 JWT 重建 `X-User-Id`、`X-Username` 和 `X-User-Role`，覆盖请求携带的同名值。内部系统即使直连仓储服务，也必须发送同一有效 Bearer JWT，不能使用这些身份头代替认证。

## 5. 幂等请求

以下接口支持 `Idempotency-Key`：

| 操作 | 接口 |
| --- | --- |
| 创建入库 | `POST /api/inbound-orders` |
| 确认收货 | `POST /api/inbound-orders/{id}/receive` |
| 库存调整 | `POST /api/inventory/adjustments` |
| 库存移库 | `POST /api/inventory/transfers` |
| 库存盘点 | `POST /api/inventory/stocktakes` |
| 创建出库 | `POST /api/outbound-orders` |
| 库存分配 | `POST /api/outbound-orders/{id}/allocate` |
| 拣货、包装、取消 | `POST /api/outbound-orders/{id}/pick|pack|cancel` |
| 确认发运 | `POST /api/outbound-orders/{id}/ship` |
| 整单退货 | `POST /api/outbound-orders/{id}/return` |

示例：

```bash
curl -X POST http://localhost:8080/api/inbound-orders/1/receive \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: receive-inbound-1-attempt-1" \
  -d '{"locationCode":"RCV-01"}'
```

键最多 128 个字符。一次业务意图第一次提交和后续重试必须使用同一键及相同请求内容；相同操作和键会返回首次保存的业务结果，同键不同内容返回 `409`。不同操作可以使用相同键，但推荐全局生成 UUID。管理端会自动添加 UUID；第三方调用方需自己保存并复用键。

## 6. 运营总览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/dashboard/summary` | SKU、库存、待处理单据、低库存、临期和近期活动汇总 |

## 7. 基础资料 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/warehouses` | 分页查询仓库 |
| `POST` | `/api/warehouses` | 新建仓库 |
| `GET` | `/api/locations` | 分页查询货位，可按仓库过滤 |
| `POST` | `/api/locations` | 新建货位 |
| `GET` | `/api/products` | 分页查询 SKU/商品 |
| `POST` | `/api/products` | 新建 SKU/商品 |
| `GET` | `/api/partners` | 分页查询供应商/客户 |
| `POST` | `/api/partners` | 新建供应商/客户 |

通用参数为 `page`、`size`、`keyword`；货位增加 `warehouseId`，合作方增加 `type=SUPPLIER|CUSTOMER`。

创建仓库示例：

```json
{
  "code": "WH-SH-01",
  "name": "上海一号仓",
  "address": "上海市",
  "manager": "张三",
  "status": "ACTIVE"
}
```

只有启用的仓库、货位、商品和对应类型合作方可以参与新的仓储作业。

## 8. 入库 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/inbound-orders` | 分页查询入库单，可按 `keyword`、`status` 筛选 |
| `POST` | `/api/inbound-orders` | 创建入库单及明细，支持幂等 |
| `POST` | `/api/inbound-orders/{id}/receive` | 分批或整单收货、入账并写流水，支持幂等 |

```json
{
  "warehouseId": 1,
  "supplierId": 1,
  "expectedAt": "2026-08-18T09:00:00",
  "remark": "采购到货",
  "items": [
    {
      "productId": 1,
      "quantity": 100,
      "batchNo": "B20260818",
      "expiryDate": "2027-08-18"
    }
  ]
}
```

收货请求可指定本次实际数量：`{"locationCode":"RCV-01","items":[{"itemId":1,"quantity":40}]}`。`items` 省略时接收所有剩余数量；状态按 `PENDING → PARTIALLY_RECEIVED → RECEIVED` 推进。

## 9. 库存与流水 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/inventory` | 分页查询库存余额 |
| `GET` | `/api/inventory/movements` | 分页查询不可变库存流水 |
| `POST` | `/api/inventory/adjustments` | 库存增减调整，支持幂等 |
| `POST` | `/api/inventory/transfers` | 同仓货位移库，支持幂等 |
| `POST` | `/api/inventory/stocktakes` | 按库存明细登记实盘数量并写差异流水 |

库存余额参数：`page`、`size`、`keyword`、`warehouseId`。流水参数：`page`、`size`、`keyword`、`warehouseId`、`type`；关键字可匹配流水号、SKU/商品、批次、引用类型和操作人。流水结果包含 `movementNo`、`type`、仓库/货位、SKU、批次、带方向数量、业务引用、原因、操作人和时间。

调整示例：

```json
{
  "warehouseId": 1,
  "locationCode": "A-01-01",
  "productId": 1,
  "quantity": -2,
  "batchNo": "B20260818",
  "expiryDate": "2027-08-18",
  "reason": "破损调整"
}
```

移库示例：

```json
{
  "inventoryId": 18,
  "sourceLocationCode": "A-01-01",
  "targetLocationCode": "PICK-01",
  "quantity": 10,
  "reason": "拣选位补货"
}
```

库存余额区分实物量、可用量、已分配量和锁定量。调整和移库在事务内加锁、校验非负并追加流水。盘点请求为 `{"inventoryId":18,"actualQuantity":97,"reason":"月度循环盘点"}`；存在已分配或冻结数量时拒绝盘点。

## 10. 出库 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/outbound-orders` | 分页查询出库单，可按 `keyword`、`status` 筛选 |
| `POST` | `/api/outbound-orders` | 创建出库单及明细，支持幂等 |
| `POST` | `/api/outbound-orders/{id}/allocate` | 按 FEFO 分配可用库存，支持幂等 |
| `POST` | `/api/outbound-orders/{id}/pick` | 确认拣货 |
| `POST` | `/api/outbound-orders/{id}/pack` | 确认复核包装 |
| `POST` | `/api/outbound-orders/{id}/cancel` | 取消并释放库存预占 |
| `POST` | `/api/outbound-orders/{id}/ship` | 发运扣减并写流水，支持幂等 |
| `POST` | `/api/outbound-orders/{id}/return` | 已发运订单整单退回并恢复原库存 |

```json
{
  "warehouseId": 1,
  "customerId": 2,
  "requiredAt": "2026-08-19T16:00:00",
  "remark": "优先发货",
  "items": [
    {
      "productId": 1,
      "quantity": 5,
      "batchNo": ""
    }
  ]
}
```

主状态流为 `PENDING → ALLOCATED → PICKED → PACKED → SHIPPED`；待发运状态均可取消并释放预占，已发运订单可整单退回为 `RETURNED`。未指定批次时按 FEFO 分配；已过期库存和停用货位不会参与分配。

## 11. 多快递集成 API

快递账号配置与同步操作仅 `ADMIN`、`WAREHOUSE_MANAGER` 可用；收货员和拣货员只能查询聚合订单。响应只返回脱敏提示，不返回凭证明文或密文。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET/POST` | `/api/carrier-accounts` | 分页查询或创建快递账号 |
| `PUT` | `/api/carrier-accounts/{id}` | 更新账号；凭证留空表示保持不变 |
| `POST` | `/api/carrier-accounts/{id}/test` | 测试账号配置 |
| `POST` | `/api/carrier-accounts/{id}/sync` | 手动拉取订单并记录日志 |
| `GET` | `/api/carrier-orders` | 按关键字、快递公司或状态分页查询聚合订单 |
| `GET` | `/api/carrier-sync-logs` | 按账号分页查询同步日志 |

创建账号示例：

```json
{
  "warehouseId": 1,
  "carrierCode": "SF",
  "accountName": "顺丰沙箱账号",
  "apiBaseUrl": "mock://sf",
  "credential": "your-token-or-app-secret",
  "status": "ACTIVE"
}
```

第二阶段仍使用 `mock://` 确定性适配器，每次同步生成或更新当天 3 张新疆匿名演示订单，重复同步不会重复插入。账号请求可用 `syncEnabled` 和 `syncIntervalMinutes`（1～1440）配置自动同步；响应包含 `nextSyncAt`、`consecutiveFailures` 和 `circuitOpenedUntil`。手动调用受最小间隔限制，过于频繁或熔断中返回 `429`。真实公网 Token 不应填入演示环境。

## 12. OpenAPI 与待办

认证和仓储服务启用 `SPRINGDOC_ENABLED=true` 时分别在内部端口提供 `/swagger-ui.html` 与 `/v3/api-docs`。生产建议关闭交互 UI。当前 URL 尚未加入版本段；正式对外集成前应冻结契约并迁移到 `/api/v1/**`。质检/上架、部分发运、范围冻结、部分退货、PDA、条码和波次相关接口仍未实现。
