# AI Assistant (LiteRT-LM Multimodal)

A futuristic, high-performance Android AI assistant powered by **Google AI Edge LiteRT-LM**. This application runs large language models (like Gemma) entirely on-device, supporting both text and image-based multimodal conversations.

## 🚀 Features

- **Local LLM Execution**: Run models locally on your device for maximum privacy and offline availability.
- **Multimodal Support**: Analyze images from your gallery or take new photos to discuss them with the AI.
- **Hardware Acceleration**: Automatic fallback logic attempting GPU acceleration first, with CPU fallback for stability.
- **Futuristic UI**: A high-tech, "cyberpunk" inspired interface built with Jetpack Compose.
- **Shared Design System**: Modular `:ui-designsystem` library containing reusable high-tech components and theme.
- **Real-time Engine Status**: Detailed feedback during the neural engine initialization process.
- **Reliable Downloads**: Model management via WorkManager and Ktor for robust background downloading with atomic file swapping.

## 🛠 Tech Stack

- **UI**: Jetpack Compose
- **Local AI**: [LiteRT-LM (Google AI Edge)](https://ai.google.dev/edge/litert)
- **Architecture**: Clean Architecture + MVI (Model-View-Intent)
- **Dependency Injection**: Hilt
- **Networking**: Ktor + OkHttp
- **Database/Storage**: Firebase Firestore (for model metadata) & Firebase Storage (for model files)
- **Background Tasks**: WorkManager
- **Image Handling**: Coil & AndroidX Exifinterface

## 📋 Architecture & Modules

The project follows a decoupled architecture to ensure reliability and maintainability:

### 1. `:app` (Android Application)
- **Inference Layer** (`data/inference/`):
    - `LlmRuntimeManager`: Manages the LiteRT-LM engine lifecycle, hardware backends, and conversation sessions.
    - `MultimodalMessageFactory`: Centralizes image preprocessing and `Message` construction.
    - `LlmResponseMapper`: Safely extracts and maps model outputs.
- **Data Sources** (`data/datasource/`):
    - `ModelCatalogDataSource`: Interface for discovering available models.
    - `RemoteModelDataSource`: Interface for resolving URIs and performing file downloads.
- **Repository** (`ChatRepositoryImpl.kt`): A thin orchestration layer coordinating the inference runtime and data sources.

### 2. `:ui-designsystem` (Android Library)
Centralized source of truth for the app's visual identity:
- **Theme**: Custom Material3 theme with high-tech typography and colors.
- **Components**: Reusable elements like `HighTechPrimaryButton`, `ModelSelectorCard`, `StatCard`, and `AmbientGlow`.

## ⚙️ Setup & Configuration

### Option 1: Firebase Integration (Automatic Downloads)
1. **Firebase Project**: Add an Android app with package `com.neo.aiassistant` and place `google-services.json` in `app/`.
2. **Firestore**: Create a `models` collection with `name` (String) and `url` (String) fields.
3. **Storage**: Upload `.litertlm` files. The app supports both `gs://` and `https://` URLs.

### Option 2: Manual Sideloading
1. **Prepare Models**: Obtain `.litertlm` files (e.g., `gemma-4-E2B-it.litertlm`).
2. **Push to Device**:
   ```bash
   adb shell "run-as com.neo.aiassistant mkdir -p /data/data/com.neo.aiassistant/files"
   adb push your_model.litertlm /data/local/tmp/
   adb shell "run-as com.neo.aiassistant cp /data/local/tmp/your_model.litertlm /data/data/com.neo.aiassistant/files/"
   ```

## 🧠 Model Downloader Context

The `ModelDownloadWorker` is decoupled from specific cloud providers:
- **Abstract Data Source**: Uses `RemoteModelDataSource` for URL resolution and downloading.
- **Reactive Progress**: Uses Kotlin Flows to track byte-level download progress.
- **Atomic Persistence**: Downloads to a `.tmp` file and performs an atomic rename upon success.

## ⚙️ Hardware Requirements

- **GPU Acceleration**: Modern GPU with OpenCL/Vulkan support.
- **Memory**: 4GB+ RAM recommended for multimodal Gemma models.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
