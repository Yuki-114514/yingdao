# YingDao

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
MODEL_BASE_URL=http://127.0.0.1:8317
MODEL_API_KEY=your_cliproxyapi_token_here
MODEL_NAME=gpt-4o-mini
REQUEST_TIMEOUT_MS=120000
```

Notes:

- `MODEL_BASE_URL` must point to your upstream OpenAI-compatible service.
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

## Development notes

### Android networking

- debug builds use `http://10.0.2.2:8787` to reach the host machine from the Android emulator
- release builds are not yet wired to a production AI endpoint

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
