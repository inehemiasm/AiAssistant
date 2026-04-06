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
- `:app`: Main application module containing business logic and UI.
- `:ui-designsystem`: Shared design system containing themes, colors, and reusable high-tech UI components.

### Architecture Layers (in `:app`)
- **Domain Layer** (`app/src/main/java/com/neo/aiassistant/domain/`): Contains business logic, use cases, and repository interfaces.
- **Data Layer** (`app/src/main/java/com/neo/aiassistant/data/`): Implements repository interfaces and handles data sourcing.
  - `ChatRepositoryImpl.kt`: Manages LiteRT-LM engine lifecycle, initialization fallback (GPU -> CPU), and multimodal message sending.
- **UI Layer**:
  - `ChatViewModel.kt`: Manages UI state and MVI logic.
  - `ChatContract.kt`: Defines `ChatState`, `ChatIntent`, and `ChatEffect`.
  - `com.neo.aiassistant.ui.ChatComponents.kt`: Contains main screen Compose UI components and Markdown parser.
  - `MainActivity.kt`: The main entry point.
- **Core** (`app/src/main/java/com/neo/aiassistant/core/`):
  - `BaseMvi.kt`: Base MVI infrastructure.
  - `ImageUtils.kt`: Utility for processing images (Exif rotation, cropping, scaling).

### Design System (`:ui-designsystem`)
- `Theme.kt`: `HighTechAiTheme` implementation.
- `Components.kt`: Reusable futuristic components:
  - `HighTechPrimaryButton`: Gradient-styled primary button.
  - `ModelSelectorCard`: Detailed card for selecting LLM architectures.
  - `StatCard`: Performance metrics display.
  - `AmbientGlow`: Decorative background glow effect.

### Dependency Injection
- **Hilt**: Used for dependency injection throughout the app.
- **AppModule**: Located in `app/src/main/java/com/neo/aiassistant/di/AppModule.kt`.

### Local LLM & Multimodal Support
- **LiteRT-LM (0.10.0)**: Used to run local large language models. Supports text and image input.
- **Multimodal Message Path**: Use `Message.user(Contents.of(Content.ImageBytes(bytes), Content.Text(prompt)))`.
- **Initialization Fallback**: GPU + Vision CPU -> CPU + Vision CPU -> Text-Only Mode.
- **Session Lifecycle**: Engine/Conversation objects are explicitly closed. A fresh conversation is used for each message.

## Development Guidelines
- **Kotlin Versions**: Always use the `-Xskip-metadata-version-check` compiler flag.
- **Multimodal**: When sending images, ensure `isVisionSupported()` is checked.
- **Attachments**: Camera and Gallery actions are grouped under a single "plus" icon menu in the `ChatInputBar`.
- **Camera/Gallery**: Implemented via `ActivityResultContracts` in `MainActivity.kt`. Safely sharing URIs via `FileProvider`.
