# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common Commands
- Build project: `./gradlew assembleDebug`
- Run all tests: `./gradlew test`
- Run a single test: `./gradlew test --tests "com.neo.aiassistant.ClassName"`
- Run Android instrumented tests: `./gradlew connectedAndroidTest`
- Run lint: `./gradlew lint`
- Clean project: `./gradlew clean`

## High-level Architecture
The project is an Android application following Clean Architecture and MVI (Model-View-Intent) patterns.

### Architecture Layers
- **Domain Layer** (`app/src/main/java/com/neo/aiassistant/domain/`): Contains business logic, use cases (e.g., `SendMessageUseCase`), and repository interfaces.
- **Data Layer** (`app/src/main/java/com/neo/aiassistant/data/`): Implements repository interfaces and handles data sourcing.
- **UI Layer** (`app/src/main/java/com/neo/aiassistant/`):
  - Uses Jetpack Compose for the UI.
  - `ChatViewModel.kt` manages UI state and uses Hilt for injection.
  - `MainActivity.kt` is the `@AndroidEntryPoint`.
- **Core** (`app/src/main/java/com/neo/aiassistant/core/`): Contains base MVI infrastructure (`BaseMvi.kt`).

### Dependency Injection
- **Hilt**: Used for dependency injection throughout the app.
- **AppModule**: Located in `app/src/main/java/com/neo/aiassistant/di/AppModule.kt` for binding interfaces.
- **HiltWorker**: Used for background task injection in `ModelDownloadWorker`.

### Local LLM & Data Transfer
- **LiteRT-LM**: Used to run local large language models (`.litertlm` files).
- **Ktor**: Used for reliable model downloads with precise progress tracking.
- **Firebase Storage**: Source for model files via `gs://` URIs.
- **WorkManager**: Manages long-running downloads as Foreground Services to survive backgrounding.

## Development Guidelines
- **Kotlin Versions**: Always use the `-Xskip-metadata-version-check` compiler flag to handle library metadata mismatches.
- **Downloads**: Progress updates are throttled to 500ms intervals to maintain UI performance.
- **Local Models**: The app auto-detects `gemma-4-E2B-it.litertlm` and `gemma-4-E4B-it.litertlm` files in internal storage to bypass download screens.

## Technical Stack
- **Language**: Kotlin 2.1.10 (with metadata check bypass)
- **DI**: Hilt 2.55
- **Network/Transfer**: Ktor 2.3.12 + OkHttp 4.12.0
- **Processing**: KSP 2.1.10-1.0.30
- **UI Framework**: Jetpack Compose
- **Local AI**: LiteRT-LM (Google AI Edge)
- **Build System**: Gradle 8.7.3 (KTS)
- **JDK**: 17
