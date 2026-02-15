# 考研背单词助手（Android）

离线优先的考研英语背词应用，覆盖学习、查词、词书管理与 AI 增强能力（可选）。

## 功能特性

- 学习模块：认词/拼写双模式，基于 SM2 进行复习调度。
- 查词模块：本地词典检索与词条详情展示。
- 词书模块：预置词书 + 自定义导入（TXT/CSV，兼容 maimemo-export 的 `word,meaning` 连续流格式）。
- 我的模块：学习统计、备份恢复、学习设置。
- AI 实验室（可选）：
  - 支持预设服务商与自定义 OpenAI 兼容 `Base URL`。
  - 支持例句生成、助记生成、长句解析。
  - Cache First，降低重复请求与额度消耗。

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- Room + KSP
- Coroutines + Flow
- DataStore
- Retrofit + OkHttp
- Jetpack Security（API Key 本地加密存储）

## 环境要求

| 项目 | 要求 |
| --- | --- |
| JDK | 17 或 21（推荐 21） |
| Android SDK | Compile SDK 34 / Target SDK 34 / Min SDK 26 |
| 构建工具 | Gradle（当前仓库未内置 `gradlew`） |

## 快速开始

1. 在项目根目录创建 `local.properties`（指向本机 Android SDK）：

```properties
sdk.dir=E:\\android_sdk
```

2. 设置 JDK（PowerShell 示例）：

```powershell
$env:JAVA_HOME = "E:\\java_21"
$env:Path = "$env:JAVA_HOME\\bin;" + $env:Path
```

3. 构建 Debug APK：

```powershell
gradle --no-daemon :app:assembleDebug
```

输出文件：`app/build/outputs/apk/debug/app-debug.apk`

## 运行与测试

编译 AndroidTest：

```powershell
gradle :app:compileDebugAndroidTestKotlin --no-daemon
```

执行全部连接测试（需设备或模拟器）：

```powershell
gradle connectedDebugAndroidTest --no-daemon
```

仅执行 M6 验收测试：

```powershell
gradle :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.kaoyan.wordhelper.M6AcceptanceTest" --no-daemon
```

## AI 功能配置（可选）

入口：`我的 -> AI 实验室`

- `Base URL`：填写服务根地址（例如 `https://yunwu.ai/v1`），不要填写到 `chat/completions`。
- `模型名称`：填写服务商实际可用模型 ID（例如 `gpt-5.2-chat-latest`）。
- `API Key`：仅在本地加密存储，不上传到项目仓库。
- 可通过“测试连接”验证配置是否生效。

## 项目结构

```text
app/
  src/main/java/com/kaoyan/wordhelper/
    data/      # 数据层（数据库、仓储、网络）
    ui/        # Compose 界面与状态管理
    util/      # 工具类与通用能力
  src/test/         # 单元测试
  src/androidTest/  # 仪器/界面测试
gradle/             # 版本目录与构建配置
```

## 更新日志
### 2026-02-15
- 调整：认词模式下 AI 入口改为独立新页面，例句生成与助记技巧统一在该页面展示（从灯泡进入）。
- 优化：查词页长难句解析分区卡片支持内部滚动，避免解析内容过长被遮挡。
- 修复：真实 maimemo-export 样本中“释义含换行”被拆成多词的问题，新增引号内换行与续行合并解析，并补充单元测试覆盖。

### 2026-02-14
- 新增：AI 基础设施与“AI 实验室”入口（BYOK 配置、连通性测试、缓存表与安全存储）。
- 新增：学习页 AI 例句/助记入口，查词页长难句解析（输入超过 `20` 字符自动触发）。
- 新增：词书导入兼容 maimemo-export 样式 CSV（`word,meaning` 连续词流，支持引号与释义内逗号）。
- 优化：AI 卡片统一“AI 生成”标识、风险提示入口与免责声明展示。
- 优化：AI 请求弱网治理（超时/域名解析/连接失败/`429`/`5xx` 一次重试）与错误提示映射。
- 测试：补齐 AI 相关单元测试、仪器测试与 UI 测试，并产出 Debug 包。

### 2026-02-13- 修复：切换到自定义词库后，在认词模式点击“`不认识`”导致“还剩单词个数”反而增加的问题。
- 新增：跨词库已学习进度同步。导入或补充词库后，若存在同词条历史学习记录，会自动同步为已学习状态。

## 许可证

本项目基于 `LICENSE` 文件进行授权。

## 说明与边界

- 核心学习能力可离线运行。
- AI 功能需要联网并由用户自备 API Key。
- AI 生成内容仅供学习参考，请结合教材自行甄别。
