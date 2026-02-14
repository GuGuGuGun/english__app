# 考研背单词助手（Android）

离线优先的考研英语背词应用，支持学习、查词、词书管理与 AI 增强（可选）。

## 功能概览

- 学习：认词/拼写双模式，SM2 复习调度。
- 查词：本地词典检索与详情展示。
- 词书：预置词书 + 自定义导入（TXT/CSV）。
- 我的：学习统计、备份恢复、学习设置。
- AI 实验室（可选）：
  - 服务商预设与自定义 OpenAI 兼容 Base URL。
  - 例句生成、助记生成、长句解析。
  - Cache First，减少重复请求与额度消耗。

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- Room + KSP
- Coroutines + Flow
- DataStore
- Retrofit + OkHttp
- Jetpack Security（API Key 本地加密存储）

## 环境要求

- JDK 17 或 21（推荐 21）
- Android SDK（Compile SDK 34）
- Gradle（项目未内置 `gradlew`）

## 快速开始

1. 在项目根目录创建 `local.properties`：

```properties
sdk.dir=E:\\android_sdk
```

2. 设置 JDK（PowerShell 示例）：

```powershell
$env:JAVA_HOME = "E:\java_21"
$env:Path = "$env:JAVA_HOME\\bin;" + $env:Path
```

3. 构建 APK：

```powershell
gradle --no-daemon :app:assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`

## 测试命令

```powershell
gradle :app:compileDebugAndroidTestKotlin --no-daemon
gradle connectedDebugAndroidTest --no-daemon
```

仅运行 M6 验收：

```powershell
gradle :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.kaoyan.wordhelper.M6AcceptanceTest" --no-daemon
```

## AI 配置说明

入口：`我的 -> AI 实验室`

- `Base URL` 填服务根地址（例如 `https://yunwu.ai/v1`），不要填到 `chat/completions`。
- `模型名称` 必须填写服务商实际可用模型 ID（例如 `gpt-5.2-chat-latest`）。
- `API Key` 仅本地加密存储。
- 点击“测试连接”校验连通性。

## 目录结构

```text
app/
  src/main/java/com/kaoyan/wordhelper/
    data/
    ui/
    util/
docs/
open/
```

## 说明

- 核心学习能力可离线运行。
- AI 功能需要联网与用户自备 API Key。
- AI 内容仅供学习参考，请结合教材自行甄别。
