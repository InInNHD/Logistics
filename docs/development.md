# 开发与启动指南

## 1. 环境选择

| 方式 | 本机要求 | 适用场景 |
| --- | --- | --- |
| 完整 Docker Compose | Docker Compose v2 | 一条命令运行完整应用栈 |
| 原生开发 | JDK 17+、Maven 3.8+、Node.js 20.19+/22.12+、可访问的 MySQL 8 | 调试源码和热更新 |
| Railway | Git 远程仓库与 Railway 账号 | 本机没有 Docker、需要云端演示 |

Windows 用户可以直接在 PowerShell 执行本文对应命令。路径包含中文时应使用 UTF-8 终端并避免过长目录。

## 2. 环境变量

仓库根目录提供 `.env.example`：

```powershell
Copy-Item .env.example .env
```

macOS/Linux：

```bash
cp .env.example .env
```

`.env` 已被 Git 忽略。Compose 会自动读取它；直接执行 Java 或 npm 时，应用不会自动读取根目录 `.env`，需要在终端、IDE Run Configuration 或部署平台中注入变量。

关键配置：

| 变量 | 用途 |
| --- | --- |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` | Compose 与 Windows 一键脚本使用的 MySQL 地址 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 一键脚本归一化为 Java 服务的数据库账号 |
| `MYSQL_SSL_MODE` | MySQL Client 与 JDBC TLS 模式；本机可用 `DISABLED`，远程至少为 `REQUIRED` |
| `MYSQL_CLIENT_PATH` / `MYSQL_SERVER_PATH` / `MYSQL_CONFIG_FILE` | 可选 ZIP 版 MySQL 绝对路径；用于客户端发现及按需启动本地独立实例 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 手动启动 Java 或云端部署时使用的 MySQL JDBC 连接 |
| `JWT_SECRET` | 三个服务共享的 HS256 密钥，至少 32 字节 |
| `JWT_ISSUER` / `JWT_AUDIENCE` | 三个服务完全相同的签发方与受众 |
| `JWT_TTL_HOURS` | 认证服务令牌有效期；生产建议短有效期，Railway 示例为 1 小时 |
| `LOGIN_MAX_FAILURES` / `LOGIN_LOCK_MINUTES` | 登录锁定策略 |
| `ADMIN_BOOTSTRAP_ENABLED` | 是否在无管理员时创建引导账号 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 引导管理员；只允许开发默认值 |
| `CORS_ALLOWED_ORIGINS` | 网关允许的浏览器来源 |
| `WAREHOUSE_SECURITY_ENABLED` | 仓储本地 JWT/RBAC；生产必须保持 `true` |

认证服务、网关和仓储服务使用相同 JWT 三项配置，否则会出现登录成功但下游 `401`。`prod`/`production` Profile 会拒绝已知开发 JWT 密钥；认证服务还会拒绝默认管理员密码。

## 3. 完整 Docker Compose

根目录的 `docker-compose.yml` 不是只启动基础设施，而是完整应用编排：

| 服务 | 默认是否启动 | 宿主端口 | 说明 |
| --- | :---: | ---: | --- |
| `mysql` | ✓ | 3306 | MySQL 8 数据卷与健康检查 |
| `auth-service` | ✓ | 仅容器网络 | 登录和用户管理 |
| `warehouse-service` | ✓ | 仅容器网络 | 仓储服务，启用 `demo` Profile |
| `gateway` | ✓ | 8080 | API 入口 |
| `frontend` | ✓ | 5173 | Caddy 托管 SPA 并代理 `/api` |
| `redis` |  | 6379 | 可选 Profile，当前业务不依赖 |

启动完整栈：

```bash
docker compose up --build -d
docker compose ps
```

首次构建需要下载 Maven、npm 和镜像依赖。服务健康后打开 `http://localhost:5173`，默认开发账号为 `admin` / `Firefly@123`。

查看日志：

```bash
docker compose logs -f auth-service warehouse-service gateway frontend
```

启用可选 Redis：

```bash
docker compose --profile redis up --build -d
```

停止并保留数据：

```bash
docker compose down
```

`docker compose down -v` 会永久删除本项目 MySQL/Redis 数据卷，只能在确认开发数据可丢弃后使用。

Compose 为 warehouse-service 设置 `SPRING_PROFILES_ACTIVE=demo`，因此新库执行仓储 V1、Demo V2、V3。生产环境不得照搬此 Profile。

认证与仓储共用 `firefly_logistics` schema，但使用各自的 Flyway 历史表。两端均以版本 `0` 启用 `baseline-on-migrate`：第二个启动的服务先写入 V0 基线标记，再正常执行 V1 及后续迁移；V0 不是业务迁移，也不会跳过 V1。一键脚本会在启动前检查对应的 `sys_`/`wms_` 表，避免把已有模块误当成新模块。

## 4. 原生开发启动

### 4.0 Windows 一键启动

本机已提供双击入口：

