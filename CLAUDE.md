# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common Commands
- Build project: `./gradlew assembleDebug`
- Run all tests: `./gradlew test`
- Run Android instrumented tests: `./gradlew connectedAndroidTest`
- Run lint: `./gradlew lint`
- Clean project: `./gradlew clean`

## High-level Architecture
The project is an Android application following Clean Architecture and MVI (Model-View-Intent) patterns.

### Project Structure
- `:app`: Main application module.
- `:ui-designsystem`: Shared design system (themes, components).

### Architecture Layers (in `:app`)
- **Domain Layer** (`app/src/main/java/com/neo/aiassistant/domain/`): Use cases and repository interfaces.
- **Data Layer** (`app/src/main/java/com/neo/aiassistant/data/`):
    - **Inference Runtime** (`data/inference/`):
        - `LlmRuntimeManager`: Strictly manages LiteRT-LM `Engine` and `Conversation` lifecycles using a `Mutex`. Handles hardware fallback (GPU -> CPU).
        - `MultimodalMessageFactory`: Encapsulates image processing (448px, PNG) and `Message` creation.
        - `LlmResponseMapper`: Maps LiteRT-LM responses to strings using type-safe extraction.
    - **Data Sources** (`data/datasource/`):
        - `ModelCatalogDataSource`: Interface for model discovery (Firebase Firestore implementation).
        - `RemoteModelDataSource`: Interface for downloading models (Firebase Storage + Ktor implementation).
    - **Repository**: `ChatRepositoryImpl.kt` orchestrates the runtime and data sources.
- **UI Layer**:
    - `ChatViewModel.kt`: MVI logic using `ChatState`, `ChatIntent`, and `ChatEffect`.
    - `com.neo.aiassistant.ui.ChatComponents.kt`: Futuristic Compose UI components.
- **Background Tasks**: `ModelDownloadWorker.kt` handles atomic model downloads via `RemoteModelDataSource`.

### Design System (`:ui-designsystem`)
- Reusable components: `HighTechPrimaryButton`, `ModelSelectorCard`, `StatCard`, `AmbientGlow`.

### Local LLM & Multimodal Support
- **LiteRT-LM (0.10.0)**: On-device inference.
- **Session Lifecycle**: One active conversation at a time. Explicitly closed before re-init.
- **Cleanup**: `LlmRuntimeManager` handles native resource cleanup on failure.
- **Fallback**: GPU + Vision -> CPU + Vision -> Text-Only.

## Development Guidelines
- **Lifecycle Safety**: Always use `LlmRuntimeManager` for engine operations; never call `Engine` directly from repositories.
- **Image Handling**: Use `MultimodalMessageFactory` to ensure consistent bitmap processing.
- **Error Handling**: Surface specific exceptions (e.g., `IllegalArgumentException` for unsupported vision) to the UI via `Result` wrappers.
- **Kotlin Versions**: Always use the `-Xskip-metadata-version-check` compiler flag.
