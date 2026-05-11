# MaidBridge

MaidBridge 是一个面向 Touhou Little Maid 的 NeoForge 桥接 mod。它在 Minecraft / Touhou Little Maid 一侧提供 WebSocket 服务，把女仆聊天、女仆状态、动作能力和部分 Maid API 暴露给外部 agent。

MaidBridge 不绑定 MaiBot。MaiBot 的 `maibot-maid-adapter` 只是一个当前可用的外部 agent 实例；后续其他客户端只要遵守 `maidbridge.maid` 协议，也可以接入。

## 主要能力

- WebSocket 服务端：默认监听 `ws://127.0.0.1:8765/maidbridge`。
- 外部女仆 agent 回合：拦截 Touhou Little Maid 原生 `MaidAIChatManager.chat` 入口，把一轮女仆对话交给外部 agent。
- 女仆回合上下文：向外部 agent 发送女仆身份、说话者、玩家消息、TLM 被动状态、动作上下文、可执行动作摘要和 TLM 工具摘要。
- 回写执行：接收外部 agent 的 `maid.agent.turn.complete`，投递女仆回复、写入历史，并按配置执行 actions。
- Maid API 桥接：可选开放女仆查询、女仆动作调用、注册表查询、工具 schema 和工具调用。
- 调试报告：在游戏内提供 `/maidbridge` 系列命令查看 WebSocket、聊天链路和外部 agent 回合。

## 环境

- Java 21
- Minecraft `1.21.1`
- NeoForge `21.1.186`
- Touhou Little Maid `1.5.2+`
- Cloth Config，客户端配置界面需要
- `org.java-websocket:Java-WebSocket`，构建时通过 NeoForge Jar-in-Jar 打包

项目构建依赖相邻目录 `../TouhouLittleMaid`。运行 `compileJava`、`build`、`runClient` 或 `runServer` 时，Gradle 会先构建 Touhou Little Maid 的 runtime jar，并同步到 `run/mods/touhoulittlemaid-runtime.jar`。

## 构建与运行

在 `bridge/MaidBridge` 目录下执行：

```powershell
.\gradlew.bat compileJava
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

常用验证：

```powershell
.\gradlew.bat check
.\gradlew.bat verifyJarJarMetadata
```

## 关键配置

配置文件在 Minecraft 配置目录下生成：

```text
config/maidbridge-common.toml
```

常用开关：

```toml
bridgeServerEnabled = true
bridgeServerHost = "127.0.0.1"
bridgeServerPort = 8765
bridgeServerPath = "/maidbridge"
bridgeAccessToken = ""

enableExternalMaidAgentTurns = true
enableMaidMessageBridge = true
enableMaidApiExposure = true
enableMaidApiActions = false
enableExternalAgentEmoji = false

