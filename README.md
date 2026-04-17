# AI Assistant (LiteRT-LM Multimodal + Agents)

A futuristic, high-performance Android AI assistant powered by **Google AI Edge LiteRT-LM**. This application runs large language models (like Gemma) entirely on-device, supporting text, image-based multimodal conversations, and autonomous agent capabilities.

## 🚀 Features

- **Local LLM Execution**: Run models locally on your device for maximum privacy and offline availability.
- **Multimodal Support**: Analyze images from your gallery or take new photos to discuss them with the AI.
- **Agentic Reasoning**: Features an **Agent Orchestrator** that follows a Reason-Act-Observe loop to solve complex tasks.
- **Hardened Model Management**: Production-grade marketplace for discovering and managing AI engines:
    - **Hybrid Discovery**: Merged results from **Hugging Face Hub**, **Kaggle Models**, and **Firebase Firestore**.
    - **Integrity Verification**: Automatic SHA-256 checksum validation and health checks for installed models.
    - **Lifecycle Safety**: Guards against deleting active models or interrupting engine switches.
    - **Smart Grouping**: Curated models are prioritized over raw discovery items.
- **Tool Use (Function Calling)**: The agent can invoke a suite of local and remote tools:
    - **AI/Content Tools**:
        - `AnalyzeImageTool`: Detailed visual description using multimodal capabilities.
        - `ExtractTasksTool`: Automatically identifies actionable items from text.
        - `SummarizeTextTool`: Condenses long conversations or documents.
    - **System & Utility Tools**:
        - `WeatherTool`: Real-time weather data and forecasts (via Open-Meteo API).
        - `WebSearchTool`: Real-time information retrieval via Serper.dev with built-in **Internet Connectivity Detection** and LRU caching.
    - **Android System Tools**:
        - `OpenAppTool`: Intelligent launch-by-name for any installed application.
        - `ListAppsTool`: Enumerates all user-accessible apps on the device.
        - `GetAppCapabilitiesTool`: Analyzes an app's supported Android Intents (Sharing, Maps, Web, etc.).
        - `OpenUrlTool` & `OpenMapsTool`: Deep-link into browsers or navigation.
        - `DraftEmailTool` & `CreateCalendarEventTool`: Direct productivity shortcuts.
        - `ShareTextTool` & `CopyToClipboardTool`: System-wide data interaction.
- **Robust Background Downloads**: Uses **WorkManager** and **Ktor** for resilient model downloading with status tracking (Downloading -> Verifying -> Installed).
- **Hardware Acceleration**: Automatic fallback logic attempting GPU acceleration first, with CPU fallback for stability.
- **Futuristic Animated Splash**: A custom-drawn, high-tech animated splash screen with a floating robot companion and dynamic light trails.

## 🛠 Tech Stack

- **UI**: Jetpack Compose (MVI Pattern)
- **Local AI**: [LiteRT-LM (0.10.0)](https://ai.google.dev/edge/litert)
- **Architecture**: Clean Architecture + Agentic Workflow
- **Dependency Injection**: Hilt
- **Database**: Room (Registry for installed models and search cache)
- **Networking**: Ktor + OkHttp
- **Data Sources**: Hugging Face Hub API, Kaggle Models API, Firebase Firestore.
- **Background Tasks**: WorkManager (Foreground Service for downloads)

## 📋 Architecture & Modules

### 1. `:app` (Android Application)
- **Agent Layer** (`data/agent/`): Manages reasoning loops and tool execution.
- **Action Layer** (`data/agent/actions/`): Executes system-level Android commands with Intent security.
- **Inference Layer** (`data/inference/`): LiteRT-LM engine lifecycle and hardware backend management.
- **Registry** (`domain/InstalledModelRegistry.kt`): The single source of truth for model health and availability.

### 2. `:ui-designsystem` (Android Library)
Centralized visual identity: custom Material3 theme, futuristic typography, and reusable high-tech components.

## ⚙️ Setup & Configuration

### Model Discovery
The app automatically aggregates models from:
1. **Kaggle Models**: Official TFLite/LiteRT-LM versions of Gemma, MobileNet, etc.
2. **Hugging Face**: Curated community models and dynamic Hub search for `litertlm` tags.
3. **Firebase**: Private/Custom models defined in Firestore.

### Firebase Integration
1. Add `google-services.json` to `app/`.
2. Enable Firestore for the `models` collection.

## ⚙️ Hardware Requirements

- **GPU Acceleration**: Modern GPU (Adreno 600+ or Mali-G series).
- **Memory**: 4GB+ RAM recommended for Gemma 2B models.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