```text
start.cmd   检查环境、构建并启动全部应用
stop.cmd    只停止状态文件严格校验通过的本项目进程
```

`start.cmd` 使用本地 Demo 模式，流程为：严格解析根目录 `.env` → 检查 JDK/Maven/Node 和端口 → 按需启动已配置的本地 ZIP 版 MySQL → 读取 MySQL 握手版本 → 检查或初始化数据库 → 校验 Flyway 模式 → 构建三个可执行 JAR → 依次启动认证、仓储、网关和 Vue 前端 → 等待健康检查 → 打开浏览器。首次没有 `.env` 时会从 `.env.example` 创建；首次初始化本机 MySQL 8 时会用安全输入框询问管理员密码，该密码不会进入命令行、日志或 PID 状态。

要求 MySQL Server 和 Client 都为 8.x，Server 至少为 8.0.16。脚本不会替换或修改已有 MySQL 5.7 服务；可以并行安装 MySQL 8 到其他端口并修改 `.env`，也可以配置远程 MySQL 8。若填写 `DB_URL`，它必须与 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE` 完全一致；否则脚本会拒绝启动，避免连错数据库。远程数据库必须将 `MYSQL_SSL_MODE` 设为 `REQUIRED`、`VERIFY_CA` 或 `VERIFY_IDENTITY`，并让 `DB_URL` 使用相同 `sslMode`。对于已迁移数据库，Demo 模式必须包含仓储 V2；已经按非 Demo 模式执行到 V3 的库不能随后用一键脚本补跑低版本 V2。

所有应用显式绑定 `127.0.0.1`。若配置了 `MYSQL_SERVER_PATH` 与 `MYSQL_CONFIG_FILE`，一键脚本会在目标本地端口未监听时启动该实例，但 `stop.cmd` 仍只停止四个应用，不停止 MySQL。运行日志及经过校验的进程状态位于 `.firefly/runtime/`；`.env` 与运行目录会收紧为当前用户、SYSTEM 和 Administrators 可访问。重复启动、端口被其他程序占用或 PID 身份不一致时，脚本会拒绝自动终止未知进程。高级用法可在 PowerShell 执行：

```powershell
.\scripts\start.ps1 -DataMode Empty
.\scripts\start.ps1 -DataMode Demo -AllowDevDefaults -NoBrowser
.\scripts\stop.ps1
```

### 4.1 准备 MySQL

使用本机或远程 MySQL 8 创建数据库和最小权限业务用户，并在三个服务的启动环境中设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。若本机恰好有 Docker，也可只启动数据库：

```bash
docker compose up -d mysql
```

不要把“只启动 mysql”误认为完整系统；它只是原生开发方式的数据库依赖。

### 4.2 构建后端

```bash
cd backend
mvn clean install
```

分别在三个终端从 `backend` 目录启动：

```bash
mvn -pl auth-service spring-boot:run
```

```bash
mvn -pl warehouse-service spring-boot:run
```

```bash
mvn -pl gateway spring-boot:run
```

| 服务 | 地址 |
| --- | --- |
| gateway | `http://localhost:8080` |
| auth-service | `http://localhost:8081` |
| warehouse-service | `http://localhost:8082` |

默认原生启动 warehouse-service 只扫描 `db/migration`，不会插入演示主数据。需要本地 Demo 时显式设置 `SPRING_PROFILES_ACTIVE=demo`；不要在共享或生产库启用。

8082 直连也必须携带 Bearer JWT，并会重新从令牌声明构造身份头。仅在测试 Profile 中才可将 `WAREHOUSE_SECURITY_ENABLED=false`，不要用它解决本地 401。

### 4.3 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`。Vite 默认把 `/api` 代理到 `http://localhost:8080`。直连其他网关时，在 `frontend/.env.local` 中配置：

```dotenv
VITE_API_BASE_URL=https://your-host.example.com/api
```

`VITE_*` 会编译进浏览器包，禁止存放密钥和密码。

## 5. 本机无 Docker：Railway

本项目提供 Railway 的四个应用服务配置和通用 Dockerfile，云端负责镜像构建，本机无需安装 Docker。完整步骤见 [Railway 部署指南](../deploy/railway/README.md)。

部署要点：

- 创建 Railway MySQL、`auth-service`、`warehouse-service`、`gateway` 和 `frontend`。
- 仅 frontend 创建公网域名，其他服务使用 Railway 私有域名。
- 三个 Java 服务设置 `SPRING_PROFILES_ACTIVE=prod`（按各自配置文件）；不要给 warehouse-service 加 `demo`。
- auth-service、gateway、warehouse-service 配置完全相同的 `JWT_SECRET`、`JWT_ISSUER`、`JWT_AUDIENCE`。
- 生产 JWT 密钥和管理员密码必须随机生成，不能复制 `.env.example` 默认值。
- 先让数据库和两个业务服务健康，再部署网关和前端。

## 6. Flyway Profile 与旧库升级

当前仓储迁移布局：

