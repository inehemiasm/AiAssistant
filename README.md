# AI Assistant (LiteRT-LM Multimodal + Agents)

A futuristic, high-performance Android AI assistant powered by **Google AI Edge LiteRT-LM**. This application runs large language models (like Gemma) entirely on-device, supporting text, image-based multimodal conversations, and autonomous agent capabilities.

## 🚀 Features

- **Local LLM Execution**: Run models locally on your device for maximum privacy and offline availability.
- **Multimodal Support**: Analyze images from your gallery or take new photos to discuss them with the AI.
- **Agentic Reasoning**: Features an **Agent Orchestrator** that follows a Reason-Act-Observe loop to solve complex tasks.
- **Tool Use (Function Calling)**: The agent can invoke local tools such as:
    - `AnalyzeImageTool`: Detailed visual description using multimodal capabilities.
    - `ExtractTasksTool`: Automatically identifies actionable items from text.
    - `SummarizeTextTool`: Condenses long conversations or documents.
- **Hardware Acceleration**: Automatic fallback logic attempting GPU acceleration first, with CPU fallback for stability.
- **Futuristic UI**: A high-tech, "cyberpunk" inspired interface built with Jetpack Compose.
- **Shared Design System**: Modular `:ui-designsystem` library containing reusable high-tech components and theme.
- **Real-time Engine Status**: Detailed feedback during the neural engine initialization and agent planning phases.

## 🛠 Tech Stack

- **UI**: Jetpack Compose
- **Local AI**: [LiteRT-LM (Google AI Edge)](https://ai.google.dev/edge/litert)
- **Architecture**: Clean Architecture + MVI (Model-View-Intent) + Agentic Workflow
- **Dependency Injection**: Hilt
- **Networking**: Ktor + OkHttp
- **Database/Storage**: Firebase Firestore (for model metadata) & Firebase Storage (for model files)
- **Background Tasks**: WorkManager
- **Image Handling**: Coil & AndroidX Exifinterface

## 📋 Architecture & Modules

The project follows a decoupled architecture with an added agentic layer:

### 1. `:app` (Android Application)
- **Agent Layer** (`data/agent/`):
    - `AgentOrchestrator`: Manages the iterative reasoning loop.
    - `ToolRegistry`: Manages available local functions the AI can call.
    - `ToolCallParser`: Extracts structured tool requests from raw LLM output.
- **Inference Layer** (`data/inference/`):
    - `InferenceManager`: High-level manager for model loading and generation.
    - `LlmRuntimeManager`: Manages the LiteRT-LM engine lifecycle, hardware backends, and sessions.
    - `MultimodalMessageFactory`: Centralizes image preprocessing and `Message` construction.
- **Repository** (`ChatRepositoryImpl.kt`): Orchestrates between the Agent layer, Inference runtime, and Data sources.

### 2. `:ui-designsystem` (Android Library)
Centralized source of truth for the app's visual identity:
- **Theme**: Custom Material3 theme with high-tech typography and colors.
- **Components**: Reusable elements like `HighTechPrimaryButton`, `ModelSelectorCard`, `StatCard`, and `AmbientGlow`.

## ⚙️ Setup & Configuration

### Option 1: Firebase Integration (Automatic Downloads)
1. **Firebase Project**: Add an Android app with package `com.neo.aiassistant` and place `google-services.json` in `app/`.
2. **Firestore**: Create a `models` collection with `name`, `url`, and `supportsVision` (Boolean) fields.
3. **Storage**: Upload `.litertlm` files.

### Option 2: Manual Sideloading
1. **Prepare Models**: Obtain `.litertlm` files (e.g., `gemma-2b-it-cpu-int4.litertlm`).
2. **Push to Device**:
   ```bash
   adb shell "run-as com.neo.aiassistant mkdir -p /data/data/com.neo.aiassistant/files"
   adb push your_model.litertlm /data/local/tmp/
   adb shell "run-as com.neo.aiassistant cp /data/local/tmp/your_model.litertlm /data/data/com.neo.aiassistant/files/"
   ```

## ⚙️ Hardware Requirements

- **GPU Acceleration**: Modern GPU with OpenCL/Vulkan support (e.g., Adreno 600+ or Mali-G series).
- **Memory**: 4GB+ RAM recommended for Gemma 2B models.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
