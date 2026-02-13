# 考研单词助手（Android）

一个面向考研英语的离线背单词应用，支持认词、拼写、词书管理与学习统计。

## 项目简介

本项目强调本地优先与离线可用，核心学习流程无需依赖外部服务。

- 认词模式：单词卡片 + 间隔复习调度
- 拼写模式：主动回忆输入练习
- 词书管理：内置词书、导入导出、切换当前词书
- 搜索：本地词条检索与详情查看
- 我的：学习数据、备份恢复、学习设置

## 技术栈

- Kotlin
- Jetpack Compose（Material 3）
- Room + KSP
- Coroutines + Flow
- DataStore
- Navigation Compose

## 环境要求

- JDK 17
- Android SDK（Compile SDK 34）
- Gradle（当前 `open/` 目录不含 Gradle Wrapper）

## 快速开始

1. 在项目根目录创建 `local.properties`：

```properties
sdk.dir=E:\\android_sdk
```

2. 设置 JDK（PowerShell 示例）：

```powershell
$env:JAVA_HOME = "E:\java_17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

3. 构建 Debug 安装包：

```powershell
gradle :app:assembleDebug --no-daemon
```

安装包输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 测试命令

编译 AndroidTest：

```powershell
gradle :app:compileDebugAndroidTestKotlin --no-daemon
```

运行连接设备测试（需真机/模拟器）：

```powershell
gradle connectedDebugAndroidTest --no-daemon
```

仅运行 M6 验收测试：

```powershell
gradle :app:connectedDebugAndroidTest "--Pandroid.testInstrumentationRunnerArguments.class=com.kaoyan.wordhelper.M6AcceptanceTest" --no-daemon
```

## 目录结构

```text
app/
  src/main/java/com/kaoyan/wordhelper/
    data/          # Room 实体、DAO、仓库层
    ui/            # Compose 页面、导航、组件
    util/          # 调度、解析、日期、备份等工具
  src/androidTest/ # 仪器化与 UI 测试
docs/              # 产品与开发文档
```

## 开源说明

- 项目核心功能可完全离线运行。
- 学习数据默认保存在本地设备。
- 不依赖后端服务即可完成主要学习流程。

## 许可证

本项目使用 **Apache License 2.0** 开源协议。

- 许可证文件：`LICENSE`
- 在线文本：https://www.apache.org/licenses/LICENSE-2.0

## 参与贡献

欢迎提交 Issue 与 Pull Request。

建议流程：

1. Fork 仓库
2. 创建功能分支
3. 提交代码与必要测试
4. 发起 PR 并说明变更动机与影响

