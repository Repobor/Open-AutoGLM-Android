# AutoGLM - AI驱动的Android自动化助手

用自然语言控制手机！AutoGLM是一个基于AI的Android自动化工具，支持语音指令、自动点击、滑动、输入等操作。

[English](#english) | 中文

---

## 快速开始

### 1️⃣ 安装

```bash
# 构建APK
cd AutoGLM
./gradlew assembleDebug  # Linux/Mac/Git Bash
# 或
gradlew.bat assembleDebug  # Windows CMD/PowerShell

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2️⃣ 配置（首次启动）

1. 打开应用 → 进入**设置**页面
2. 配置以下信息：
   - **API端点**: 如 `http://192.168.1.100:8000`
   - **API密钥**: 可选
   - **模型名称**: 如 `autoglm-phone-9b`
   - **语言**: 中文或英文

3. 点击**启用无障碍服务** → 在系统设置中启用AutoGLM

### 3️⃣ 开始使用

1. 切换到**任务**页面
2. 输入自然语言指令，如：
   - "打开微信"
   - "在淘宝搜索iPhone"
   - "给张三发消息说你好"
   - "打开Chrome浏览器"

3. 点击**开始任务** → AI自动分析屏幕并执行操作

---

## 核心功能

| 功能 | 描述 |
|------|------|
| 🤖 **AI驱动** | 理解自然语言，智能决策 |
| 📱 **手机控制** | 点击、滑动、输入、启动应用 |
| 📸 **屏幕分析** | 实时截图分析当前状态 |
| 🌐 **双语支持** | 中文和英文界面 |
| 📊 **实时日志** | 显示任务执行进度 |
| ⏸️ **随时停止** | 点击"停止"立即中断任务 |

---

## 系统要求

- ✅ Android 11 (API 30+)
- ✅ 屏幕截图需要 Android 11 (API 30+)
- ✅ 启用无障碍服务
- ✅ OpenAI兼容的API服务

---

## 支持的操作

模型可以执行以下动作（自动生成坐标0-999）：

```
• Tap(点击)          示例: Tap at [500, 300]
• Type(输入文本)     示例: Type "iPhone"
• Swipe(滑动)        示例: Swipe up/down/left/right
• Launch(启动应用)   示例: Launch WeChat
• Back(返回)         示例: Press back button
• Home(首页)         示例: Go home
• Long Press(长按)   示例: Long press 2 seconds
• Double Tap(双击)   示例: Double tap
• Wait(等待)         示例: Wait 3 seconds
• Finish(完成)       示例: Task finished
```

---

## API配置

AutoGLM需要OpenAI兼容的API服务支持视觉功能。

### 本地部署示例

使用开源模型服务（如vLLM、Ollama等）：

```bash
# vLLM示例
python -m vllm.entrypoints.openai.api_server \
  --model autoglm-phone-9b \
  --port 8000
```

### 测试连接

在设置页面点击**测试连接**验证API配置。

---

## 常见问题

### ❓ 运行一步就没有反应了？
- 需要设置省电策略为**无限制**

### ❓ 无法启用无障碍服务？
- 确保应用已安装
- Settings → Accessibility → 找到AutoGLM并启用
- 某些设备需要在安全设置中允许安装

### ❓ 点击位置不准确？
- 检查设备分辨率是否正确配置
- 坐标范围是0-999（归一化）

### ❓ API连接失败？
检查：
- API服务是否运行中
- 网络连接是否正常
- IP地址和端口是否正确
- 防火墙是否阻止

### ❓ 支持新应用？
在 `config/AppPackages.kt` 中添加应用名称→包名的映射即可。

---

## 项目结构

```
app/src/main/java/io/repobor/autoglm/
├── agent/              # AI编排器
├── model/              # API客户端
├── actions/            # 动作执行
├── accessibility/      # 无障碍服务
├── config/             # 应用配置
├── data/               # 数据持久化
└── ui/                 # Compose UI
```

---

## 调试

### 查看日志

```bash
# 所有相关日志
adb logcat | grep -E "PhoneAgent|ModelClient|ShizukuHelper|GestureExecutor"

# 特定模块
adb logcat -s PhoneAgent          # AI编排
adb logcat -s GestureExecutor     # 手势执行
adb logcat -s ModelClient         # API调用
adb logcat -s ScreenshotCapture   # 截图
```

### 构建命令

```bash
./gradlew clean            # 清理
./gradlew test             # 运行测试
./gradlew assembleRelease  # 发布版本
```

---

## 技术栈

| 组件 | 技术 |
|------|------|
| UI框架 | Jetpack Compose + Material3 |
| 架构 | MVVM + ViewModel |
| 并发 | Kotlin Coroutines + Flow |
| 网络 | OkHttp + Kotlin Serialization |
| 数据存储 | DataStore Preferences |

---

## 安全提示 ⚠️

- 本应用具有完整的手机控制权限，请谨慎使用
- 不要授权给不信任的应用或服务
- 保护好API密钥，不要公开分享
- 执行敏感操作（支付、删除等）前会提示确认

---

## 贡献

欢迎提交Issue和Pull Request！

## 许可证

MIT License

## 致谢

本项目基于 [Open-AutoGLM](https://github.com/zai-org/Open-AutoGLM) 迁移而来。

---

## 更新日志

**v1.0** (2025-12-18)
- ✅ 完成项目迁移到原生Android
- ✅ 修复Binder截图溢出问题
- ✅ 修复Type动作输入框清空问题
- ✅ 实现立即任务停止功能
- ✅ 修复ANR应用无响应问题

---

<a name="english"></a>

# AutoGLM - AI-Powered Android Automation Assistant

Control your phone with natural language! AutoGLM is an AI-based Android automation tool that supports voice commands, automatic clicking, swiping, typing, and more.

## Quick Start

### 1️⃣ Install

```bash
cd AutoGLM
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2️⃣ Configure (First Launch)

1. Open app → Settings page
2. Configure:
   - API Endpoint: `http://192.168.1.100:8000`
   - Model Name: `autoglm-phone-9b`
   - Language: English

3. Click "Enable Accessibility Service"

### 3️⃣ Start Automating

1. Go to Task page
2. Enter commands like:
   - "Open Chrome"
   - "Search for iPhone on Taobao"
   - "Send message to John"

3. Click "Start Task"

---

## Features

| Feature | Description |
|---------|-------------|
| 🤖 **AI-Powered** | Understand natural language |
| 📱 **Phone Control** | Click, swipe, type, launch apps |
| 📸 **Screen Analysis** | Real-time screenshot analysis |
| 🌐 **Multi-Language** | English & Chinese support |
| 📊 **Live Logging** | Show task progress |
| ⏸️ **Stop Anytime** | Instant task cancellation |

---

## System Requirements

- ✅ Android 11+ (API 30+)
- ✅ Accessibility Service enabled
- ✅ OpenAI-compatible API

---

## Supported Actions

```
• Tap              示例: Tap at [500, 300]
• Type             示例: Type "text"
• Swipe            示例: Swipe up/down
• Launch           示例: Launch app
• Back/Home        示例: Navigation
• Long Press       示例: Press 2s
• Double Tap       示例: Double tap
• Wait             示例: Wait 3s
• Finish           示例: Done
```

---

## FAQ

### ❓ Why does it freeze after just one step?
- You need to set the power saving policy to **Unrestricted**.

### ❓ Accessibility Service not working?
- Settings → Accessibility → Enable AutoGLM
- Restart the app

### ❓ API connection failed?
- Check service is running
- Verify IP and port
- Check firewall settings

### ❓ Screenshot not working?
- Requires Android 11+
- Check device settings

---

## Tech Stack

- UI: Jetpack Compose + Material3
- Architecture: MVVM
- Concurrency: Kotlin Coroutines
- Networking: OkHttp

---

## License

MIT

---

**Last Updated**: 2025-12-18
**Version**: 1.0
**Status**: ✅ Production Ready