```text
db/migration/V1__create_warehouse_schema.sql
db/migration/V3__harden_inventory_and_idempotency.sql
db/demo/V2__seed_demo_master_data.sql
application-demo.yml  # 追加 classpath:db/demo
```

全新默认/生产数据库执行 V1、V3，不含演示数据；全新 Demo 数据库从两个 locations 合并排序，执行 V1、V2、V3。应在空库第一次迁移前决定是否使用 Demo：默认库已经执行到 V3 后再打开 `demo` 时，低版本 V2 不会默认补跑；请通过 API 建立数据或新增受控的高版本 seed，不要篡改迁移历史。

如果旧数据库已登记原先位于默认 migration 目录的 V2，新版本可能报 `applied migration not resolved locally` 或 checksum mismatch。处理前先备份；不要直接删历史记录或随意执行 `flyway repair`。需要保留旧库时继续使用含原 V2 的匹配构件并设计显式迁移；可重建时将业务数据迁移到由当前版本初始化的新库。详见[数据模型](data-model.md)。

## 7. 构建、测试与 CI

后端完整验证：

```bash
cd backend
mvn clean verify
```

测试覆盖 JWT issuer/audience/jti、登录锁定、用户 RBAC、最后管理员并发保护、旧管理员令牌复核、网关与仓储双层授权、伪造身份头、库存并发、幂等、FEFO、数据库分页、库存流水和主业务闭环。MySQL Testcontainers 会验证默认仓储迁移可从空库执行到当前版本 3（即默认目录中的 V1、V3）；当运行环境没有 Docker 时该测试自动跳过。

前端验证：

```bash
cd frontend
npm ci
npm run lint
npm test
npm run build
```

GitHub Actions 配置在 `../.github/workflows/ci.yml`，对 `main`/`master` 推送和 Pull Request 执行：

- Maven `clean verify`；
- npm `ci`、ESLint、Vitest 和生产构建。

依赖更新由 `../.github/dependabot.yml` 管理。提交前应在与 CI 相同的 JDK/Node 主版本上执行上述命令。

## 8. 接口冒烟测试

登录：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<your-password>"}'
```

用返回的令牌查询当前用户：

```bash
curl http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer <accessToken>"
```

对收货、调整、移库、分配和发运的重试必须复用相同 `Idempotency-Key`。详细请求见 [API 概览](api.md)。

## 9. 常见问题

### Compose 服务迟迟不健康

运行 `docker compose ps` 和 `docker compose logs <service>`。先确认 MySQL 健康，再检查 Java 服务的数据库、Flyway 和 JWT 配置。修改 `.env` 中的 MySQL 初始密码不会改变已有数据卷中的账号密码。

### 网关返回 502

确认 auth-service 和 warehouse-service 健康，并检查 `AUTH_SERVICE_URL`、`WAREHOUSE_SERVICE_URL`。当前未使用注册中心，地址不会自动发现。

### 登录成功后 gateway 或 warehouse-service 返回 401

确认三个服务的 `JWT_SECRET`、`JWT_ISSUER`、`JWT_AUDIENCE` 逐字一致，令牌未过期且请求头格式为 `Authorization: Bearer ...`。不要尝试伪造 `X-User-*`，网关会清理，仓储服务也会覆盖。

### 返回 403

检查用户当前角色和[角色矩阵](api.md#4-角色矩阵)。前端隐藏按钮不是授权依据，`RECEIVER` 不能调整库存，`PICKER` 不能执行入库。

### 登录返回 429

账号达到失败阈值后进入限时锁定。等待 `LOGIN_LOCK_MINUTES`，或由管理员通过用户管理重置密码/重新启用。不要在生产环境通过直接修改数据库解除锁定。

### Flyway 报 V2 missing/checksum

这通常表示数据库由旧版默认 V2 初始化，而当前构件已把演示 V2 移到 `db/demo`。停止启动并按第 6 节处理，不要直接 `repair`。

### 幂等键冲突

同一操作下复用键但请求内容不同会返回 `409`。若是原请求重试，应恢复原请求体；若是新的业务意图，应生成新键。

### Vite 提示 Node.js 版本不支持

升级到 Node.js 20.19+ 或 22.12+，删除旧 `node_modules`，再执行 `npm install`。

## 10. 生产检查清单

- 使用 `prod` Profile，warehouse-service 不启用 `demo`，`WAREHOUSE_SECURITY_ENABLED=true`。
- 三个服务共享唯一、随机、至少 32 字节的 JWT 密钥及相同 issuer/audience。
- 管理员使用强密码；不需要引导时关闭 `ADMIN_BOOTSTRAP_ENABLED`。
- 只公开 HTTPS 前端入口，后端服务与数据库位于私有网络。
- CORS 只允许正式前端域名；数据库账号遵循最小权限。
- 启用数据库备份、恢复演练、资源/费用上限、日志脱敏、健康检查和告警。
- 制定幂等记录清理、JWT 密钥轮换、依赖更新和 Flyway 变更审核流程。
