# Firefly Logistics

Firefly Logistics 是一个面向中小型仓库的仓储物流管理系统。当前版本已经形成可运行的“入库 → 库存 → 出库”闭环，并加入多快递账号配置、凭证加密、连通测试、订单同步与同步日志，适合演示边远地区快递代理平台的核心后台流程。

## 已实现能力

- 登录、受控账号申请、管理员审批启用、当前用户查询、用户新增/编辑/启停和角色查询。
- `ADMIN`、`WAREHOUSE_MANAGER`、`RECEIVER`、`PICKER` 四类岗位权限；网关和仓储服务双层校验。
- JWT HS256 签名，同时校验 `issuer`、`audience`、有效期和唯一 `jti`。
- 连续登录失败计数和限时锁定，用户不存在时执行虚拟密码校验以降低时序泄露。
- 仓库、货位、SKU、供应商与客户基础资料。
- 入库建单、分批/整单收货、库存余额与不可变库存流水联动。
- 库存查询、调整、移库和库存流水分页检索。
- 出库建单、FEFO 库存分配、拣货、复核包装、取消释放、发运扣减与整单退货。
- 单库存维度盘点、认证审计、主动退出令牌撤销和 OpenAPI/Swagger UI。
- 写操作 `Idempotency-Key` 去重、库存维度悲观锁、单据状态机和数据库约束。
- 所有主要列表由数据库分页，管理端提供分页、筛选和角色感知菜单。
- 中通、圆通、韵达、申通、顺丰等快递账号统一管理；AES-GCM 加密凭证，支持手动/定时 Mock 同步、失败重试、MySQL 多实例租约、限频、熔断和同步日志。
- 新疆目的地演示运费试算、订单物流轨迹和按快递公司汇总的周期对账差异；Mock 规则与真实官方报价严格区分。
- 完整 Docker Compose、本机原生启动、Railway 云部署和 GitHub Actions CI。

## 技术栈

| 范围 | 主要技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Axios、Vitest |
| 网关与服务 | Java 17、Spring Boot 3.5.6、Spring Cloud 2025.0.3、Spring Cloud Gateway |
| 安全 | Spring Security、BCrypt、JWT HS256、AES-GCM、岗位 RBAC |
| 数据 | Spring Data JPA、Hibernate、Flyway、MySQL 8 |
| 交付 | Docker Compose、Caddy、Railway、GitHub Actions |
| 测试 | JUnit、Spring Boot Test、H2、Testcontainers、Vitest |

## 项目结构

```text
firefly-logistics/
├─ backend/
│  ├─ common/               通用响应、异常和 JWT 组件
│  ├─ gateway/              API 网关与鉴权授权，端口 8080
│  ├─ auth-service/         登录、用户与角色服务，端口 8081
│  └─ warehouse-service/    仓储核心服务，端口 8082
├─ frontend/                Vue 3 管理端
├─ deploy/
│  ├─ docker/               后端通用镜像构建文件
│  └─ railway/              无需本机 Docker 的 Railway 部署配置
├─ docs/                    架构、流程、数据、API 与开发文档
├─ scripts/                 Windows 原生一键启动与精确停止脚本
├─ .github/workflows/ci.yml 后端与前端持续集成
├─ start.cmd / stop.cmd     双击启动或停止本地 Demo
├─ docker-compose.yml       完整本地应用栈
└─ .env.example             环境变量模板
```

## 快速运行

### 方式一：完整 Docker Compose

环境要求为 Docker Compose v2。先复制配置并至少替换数据库密码和 JWT 密钥：

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
```

Windows PowerShell 使用：

```powershell
Copy-Item .env.example .env
docker compose up --build -d
```

默认启动 MySQL、认证服务、仓储服务、网关和前端；访问 `http://localhost:5173`。Redis 目前不是运行必需项，需要预留实例时使用 `docker compose --profile redis up --build -d`。

本地演示账号为 `admin` / `Firefly@123`。它只能用于个人开发环境。

### 方式二：本机没有 Docker

- 云端运行：按照 [Railway 部署指南](deploy/railway/README.md) 创建 MySQL 和四个应用服务，构建过程全部在云端完成。
- Windows 一键运行：准备可访问的 MySQL 8.0.16+，配置 `.env` 后双击根目录 `start.cmd`；ZIP 版 MySQL 可通过 `MYSQL_CLIENT_PATH`、`MYSQL_SERVER_PATH`、`MYSQL_CONFIG_FILE` 配置为按需启动。首次使用本机 MySQL 时，脚本会安全询问管理员凭据以创建数据库和本地业务用户。启动成功后自动打开 `http://127.0.0.1:5173`，双击 `stop.cmd` 精确停止本次记录的四个应用进程，MySQL 和业务数据不会被停止或删除。
- 手动原生运行：按照[开发与启动指南](docs/development.md)分别启动三个 Java 服务和前端。

一键脚本会检查 Java、Maven、Node、端口、MySQL 版本、数据库字符集和 Demo/Flyway 历史兼容性；所有应用只绑定 `127.0.0.1`，日志与 PID 状态写入已忽略的 `.firefly/runtime/`。它不会自动安装、升级、停止或删除 MySQL，也不会执行 `flyway repair`。检测到 MySQL 5.7、数据库连接配置互相矛盾或旧库不兼容时会在写入前停止；远程 MySQL 强制使用 TLS。