logCapturedEvents = false
captureRawLlmRequestBodies = false
```

说明：

- `bridgeServerEnabled` 控制 WebSocket 服务是否启动。
- `enableExternalMaidAgentTurns` 控制女仆原生 AIChat 是否交给外部 agent 接管。
- `enableMaidApiExposure` 只开放查询和注册表摘要。
- `enableMaidApiActions` 会允许外部请求修改女仆状态，默认应保持关闭，除非信任接入端。
- `bridgeAccessToken` 非空时，外部客户端需要使用相同的 Bearer Token。
- `captureRawLlmRequestBodies` 可能包含完整提示词，只有诊断时再打开。

## 外部 agent 协议概览

所有 WebSocket 帧都使用 JSON，稳定外壳字段为：

```json
{
  "protocol": "maidbridge.maid",
  "type": "maid.agent.turn.request",
  "id": "...",
  "request_id": "...",
  "trace_id": "...",
  "deadline_ms": 30000,
  "direction": "java_to_client",
  "source_endpoint": "maidbridge-java",
  "target_endpoint": "external-agent",
  "payload": {}
}
```

协议层只定义外壳和 `type`，业务语义放在 `payload`。当前不使用 `plane`，也不使用 `payload.ok`；成功响应由具体 response type 表示，失败统一走 `bridge.error`。

Java 发向外部 agent 的主要 type：

- `bridge.session.ready`
- `maid.agent.turn.request`
- `maid.message.out`
- `maid.api.response`
- `bridge.error`

外部 agent 发向 Java 的主要 type：

- `bridge.session.initialize`
- `maid.agent.turn.complete`
- `maid.message.in`
- `maid.api.query.maids`
- `maid.api.query.maid`
- `maid.api.query.registry`
- `maid.api.call.maid_action`
- `maid.api.query.maid_tool_schema`
- `maid.api.query.maid_context`
- `maid.api.call.maid_tool`

## 女仆 agent 回合

`maid.agent.turn.request` 的 `payload` 核心结构：

```json
{
  "turn_id": "maid-agent-turn-...",
  "request_id": "maid-agent-request-...",
  "maid": {
    "uuid": "...",
    "name": "..."
  },
  "speaker": {
    "uuid": "...",
    "name": "...",
    "language": "zh_cn",
    "description": []
  },
  "message": {
    "text": "玩家原话"
  },
  "state": {},
  "action_context": {},
  "actions": [],
  "tools": []
}
```

字段边界：

- `state` 表示 TLM 原生每轮会提供给 LLM 的被动事实状态。
- `action_context` 是 MaidBridge 为动作规划额外提供的上下文，例如附近实体。
- `actions` 是外部 agent 可以回写给 Java 执行的动作摘要。
- `tools` 是 TLM 原生工具摘要，目前只作为能力参考，不等同于 Python 侧可直接执行的工具。

外部 agent 完成回合时发送 `maid.agent.turn.complete`：

```json
{
  "protocol": "maidbridge.maid",
  "type": "maid.agent.turn.complete",
  "id": "...",
  "direction": "client_to_java",
  "payload": {
    "maid": {
      "uuid": "..."
    },
    "turn_id": "maid-agent-turn-...",
    "outcome": "reply",
    "reply": {
      "text": "女仆回复",
      "tts_text": "可选 TTS 文本"
    },
    "history": {
      "policy": "append"
    },
    "actions": []
  }
}
```

如果不回复，可以使用：

```json
{
  "payload": {
    "maid": {
      "uuid": "..."
    },
    "turn_id": "maid-agent-turn-...",
    "outcome": "no_reply",
    "reason": "..."
  }
}
```

## 调试命令

游戏内需要权限等级 2；重启 WebSocket 需要权限等级 3。

```text
/maidbridge
/maidbridge summary
/maidbridge ws
/maidbridge ws restart
/maidbridge chat 20
/maidbridge turns 20
```

命令用途：

- `/maidbridge summary`：查看 WebSocket 是否启用、是否运行、端点、客户端数量、活动 agent、传输计数和配置文件路径。
- `/maidbridge ws`：查看 WebSocket 客户端、订阅、排队帧和传输计数。
- `/maidbridge ws restart`：重新读取配置并重启 WebSocket 服务。
- `/maidbridge chat [limit]`：查看最近聊天链路事件。
- `/maidbridge turns [limit]`：查看待处理女仆 agent 回合和最近回合事件。

Java 日志关键字：

```text
MaidBridge WebSocket
maid.agent.turn.request
maid.agent.turn.complete
外部 agent
bridge.error
```

## 与 MaiBot 适配器配合

当前仓库内的 MaiBot 适配器位于：

```text
MaiBot/plugins/maibot-maid-adapter
```

适配器配置通常需要与 Java 端保持一致：

```toml
[maid_adapter]
enabled = true
enable_maid_agent_turns = true
server_uri = "ws://127.0.0.1:8765/maidbridge"
access_token = ""
enable_agent_actions = true
```

Java 端只负责发送女仆回合、校验回写并执行允许的动作；外部 agent 如何接入 LLM、如何插入自己的消息循环，由各自客户端实现。

## 开发边界

- 不直接修改 Touhou Little Maid 本体代码。
- 协议外壳保持稳定，新增语义优先放入 `payload`。
- 女仆原生 AIChat 被外部接管时，应避免再触发 TLM 原生 LLM 计费链路。
- 调试日志和捕获事件默认关闭，需要排查链路时再开启。
