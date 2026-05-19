# MQTT 通信协议

## 连接

- **Broker**: `broker.emqx.io`
- **Port**: `1883`（TCP 明文）
- **Client ID**: `sg_{deviceId}_{random}`（每次连接唯一）

## Topic

两人约定频道号 `CHANNEL`（8–64 位，`[a-zA-Z0-9_-]`），实际 Topic：

```
studyguardian/{CHANNEL}
```

双方 **Subscribe + Publish** 同一 Topic。

## 连接参数

- **Keep Alive**: 60s
- **LWT（遗嘱）**: 意外断线时 Broker 自动发布：

```json
{
  "device_id": "本机16位ID",
  "type": "offline",
  "state": "offline",
  "timestamp": 1710000000000
}
```

- QoS: 1（AT_LEAST_ONCE）
- Retain: false

## 消息体（JSON）

```json
{
  "device_id": "a1b2c3d4e5f6g7h8",
  "type": "status",
  "state": "study",
  "screen_on": true,
  "charging": false,
  "foreground_app": "com.example.app",
  "timestamp": 1710000000000,
  "payload": null
}
```

### type 枚举

| type | 说明 |
|------|------|
| `status` | 状态心跳（亮灭屏、充电、前台 App、study/play/sleep） |
| `poke` | 爱心戳戳 → 对方霸屏 + 震动 |
| `request_delay` | 撒娇申请延时，`payload` 为分钟数 |
| `approve_delay` | 同意延时 |
| `offline` | 离线（LWT 或主动退出前发布） |

### state 枚举

`study` | `play` | `sleep` | `offline`

## 接收规则

1. 忽略 `device_id` 与本机相同的报文。
2. 根据对方最新消息更新 UI / 小组件。
3. `timestamp` 超过 15 分钟未更新 → 显示「星人信号丢失中 📡」。

## 示例：戳一下

发布到 `studyguardian/Xingqiu_2026_LOVE`：

```json
{
  "device_id": "a1b2c3d4e5f6g7h8",
  "type": "poke",
  "timestamp": 1710000000000
}
```
