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

The project is split into two main modules to promote reusability and clean separation of concerns:

### 1. `:app` (Android Application)
Follows Clean Architecture principles:
- **Domain Layer**: Pure Kotlin business logic and repository interfaces.
- **Data Layer**: Concrete implementations of repositories, managing the LiteRT-LM engine lifecycle and model downloads.
- **UI Layer**: MVI-based ViewModels and main screen Compose components (found in `ChatComponents.kt`).

### 2. `:ui-designsystem` (Android Library)
Centralized source of truth for the app's visual identity:
- **Theme**: Custom Material3 theme with high-tech typography and colors.
- **Components**: Reusable elements like `HighTechPrimaryButton`, `ModelSelectorCard`, `StatCard`, and `AmbientGlow`.

## ⚙️ Setup & Configuration

### Option 1: Firebase Integration (Automatic Downloads)
If you want the app to handle model downloads automatically from your own cloud storage:

1. **Firebase Project**:
   - Create a new project in the [Firebase Console](https://console.firebase.google.com/).
   - Add an Android app with the package name `com.neo.aiassistant`.
   - Download the `google-services.json` and place it in the `app/` directory.
2. **Firestore Configuration**:
   - Enable Cloud Firestore.
   - Create a collection named `models`.
   - Add documents for your models with the following fields:
     - `name`: String (e.g., `gemma-4-E2B-it.litertlm`)
     - `url`: String (either a `gs://` path or a direct `https://` link).
3. **Storage Configuration**:
   - Enable Firebase Storage.
   - Upload your `.litertlm` model files. If using `gs://` URLs, ensure your `google-services.json` is correctly configured to allow the app to resolve these paths.

### Option 2: Manual Sideloading (No Firebase Required)
If you prefer to bypass Firebase and provide the models manually via ADB:

1. **Prepare Models**:
   - Obtain the `.litertlm` model files.
   - Ensure they are named `gemma-4-E2B-it.litertlm` or `gemma-4-E4B-it.litertlm`.
2. **Push to Device**:
   - Use ADB to push the model to a temporary location and then move it to the app's private directory:
     ```bash
     adb push your_model_file.litertlm /data/local/tmp/
     adb shell "run-as com.neo.aiassistant cp /data/local/tmp/your_model_file.litertlm /data/data/com.neo.aiassistant/files/"
     ```
3. **Launch App**:
   - The app checks for these files on startup. If found, it will initialize the LiteRT-LM engine immediately.

## 📦 Downloading Models from Hugging Face

You can download official LiteRT-LM compatible models (like Gemma) from Hugging Face.

### Official Repositories
- **Gemma 2B IT (LiteRT)**: [google/gemma-2b-it-litert](https://huggingface.co/google/gemma-2b-it-litert)
- **Gemma 4B IT (LiteRT)**: [google/gemma-4b-it-litert](https://huggingface.co/google/gemma-4b-it-litert)
- **Gemma 2 2B (LiteRT)**: [google/gemma-2-2b-it-litert](https://huggingface.co/google/gemma-2-2b-it-litert)

### How to download using Hugging Face CLI
If you want to download these models via command line for sideloading:

1. **Install HF CLI**:
   ```bash
   pip install -U "huggingface_hub[cli]"
   ```
2. **Login** (Required for gated models like Gemma):
   ```bash
   huggingface-cli login
   ```
3. **Download Model**:
   ```bash
   # Example: Download Gemma 2B IT
   huggingface-cli download google/gemma-2b-it-litert --local-dir ./models --include "*.litertlm"
   ```
4. **Rename and Sideload**:
   Once downloaded, rename the file to match the app's expected names (e.g., `gemma-4-E2B-it.litertlm`) and follow the **Sideloading** instructions above.

## 🧠 Model Downloader Context

The `ModelDownloadWorker` is built for reliability and flexibility:
- **Hybrid Source**: It supports both `Firebase Storage` (resolved via Google Services) and raw `HTTP/HTTPS` URLs.
- **Atomic Persistence**: Downloads are written to a `.tmp` file first. The file is only renamed to the final `.litertlm` name after the download is 100% complete, preventing the engine from attempting to load a partial/corrupted core.
- **Foreground Execution**: Uses WorkManager's `setForeground` to ensure downloads continue even if the app is minimized.

## ⚙️ Hardware Requirements

- **GPU Acceleration**: Requires a device with a modern GPU and support for OpenCL/Vulkan.
- **Memory**: Multimodal capabilities require at least 4GB+ of available RAM.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
