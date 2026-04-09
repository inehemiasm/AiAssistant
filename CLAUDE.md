# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common Commands
- Build project: `./gradlew assembleDebug`
- Run all tests: `./gradlew test`
- Run Android instrumented tests: `./gradlew connectedAndroidTest`
- Run lint: `./gradlew lint`
- Clean project: `./gradlew clean`

## High-level Architecture
The project is an Android application following Clean Architecture and MVI (Model-View-Intent) patterns, enhanced with an **Agentic Workflow**.

### Project Structure
- `:app`: Main application module.
- `:ui-designsystem`: Shared design system (themes, components).

### Architecture Layers (in `:app`)
- **Domain Layer** (`app/src/main/java/com/neo/aiassistant/domain/`): Use cases, repository interfaces, and shared models like `ChatMessage`, `InferenceRequest`, and `LocalModel`.
- **Data Layer** (`app/src/main/java/com/neo/aiassistant/data/`):
    - **Agent Layer** (`data/agent/`):
        - `AgentOrchestrator`: Implements the Reason-Act-Observe loop. Injects tool definitions into prompts and parses model output for tool calls.
        - `ToolRegistry`: Manages available `AgentTool` implementations (e.g., `AnalyzeImageTool`, `ExtractTasksTool`).
        - `ToolCallParser`: Parses XML-like tool calls from the LLM's raw text response.
    - **Inference Runtime** (`data/inference/`):
        - `InferenceManager`: Central entry point for model loading and generation. Uses `ModelEngineFactory` to instantiate specific engines.
        - `LiteRtEngine`: Implementation of `ModelEngine` for LiteRT-LM.
        - `LlmRuntimeManager`: Manages the low-level LiteRT-LM `Engine` and `Conversation` lifecycles. Handles hardware fallback (GPU -> CPU).
        - `MultimodalMessageFactory`: Encapsulates image processing (448px, PNG) and `Message` creation.
    - **Data Sources** (`data/datasource/`):
        - `ModelCatalogDataSource`: Interface for model discovery (Firebase Firestore implementation).
        - `RemoteModelDataSource`: Interface for downloading models (Firebase Storage + Ktor implementation).
    - **Repository**: `ChatRepositoryImpl.kt` orchestrates between the `AgentOrchestrator`, `InferenceManager`, and data sources.
- **UI Layer**:
    - `ChatViewModel.kt`: MVI logic using `ChatState`, `ChatIntent`, and `ChatEffect`.
    - `com.neo.aiassistant.ui.ChatComponents.kt`: Futuristic Compose UI components.
- **Background Tasks**: `ModelDownloadWorker.kt` handles atomic model downloads via `RemoteModelDataSource`.

### Design System (`:ui-designsystem`)
- Reusable components: `HighTechPrimaryButton`, `ModelSelectorCard`, `StatCard`, `AmbientGlow`.

### Local LLM & Agent Capabilities
- **LiteRT-LM (0.10.0)**: On-device inference for text and vision.
- **Agent Loop**: Supports multi-turn reasoning with local tool execution.
- **Session Lifecycle**: One active conversation at a time in `LlmRuntimeManager`.
- **Hardware Fallback**: GPU + Vision -> CPU + Vision -> Text-Only (CPU).

## Development Guidelines
- **Agent Extensions**: To add new capabilities, implement `AgentTool` and register it in `ToolRegistry`.
- **Lifecycle Safety**: Always use `InferenceManager` or `LlmRuntimeManager` for engine operations; never call LiteRT-LM `Engine` directly from UI or Repositories.
- **Image Handling**: Use `MultimodalMessageFactory` to ensure consistent bitmap processing (scaling to 448px).
- **Error Handling**: Surface specific exceptions to the UI via `Result` wrappers and `ChatState.RuntimeState`.
- **Kotlin Versions**: Always use the `-Xskip-metadata-version-check` compiler flag due to LiteRT-LM metadata.
- **Commit Attribution**: Do not add 'Co-Authored-By' or any other AI/agent attribution to git commit messages.