## 权限矩阵

| 能力 | `ADMIN` | `WAREHOUSE_MANAGER` | `RECEIVER` | `PICKER` |
| --- | :---: | :---: | :---: | :---: |
| 用户、角色管理 | ✓ |  |  |  |
| 仓储数据只读查询 | ✓ | ✓ | ✓ | ✓ |
| 快递账号配置与同步日志 | ✓ | ✓ |  |  |
| 聚合快递订单查询 | ✓ | ✓ | ✓ | ✓ |
| 基础资料、库存调整与移库 | ✓ | ✓ |  |  |
| 入库建单与收货 | ✓ | ✓ | ✓ |  |
| 出库建单、分配与发运 | ✓ | ✓ |  | ✓ |

权限由网关和仓储服务分别执行，前端菜单控制仅用于改善体验。认证服务再次保护用户管理接口；仓储服务直连也会校验 JWT，并用令牌声明覆盖 `X-User-*`。仍应只在内部网络开放后端端口。详细规则见[系统架构](docs/architecture.md)。

## 幂等与并发约定

前端会为写请求自动生成 `Idempotency-Key`。调用方重试创建入库/出库、收货、调整、移库、分配或发运时，应复用同一键；同一“操作＋键＋请求内容”返回首次结果，同一键携带不同内容返回 `409`。

库存事务采用确定顺序的悲观锁、余额 `version` 字段、唯一维度约束和单据状态校验。余额更新、流水追加与单据状态变化在同一事务提交。当前幂等记录保存在 MySQL，不依赖 Redis。

## 数据库迁移

- 认证服务：`V1` 创建用户表，`V2` 增加失败次数和锁定时间，`V3` 增加管理员集合并发保护锁。
- 仓储服务默认迁移：`V1` 创建核心结构，`V3` 增加幂等表和约束，`V4` 增加快递集成，`V5` 增加调度租约、重试/熔断状态并统一新疆演示数据。
- 演示数据仅由 `demo` Profile 加载：`db/demo/V2` 提供基础资料，可重复迁移 `R__seed_public_orders.sql` 提供 8 张 UCI CC BY 4.0 真实匿名零售订单及中通、圆通、韵达、申通、顺丰服务商资料；Railway `prod` 不播种演示数据。
- 两个服务使用独立的 Flyway 历史表；后启动的一方会写入版本 `0` 的基线标记，然后仍从 V1 执行自己的迁移。V0 不是业务迁移，也不会跳过 V1；迁移只能追加，不能修改已经执行的版本。

如果旧数据库已经登记原默认位置的仓储 V2，不要直接对其执行新构件或随意 `flyway repair`：应保留匹配旧历史的构件，或备份并迁移数据后重建新库，避免 missing/checksum 错误。详见[数据模型](docs/data-model.md)。

## 生产安全要求

- 使用 `prod` Profile，并为三个服务注入相同的唯一 `JWT_SECRET`（至少 32 字节）、`JWT_ISSUER`、`JWT_AUDIENCE`，为认证服务设置强 `ADMIN_PASSWORD`，为仓储服务单独设置唯一 `CARRIER_CREDENTIAL_KEY`（至少 32 字符）。生产 Profile 会拒绝文档中的开发密钥和默认管理员密码。
- 若管理员已通过外部流程创建，设置 `ADMIN_BOOTSTRAP_ENABLED=false`；任何环境都不得提交 `.env`、令牌或云端凭据。
- 只公开 HTTPS 前端入口；网关、认证、仓储和数据库走私有网络。
- 配置数据库最小权限账号、自动备份与恢复演练；生产仓储服务不得启用 `demo` Profile。
- 根据组织策略调整 `LOGIN_MAX_FAILURES`、`LOGIN_LOCK_MINUTES` 和 `JWT_TTL_HOURS`；在共享令牌撤销机制完成前，生产环境建议使用较短有效期（Railway 示例为 1 小时）。

## 构建与质量检查

```bash
cd backend
mvn clean verify

cd ../frontend
npm ci
npm run lint
npm test
npm run build
```

MySQL Testcontainers 迁移测试在 Docker 可用时自动执行，无 Docker 时由测试框架跳过。GitHub Actions 会在 `main`/`master` 推送和 Pull Request 上运行后端验证以及前端安装、Lint、测试和构建。

## 文档

- [系统架构](docs/architecture.md)：服务边界、JWT、RBAC、事务和部署拓扑。
- [业务流程](docs/business-flow.md)：入库、出库、库存并发、幂等与异常处理。
- [API 概览](docs/api.md)：用户接口、角色矩阵、分页与 `Idempotency-Key`。
- [数据模型](docs/data-model.md)：核心实体、库存账本、锁和 V3 迁移。
- [开发与启动](docs/development.md)：Compose、原生启动、验证和排障。
- [Railway 部署](deploy/railway/README.md)：本机无 Docker 时的云端运行入口。

## 路线图

多快递接入第三阶段的本地业务闭环已完成：在第二阶段定时同步与韧性保护之上，增加新疆目的地演示计价、订单轨迹和周期对账。当前报价与轨迹均为确定性 Mock 规则；接入官方沙箱仍需各快递公司授权、接口合同和独立凭证，不会把真实 Token 写入代码或版本库。
