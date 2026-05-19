# 考研星人守护 (Study & Sleep Guardian) V2.1

专为情侣考研设计的 Android 极简守护工具：**零后端账号、零数据库、零 BaaS 费用**。双端通过 **MQTT 公共 Broker** 实时结伴。

## 通信架构

| 项目 | 说明 |
|------|------|
| 协议 | MQTT 3.1.1 |
| Broker | `broker.emqx.io:1883`（免费公共节点） |
| 客户端 | [HiveMQ MQTT Client](https://github.com/hivemq/hivemq-mqtt-client) |
| 结对方式 | 两人输入**相同**「星际频道号」→ 订阅同一 Topic |
| 掉线检测 | **LWT（遗嘱消息）** + 15 分钟无心跳 |

详细 JSON 协议见 [`docs/MQTT_PROTOCOL.md`](docs/MQTT_PROTOCOL.md)。

## 快速开始

1. Android Studio 打开本目录，同步 Gradle。
2. 两台真机安装 APK（Android 8.0+）。
3. 约定一个复杂频道名（如 `Xingqiu_2026_LOVE`），**两台手机输入完全相同**。
4. 开启：使用情况访问、悬浮窗、电池白名单。

无需注册、无需 LeanCloud/MemFire、无需 AppKey。

## 功能对照

| 功能 | 实现 |
|------|------|
| 频道结对 | `OnboardingScreen` + `ChannelValidator` |
| 状态广播 | `MqttGuardianClient.publishStatus` ← `GuardianForegroundService` |
| 戳一下 / 延时 | MQTT `poke` / `request_delay` / `approve_delay` |
| 意外离线 | 连接时配置 LWT → `type: offline` |
| 15 分钟失联 | `startStalePartnerWatch` |
| 桌面组件 | `GuardianWidget`（读 MQTT 状态或本地缓存） |

## 项目结构

```
app/src/main/java/com/studyguardian/
├── data/mqtt/       # MqttGuardianClient、JSON 载荷
├── data/local/      # DataStore（频道号、device_id）
├── domain/          # 协调器、频道校验、睡眠延时
├── service/         # 前台守护、悬浮窗
└── ui/              # Compose 界面
```

## 隐私与安全提示

- 公共 Broker **不加密**（1883 明文），频道号务必足够复杂、仅两人知晓。
- 任何人猜到频道名即可订阅，请勿使用生日、手机号等弱口令。
- 若需更高安全性，可自建 Mosquitto 并改用 TLS（8883）。

## 已从 V2.0 移除

- LeanCloud / MemFire 及全部 REST/SDK
- `Users` / `Interactions` 表与用户注册体系
- 6 位邀请码单向绑定逻辑

---

Made with 💕 — 无服务器，只有你们的专属频道。
