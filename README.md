# 考研星人守护 (Study & Sleep Guardian) V2.0

专为情侣考研设计的 Android 极简守护与陪伴工具：**零商业收费、数据绝对隐私**，用底层系统 API 实现温柔克制的互相陪伴。

## 技术栈

| 层级 | 选型 |
|------|------|
| 客户端 | Kotlin + Jetpack Compose |
| 后端中转 | MemFire Cloud / LeanCloud（REST，见 `BaasRepository`） |
| 桌面组件 | Glance App Widget |
| 守护 | Foreground Service + UsageStats + 悬浮窗 |

## 快速开始

1. 用 **Android Studio Ladybug+** 打开本目录。
2. 在 [MemFire](https://memfiredb.com/) 或 [LeanCloud](https://www.leancloud.cn/) 创建应用，建表见 [`docs/BAAS_SCHEMA.md`](docs/BAAS_SCHEMA.md)。
3. 在 `app/build.gradle.kts` 的 `defaultConfig` 中填写：

```kotlin
buildConfigField("String", "BAAS_APP_ID", "\"你的 AppId\"")
buildConfigField("String", "BAAS_APP_KEY", "\"你的 AppKey\"")
buildConfigField("String", "BAAS_SERVER_URL", "\"https://xxx.api.lncldglobal.com\"")
```

4. 真机安装（需 Android 8.0+），按引导开启：**使用情况访问、悬浮窗、电池白名单**。

## 功能对照

| 模块 | 实现位置 |
|------|----------|
| 6 位邀请码结对 | `OnboardingScreen` + `InviteCodeGenerator` |
| 专注 / 摸鱼检测 | `UsageMonitor` + `StudyWhitelist` |
| 爱心戳戳霸屏 | `OverlayService` + `HapticHelper` |
| 睡眠判定（充电+熄屏） | `SleepMonitor` |
| 晚安遮罩 & 撒娇延时 | `OverlayService` + `SleepDelayHandler` |
| 心疼机制（3 分钟无回应） | `SleepDelayHandler` |
| 15 分钟离线灰显 | `BaasRepository.fetchPartnerStatus` + Widget |
| 临时豁免 3 分钟 | 霸屏「临时豁免」按钮 |
| 前台保活 | `GuardianForegroundService` |

## 项目结构

```
app/src/main/java/com/studyguardian/
├── data/          # DataStore、BaaS、模型
├── domain/        # 白名单、结对、协调器
├── monitor/       # 使用统计 & 睡眠
├── service/       # 前台服务 & 悬浮窗
├── ui/            # Compose 界面 & 主题
├── widget/        # 桌面小组件
└── util/          # 通知、震动、权限
```

## 隐私说明

- 仅上报：`uid`、`partner_uid`、`current_state`、`last_update_time` 及交互指令。
- 不采集聊天、相册、通讯录；白名单可自行在 `StudyWhitelist.kt` 调整。

## 后续可增强

- LeanCloud / MemFire **Live Query** 替代 8 秒轮询
- 设置页自定义就寝时间、白名单
- 双方对称的「同意延时」推送 Action

---

Made with 💕 for 考研情侣 — 互相守护，不互相施压。
