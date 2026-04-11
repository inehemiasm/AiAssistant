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
- **Hybrid Model Discovery**: Combines dynamic **Hugging Face Hub** search with curated assets and Firebase Firestore.
- **Robust Background Downloads**: Uses **WorkManager** and **Ktor** for resilient model downloading with SHA-256 verification.
- **Hardware Acceleration**: Automatic fallback logic attempting GPU acceleration first, with CPU fallback for stability.
- **Futuristic UI**: A high-tech, "cyberpunk" inspired interface built with Jetpack Compose.
- **Shared Design System**: Modular `:ui-designsystem` library containing reusable high-tech components and theme.

## 🛠 Tech Stack

- **UI**: Jetpack Compose (MVI Pattern)
- **Local AI**: [LiteRT-LM (0.10.0)](https://ai.google.dev/edge/litert)
- **Architecture**: Clean Architecture + Agentic Workflow
- **Dependency Injection**: Hilt
- **Networking**: Ktor + OkHttp
- **Data Sources**: Hugging Face Hub API, Firebase Firestore, and local JSON assets.
- **Background Tasks**: WorkManager (Foreground Service for downloads)
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
- **Data Sources** (`data/datasource/`):
    - `ModelCatalogDataSource`: Merges models from **Hugging Face Hub API**, **Firestore**, and local assets.
    - `DefaultRemoteModelDataSource`: Unified downloader supporting standard HTTPS and `gs://` (Firebase Storage) URIs.
- **Repository** (`ChatRepositoryImpl.kt`): Orchestrates between the Agent layer, Inference runtime, and Data sources.

### 2. `:ui-designsystem` (Android Library)
Centralized source of truth for the app's visual identity:
- **Theme**: Custom Material3 theme with high-tech typography and colors.
- **Components**: Reusable elements like `HighTechPrimaryButton`, `ModelSelectorCard`, `StatCard`, and `AmbientGlow`.

## ⚙️ Setup & Configuration

### Option 1: Automatic Discovery (Recommended)
The app is pre-configured to search the **Hugging Face Hub** for compatible LiteRT models (`.litertlm` and `.bin`). Simply navigate to the **Marketplace** (Models tab) to browse and download.

### Option 2: Firebase Integration
1. **Firebase Project**: Add an Android app with package `com.neo.aiassistant` and place `google-services.json` in `app/`.
2. **Firestore**: Add entries to the `models` collection to have them appear in the catalog.
3. **Storage**: Upload models to Firebase Storage; the app will resolve `gs://` links automatically.

## ⚙️ Hardware Requirements

- **GPU Acceleration**: Modern GPU with OpenCL/Vulkan support (e.g., Adreno 600+ or Mali-G series).
- **Memory**: 4GB+ RAM recommended for Gemma 2B models.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
