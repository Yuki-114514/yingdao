# YingDao

中文说明： [README.zh-CN.md](./README.zh-CN.md)

YingDao is a mobile filming assistant project for campus-style shooting workflows. It currently includes:

- an Android app built with Kotlin, Jetpack Compose, and CameraX
- a local AI proxy service built with TypeScript, Fastify, and Zod
- an iOS codebase and shared Swift package under active development

The current Android debug build talks to the local AI proxy on the host machine, which makes it practical to test the full capture → director-plan flow from an Android emulator.

## Repository structure

```text
.
├── yingdao-android/    # Android app
├── yingdao-ai-proxy/   # Local AI proxy service
├── yingdao-ios/        # iOS app and shared Swift package
└── README.md
```

This is an abbreviated view of the main project directories.

## Features

### Android app

- project creation and filming workflow screens
- CameraX-based capture flow
- AI-generated director plan requests through the local proxy
- bottom-anchored recording control optimized for capture usability

### AI proxy

- Fastify HTTP server
- request validation with Zod
- OpenAI-compatible upstream provider support
- configurable upstream timeout for longer AI generation tasks

### iOS workspace

- Swift package for shared core models and verification code
- iOS project scaffolding and camera/result workflow experiments

## Requirements

### Android

- Android Studio with Android SDK 35
- JDK 17
- Android emulator or device

### AI proxy

- Node.js 20+
- npm
- an OpenAI-compatible upstream endpoint

### iOS

- Xcode 16+
- Swift 6 toolchain

## Quick start

### 1. Start the AI proxy

```bash
cd yingdao-ai-proxy
cp .env.example .env
npm install
npm run dev
```

Configure `.env` before starting the server:

```env
HOST=127.0.0.1
PORT=8787
MODEL_PROVIDER=openai-compatible
MODEL_BASE_URL=https://integrate.api.nvidia.com
MODEL_API_KEY=your_nvidia_api_key_here
MODEL_NAME=deepseek-ai/deepseek-v4-pro
MODEL_JSON_RESPONSE_FORMAT=false
REQUEST_TIMEOUT_MS=120000
```

Notes:

- NVIDIA NIM uses `https://integrate.api.nvidia.com` for `MODEL_BASE_URL`.
- `MODEL_NAME` must use the full model ID: `deepseek-ai/deepseek-v4-pro`.
- `MODEL_JSON_RESPONSE_FORMAT=false` keeps requests compatible with the current NVIDIA DeepSeek V4 Pro API.
- `MODEL_API_KEY` is required and must stay local.
- `REQUEST_TIMEOUT_MS=120000` is the current default to allow longer director-plan generations.

### 2. Run the Android app

The Android debug build is already configured to use the host machine proxy from the emulator:

- debug `AI_BASE_URL`: `http://10.0.2.2:8787`

Run from Android Studio or Gradle:

```bash
cd yingdao-android
./gradlew assembleDebug
```

A generated debug APK will be placed at:

```text
yingdao-android/app/build/outputs/apk/debug/app-debug.apk
```

### 3. Run tests

#### AI proxy

```bash
cd yingdao-ai-proxy
npm test
```

#### Android unit tests

```bash
cd yingdao-android
./gradlew testDebugUnitTest
```

#### Swift package tests

```bash
cd yingdao-ios
swift test
```

## Deploy to Render

The repository root now includes `render.yaml`, so the AI proxy can be deployed as a Render Blueprint:

1. Push the repository to GitHub, then choose **New → Blueprint** in Render.
2. Select this repository. Render will read `render.yaml` from the repo root.
3. During creation, fill in these `sync: false` environment variables:

```text
MODEL_API_KEY=your NVIDIA API key
APP_TOKEN=a long random token used by the mobile app
```

Render configures the rest:

- `HOST=0.0.0.0` for public traffic
- `MODEL_BASE_URL=https://integrate.api.nvidia.com`
- `MODEL_NAME=deepseek-ai/deepseek-v4-pro`
- `MODEL_JSON_RESPONSE_FORMAT=false`
- `healthCheckPath=/health`
- `buildCommand=npm ci --include=dev && npm run build && npm prune --omit=dev`
- `startCommand=npm start`

After the deploy succeeds, verify:

```text
https://your-render-service.onrender.com/health
```

The expected response is `{"success":true,"data":{"status":"ok"},"error":null}`.

### Connect Android release builds

After you have the Render URL, build a release APK with Gradle properties:

```bash
cd yingdao-android
./gradlew assembleRelease \
  -PYINGDAO_RELEASE_AI_BASE_URL=https://your-render-service.onrender.com \
  -PYINGDAO_RELEASE_AI_APP_TOKEN=the_same_APP_TOKEN
```

Keep `MODEL_API_KEY` only in Render environment variables. Android needs only the Render service URL and `APP_TOKEN`.

## Development notes

### Android networking

- debug builds use `http://10.0.2.2:8787` to reach the host machine from the Android emulator
- release builds use `YINGDAO_RELEASE_AI_BASE_URL` and `YINGDAO_RELEASE_AI_APP_TOKEN` for the production AI proxy endpoint

### Local secrets

The repository ignores local environment files:

- `**/.env`
- `**/.env.*`
- `!**/.env.example`

Do not commit real API keys or provider credentials.

### Current status

This repository is an actively evolving prototype. The Android app and local proxy currently provide the main end-to-end flow. The iOS side contains shared core code and ongoing UI/workflow exploration.

## License

This repository currently has no explicit license. Add one before open-source distribution or third-party reuse.
