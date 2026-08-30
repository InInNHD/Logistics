# Railway 部署指南

本目录为 Firefly Logistics 的 Railway 单项目、五服务部署方案。Railway 在云端执行 Dockerfile 构建，因此本机不需要安装 Docker。

## 服务拓扑

| Railway 服务名 | 是否公网 | 配置文件 | 端口 |
| --- | --- | --- | --- |
| `frontend` | 是 | `/deploy/railway/frontend.json` | `80` |
| `gateway` | 否 | `/deploy/railway/gateway.json` | `8080` |
| `auth-service` | 否 | `/deploy/railway/auth-service.json` | `8081` |
| `warehouse-service` | 否 | `/deploy/railway/warehouse-service.json` | `8082` |
| `MySQL` | 否 | Railway MySQL 模板 | `3306` |

浏览器只访问 `frontend`。Caddy 将 `/api/**` 通过 Railway 私有网络转发到 `gateway`，认证和仓储服务不创建公网域名。

## 部署前准备

1. 为项目创建首个 Git 提交，并推送到 GitHub、GitLab 或 Bitbucket 私有仓库。
2. 在 Railway 创建空项目，添加 MySQL 数据库以及四个空服务，并将服务名设置为上表中的名称；只为 `frontend` 生成 Railway Domain，供网关 CORS 变量引用。
3. 为四个代码服务连接同一个代码仓库，Root Directory 均保持 `/`。
4. 在每个服务的 Settings 中设置对应的 Config File Path。

## 服务变量

### `auth-service`

```text
MODULE=auth-service
PORT=8081
SERVER_PORT=8081
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
JWT_SECRET=<至少 32 字节的随机密钥>
JWT_ISSUER=firefly-logistics
JWT_AUDIENCE=firefly-logistics-web
JWT_TTL_HOURS=1
ADMIN_USERNAME=admin
ADMIN_PASSWORD=<随机强密码>
ADMIN_BOOTSTRAP_ENABLED=true
LOGIN_MAX_FAILURES=5
LOGIN_LOCK_MINUTES=15
SPRINGDOC_ENABLED=false
```

### `warehouse-service`

```text
MODULE=warehouse-service
PORT=8082
SERVER_PORT=8082
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
JWT_SECRET=<与 auth-service 和 gateway 完全一致>
JWT_ISSUER=firefly-logistics
JWT_AUDIENCE=firefly-logistics-web
WAREHOUSE_SECURITY_ENABLED=true
CARRIER_CREDENTIAL_KEY=<至少 32 字符且独立于 JWT_SECRET 的随机密钥>
CARRIER_SYNC_SCHEDULER_ENABLED=true
CARRIER_SYNC_MAX_ATTEMPTS=3
CARRIER_SYNC_CIRCUIT_THRESHOLD=3
SPRINGDOC_ENABLED=false
```

### `gateway`

```text
MODULE=gateway
PORT=8080
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
AUTH_SERVICE_URL=http://${{auth-service.RAILWAY_PRIVATE_DOMAIN}}:${{auth-service.PORT}}
WAREHOUSE_SERVICE_URL=http://${{warehouse-service.RAILWAY_PRIVATE_DOMAIN}}:${{warehouse-service.PORT}}
CORS_ALLOWED_ORIGINS=https://${{frontend.RAILWAY_PUBLIC_DOMAIN}}
JWT_SECRET=<与 auth-service 完全一致>
JWT_ISSUER=firefly-logistics
JWT_AUDIENCE=firefly-logistics-web
TOKEN_STATUS_CHECK_ENABLED=true
```

### `frontend`

```text
PORT=80
API_UPSTREAM=http://${{gateway.RAILWAY_PRIVATE_DOMAIN}}:${{gateway.PORT}}
```

`PORT` 必须在三个 Java 服务上显式设置，才能同时用于 Railway 私有域名引用和健康检查。

## 发布与验收

1. 先部署 MySQL，随后部署 `auth-service` 和 `warehouse-service`。
2. 两个后端健康检查通过后部署 `gateway`，最后部署 `frontend`。
3. 确认只有 `frontend` 存在 Railway Domain，三个 Java 服务均未开启公网域名。
4. 访问 `https://<frontend-domain>/health`，应返回 `ok`。
5. 打开前端域名并登录，完成注册申请、管理员启用、分批收货、出库分配/拣货/包装/发运和退出失效的冒烟测试。

部署后立即更换默认管理员密码，并在 Railway 中启用用量上限和数据库备份策略。网关通过认证服务检查令牌撤销、账号状态和最新角色；认证服务不可用时会拒绝受保护请求。生产示例仍将 JWT 有效期设为 1 小时。Redis 当前不是运行必需服务，不要在五服务试用项目中额外创建。
