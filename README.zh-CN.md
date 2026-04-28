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
MODEL_BASE_URL=https://integrate.api.nvidia.com
MODEL_API_KEY=your_nvidia_api_key_here
MODEL_NAME=deepseek-ai/deepseek-v4-pro
MODEL_FALLBACK_NAMES=meta/llama-3.1-8b-instruct
MODEL_JSON_RESPONSE_FORMAT=false
MODEL_MAX_TOKENS=1200
MODEL_ATTEMPT_TIMEOUT_MS=45000
MODEL_REASONING_EFFORT=none
REQUEST_TIMEOUT_MS=240000
```

说明：

- NVIDIA NIM 的 `MODEL_BASE_URL` 使用 `https://integrate.api.nvidia.com`。
- `MODEL_NAME` 使用完整模型 ID：`deepseek-ai/deepseek-v4-pro`。
- `MODEL_FALLBACK_NAMES` 可选。主模型上游超时或失败时，会继续尝试这里列出的云端模型。
- `MODEL_JSON_RESPONSE_FORMAT=false` 用于兼容当前 NVIDIA DeepSeek 接口。
- `MODEL_ATTEMPT_TIMEOUT_MS=45000` 用于避免单次上游请求卡住整个 AI 任务。
- `MODEL_REASONING_EFFORT=none` 和 `MODEL_MAX_TOKENS=1200` 用于让 DeepSeek V4 Pro 走较低延迟模式。
- `MODEL_API_KEY` 是必填项，必须只保留在本地环境中。
- `REQUEST_TIMEOUT_MS=240000` 让代理等待更慢的 AI 生成，而不是返回本地兜底模板。

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

## 部署到 Render

仓库根目录已经提供 `render.yaml`，可以直接用 Render Blueprint 部署 `yingdao-ai-proxy`：

1. 把代码推到 GitHub，并在 Render 里选择 **New → Blueprint**。
2. 选择这个仓库，Render 会读取根目录的 `render.yaml`。
3. 创建时输入以下 `sync: false` 环境变量：

```text
MODEL_API_KEY=你的 NVIDIA API Key
APP_TOKEN=给移动端调用代理用的随机长字符串
```

Render 会自动配置：

- `HOST=0.0.0.0`，用于接收公网请求
- `MODEL_BASE_URL=https://integrate.api.nvidia.com`
- `MODEL_NAME=deepseek-ai/deepseek-v4-pro`
- `MODEL_FALLBACK_NAMES=meta/llama-3.1-8b-instruct`
- `MODEL_JSON_RESPONSE_FORMAT=false`
- `MODEL_MAX_TOKENS=1200`
- `MODEL_ATTEMPT_TIMEOUT_MS=45000`
- `MODEL_REASONING_EFFORT=none`
- `healthCheckPath=/health`
- `buildCommand=npm ci --include=dev && npm run build && npm prune --omit=dev`
- `startCommand=npm start`

部署成功后，先访问：

```text
https://你的-render-service.onrender.com/health
```

如果返回 `{"success":true,"data":{"status":"ok"},"error":null}`，说明服务已经在线。

### Android release 连接 Render

拿到 Render 的公网地址后，用 Gradle property 构建 release 包：

```bash
cd yingdao-android
./gradlew assembleRelease \
  -PYINGDAO_RELEASE_AI_BASE_URL=https://你的-render-service.onrender.com \
  -PYINGDAO_RELEASE_AI_APP_TOKEN=同一个_APP_TOKEN
```

`MODEL_API_KEY` 只放在 Render 环境变量里，不要写进 Android 包。Android 只需要 Render 服务地址和 `APP_TOKEN`。

## 开发说明

### Android 网络配置

- Android 模拟器中的 debug 构建通过 `http://10.0.2.2:8787` 访问宿主机代理
- release 构建通过 `YINGDAO_RELEASE_AI_BASE_URL` 和 `YINGDAO_RELEASE_AI_APP_TOKEN` 接入正式 AI 代理地址

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
