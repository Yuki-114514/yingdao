# YingDao

中文说明请见此文件；English version: [README.md](./README.md)

YingDao 是一个面向校园拍摄场景的移动端拍摄辅助项目，目前包含：

- 一个基于 Kotlin、Jetpack Compose 和 CameraX 的 Android 应用
- 一个基于 TypeScript、Fastify 和 Zod 的本地 AI 代理服务
- 一个仍在持续开发中的 iOS 代码库与 Swift Package

当前 Android 调试版已经配置为通过宿主机上的本地 AI 代理完成请求，因此可以在 Android 模拟器里跑通“拍摄/填写信息 → 生成导演方案”的完整链路。

## 仓库结构

```text
.
├── yingdao-android/    # Android 应用
├── yingdao-ai-proxy/   # 本地 AI 代理服务
├── yingdao-ios/        # iOS 工程与共享 Swift Package
└── README.zh-CN.md
```

这是主要目录的简化视图。

## 功能概览

### Android 应用

- 项目创建与拍摄工作流界面
- 基于 CameraX 的拍摄流程
- 通过本地代理请求 AI 生成导演方案
- 录制时底部固定的拍摄控制按钮，减少对取景的影响

### AI 代理服务

- 基于 Fastify 的 HTTP 服务
- 使用 Zod 做请求校验
- 支持 OpenAI-compatible 上游模型服务
- 可配置更长的请求超时，以支持耗时更久的导演方案生成

### iOS 工作区

- 包含共享核心模型与验证代码的 Swift Package
- iOS 端相机/结果工作流的实验性界面与工程结构

## 环境要求

### Android

- Android Studio
- Android SDK 35
- JDK 17
- Android 模拟器或真机

### AI 代理

- Node.js 20+
- npm
- 一个 OpenAI-compatible 上游服务地址

### iOS

- Xcode 16+
- Swift 6 工具链

## 快速开始

### 1. 启动 AI 代理服务

```bash
cd yingdao-ai-proxy
cp .env.example .env
npm install
npm run dev
```

启动前需要先配置 `.env`：

```env
HOST=127.0.0.1
PORT=8787
MODEL_PROVIDER=openai-compatible
MODEL_BASE_URL=http://127.0.0.1:8317
MODEL_API_KEY=your_cliproxyapi_token_here
MODEL_NAME=gpt-4o-mini
REQUEST_TIMEOUT_MS=120000
```

说明：

- `MODEL_BASE_URL` 需要指向你自己的上游 OpenAI-compatible 服务。
- `MODEL_API_KEY` 是必填项，必须只保留在本地环境中。
- `REQUEST_TIMEOUT_MS=120000` 是当前默认值，用于支持耗时更长的导演方案生成请求。

### 2. 运行 Android 应用

Android 调试版已经配置为在模拟器中通过宿主机访问本地代理：

- debug `AI_BASE_URL`: `http://10.0.2.2:8787`

可以通过 Android Studio 或 Gradle 构建：

```bash
cd yingdao-android
./gradlew assembleDebug
```

生成的调试安装包路径：

```text
yingdao-android/app/build/outputs/apk/debug/app-debug.apk
```

### 3. 运行测试

#### AI 代理测试

```bash
cd yingdao-ai-proxy
npm test
```

#### Android 单元测试

```bash
cd yingdao-android
./gradlew testDebugUnitTest
```

#### Swift Package 测试

```bash
cd yingdao-ios
swift test
```

## 开发说明

### Android 网络配置

- Android 模拟器中的 debug 构建通过 `http://10.0.2.2:8787` 访问宿主机代理
- release 构建目前还没有接入正式生产环境的 AI 地址

### 本地密钥与环境文件

仓库已忽略本地环境文件：

- `**/.env`
- `**/.env.*`
- `!**/.env.example`

不要把真实 API Key 或模型服务凭据提交到仓库。

### 当前状态

这个仓库目前仍处于持续迭代的原型阶段。Android 应用与本地 AI 代理已经构成当前主要的端到端链路；iOS 侧目前包含共享核心代码以及持续推进中的界面/流程探索。

## 许可证

当前仓库还没有显式许可证。如果后续要开源发布或允许第三方复用，建议补充明确的 License。
