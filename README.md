# Hive Delivery Graph POC

一个面向 AI 原生软件交付的最小可运行工程：

- Java 21 + Spring Boot 3.5.16
- LangGraph4j 1.8.20 固定 Control Graph
- YAML 动态加载 01～07 生命周期与阶段子图
- PostgreSQL 保存项目 Delivery Graph、TaskRun、Event
- Java 直接调用 OpenCode Server Session / `prompt_async` / Status / Message / Diff
- Vue 3 + Vue Flow 实时展示项目 Graph
- LangGraph4j Studio 展示固定控制图

## 架构边界

```text
YAML Template -> Project Delivery Graph (PostgreSQL)
LangGraph4j -> 每次处理一个确定性 Control Tick
OpenCode -> 单个 AGENT Task 内部 Plan + Build + Test
Vue Flow -> 展示项目真实 Delivery Graph
LangGraph4j Studio -> 展示固定 Control Graph
```

## 1. 环境

- JDK 21
- Maven 3.9+
- Node.js 20+
- Docker
- OpenCode（真实模式需要）

## 2. 启动 PostgreSQL

```bash
docker compose up -d
```

## 3. 启动 OpenCode

另开终端：

```bash
./scripts/start-opencode.sh
```

OpenCode 必须从 `workspace/product-search-demo` 目录启动，因为 Server 的工作区由启动目录决定。

需要 Basic Auth 时：

```bash
export OPENCODE_SERVER_PASSWORD=change-me
export OPENCODE_PASSWORD=change-me
./scripts/start-opencode.sh
```

## 4. 启动后端

```bash
mvn -pl backend spring-boot:run
```

不接 OpenCode、只看 Graph 流转：

```bash
HIVE_OPENCODE_MOCK=true mvn -pl backend spring-boot:run
```

## 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问：

- Delivery Graph：`http://localhost:5173`
- LangGraph4j Control Studio：`http://localhost:8080/?instance=delivery-control`
- OpenCode OpenAPI：`http://127.0.0.1:4096/doc`

## 6. 演示

1. 点击“创建演示项目”。
2. 点击“运行 / 继续”。
3. Stage 01 动态从 YAML 展开。
4. AGENT 节点创建 OpenCode Session 并异步执行。
5. Session idle 后 Reconcile Job 回收 Message/Diff，节点变为 COMPLETED。
6. 到 Human Gate 后在右侧点击批准。
7. 系统解锁并展开下一阶段，直到 07 完成。

## 当前 POC 边界

已经完成：生命周期 YAML、阶段动态展开、Graph 持久化、固定 LangGraph4j 控制图、OpenCode 异步调用、轮询补偿、SSE、Human Gate、可视化。

有意保留为下一步：CHANGE_REQUESTED 的 AI 影响分析和真正 Graph Patch。目前接口会持久化 Change Event，`GraphPatchService` 是下一扩展点。

## 注意

OpenCode Server API 会随版本演进。工程按 2026-07 官方 API 使用：`POST /session`、`POST /session/:id/prompt_async`、`GET /session/status`、`GET /session/:id/message`、`GET /session/:id/diff`。如果本机 OpenCode 版本响应 DTO 不同，以 `/doc` 为准调整 `HttpOpenCodeClient`。
