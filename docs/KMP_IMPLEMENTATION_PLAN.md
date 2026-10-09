# Chevere AI Kotlin Multiplatform implementation plan

## Objective

Ship an iOS version of Chevere AI while preserving the existing Android app and sharing the parts that should behave identically: domain models, use cases, agent orchestration, presentation state, and eventually most Compose UI.

This is an incremental migration. The Android application remains the production entry point while `:shared` grows behind explicit interfaces.

## Current status

- [x] Create the `:shared` Kotlin Multiplatform module.
- [x] Configure Android, iOS device, and iOS simulator targets.
- [x] Export the static `ChevereShared` iOS framework.
- [x] Add a minimal expect/actual platform API to verify source-set wiring.
- [x] Make `:app` consume `:shared`.
- [x] Add portable model, media, runtime-state, and text-generation contracts.
- [x] Adapt Android's existing `InferenceManager` to the shared text runtime.
- [ ] Create the Xcode `iosApp` entry point.
- [ ] Move the first production vertical slice.

## Batch history

### Batch 1 — shared boundary and Android bridge

Completed:

- Registered `:shared` as an Android application dependency.
- Added platform-neutral `ModelArtifact`, `ModelLocation`, and `MediaReference` types.
- Added the `TextGenerationRuntime` contract and structured streaming events.
- Added `AndroidTextGenerationRuntime` over the existing model registry and `InferenceManager`.
- Converted Chevere's cumulative response snapshots to portable delta events.
- Bound the shared runtime contract through the existing Android Hilt graph.
- Added common domain and Android mapping/delta tests.

No user-facing runtime behavior was changed. Existing chat code continues to call `InferenceManager` directly until the next vertical slice is migrated.

Next batch: move the text-chat send/stream orchestration behind a shared use case, then make the existing Android `ChatRepositoryImpl` consume that shared use case.

## Architectural decisions

### Keep platform entry points separate

`:app` remains the Android application. A future `iosApp` Xcode project will own Apple signing, entitlements, lifecycle, and App Store configuration. Neither entry point belongs in shared code.

### Share contracts before implementations

Move portable interfaces and immutable models first. Android implementations continue to use LiteRT-LM, Room, WorkManager, Hilt, and Android system APIs. iOS implementations can use native Apple APIs without leaking their types into `commonMain`.

### Remove platform types from the domain

Types such as `android.net.Uri`, `Bitmap`, `Context`, and `FileProvider` cannot appear in shared models. Replace them with portable values such as `MediaReference`, `ModelArtifact`, and byte/file-source abstractions. Convert to platform types at the edge.

### Use capability-specific inference contracts

Text generation, image generation, embeddings, and other model tasks should not be forced into one broad `generate()` API. Shared code owns lifecycle and task contracts; `androidMain` and `iosMain` provide runtime adapters.

### Keep dependency injection outside common code initially

Shared classes use constructor injection without Hilt annotations. Android Hilt modules assemble them in `:app`; the iOS entry point assembles them manually until a multiplatform DI library provides clear value.

## Intended project structure

```text
AiAssistant/
├── app/                         Android entry point and Android-only wiring
├── shared/
│   └── src/
│       ├── commonMain/          Domain, use cases, orchestration, state
│       ├── androidMain/         Android implementations and adapters
│       └── iosMain/             Apple implementations and adapters
├── ui-designsystem/             Existing Android design system during migration
└── iosApp/                      Future Xcode entry point
```

If shared Compose UI grows substantially, introduce a separate `:shared-ui` module rather than coupling all shared logic to Compose.

## Migration phases

### Phase 0 — build foundation

1. Keep `:shared` dependency-free until the targets configure successfully.
2. Compile its Android target on Windows.
3. Create and build the iOS framework on macOS.
4. Add an Xcode `iosApp` that displays a value from `ChevereShared`.

Exit criterion: Android consumes the AAR and an iOS simulator consumes `ChevereShared.framework`.

### Phase 1 — portable domain

Move or introduce:

- Model identifiers, formats, task types, capabilities, and installation states.
- Chat roles and platform-neutral message content.
- Inference request/result/event contracts.
- Repository and runtime interfaces.
- Agent tool definitions and JSON argument/result types.

Do not move the existing `Models.kt` as-is because it contains Android `Uri` values. First create portable replacements and adapters, then migrate call sites.

Exit criterion: shared domain tests run without Android or Apple dependencies.

### Phase 2 — first end-to-end slice

Use text-only chat as the first vertical slice:

1. Move chat state, message formatting, and the send-message use case.
2. Define a shared `TextGenerationRuntime` contract.
3. Adapt the existing Android `InferenceManager`/LiteRT implementation.
4. Add a fake iOS runtime so UI and orchestration can be developed independently.
5. Make Android use the shared use case before implementing native iOS inference.

Exit criterion: the existing Android chat works through shared contracts and the iOS UI streams a fake response through the same shared state.

### Phase 3 — iOS local inference

1. Select and prove one supported iOS LLM runtime.
2. Implement model loading, streaming, cancellation, reset, and unloading.
3. Measure load time, time to first token, memory pressure, and repeated load/unload behavior on a real device.
4. Add runtime-specific conversion between shared media references and native inputs.

Exit criterion: download one supported model, complete repeated conversations, and recover cleanly from cancellation and memory warnings on a physical iPhone.

### Phase 4 — model management and persistence

Share installation state machines and validation rules. Keep these implementations platform-specific:

- Android: WorkManager, Room, Android filesystem.
- iOS: URLSession/background transfer, Apple filesystem, and an iOS database driver.

Exit criterion: interrupted downloads resume or fail predictably, checksums are verified, and installed models survive application restarts on both platforms.

### Phase 5 — tools and multimodality

Add one platform capability at a time behind shared contracts:

- Documents and local RAG.
- Photos/camera and vision input.
- Voice input.
- Contacts, calendar, location, and sensors.
- Image generation.

Platform permission and privacy behavior remains native. Unsupported tools must be reported through capability discovery instead of failing at execution time.

### Phase 6 — shared Compose UI

After the text-chat slice is stable, migrate reusable UI and state screen by screen. Keep platform-owned surfaces native, including permission prompts, document/photo pickers, share sheets, and settings integrations.

Exit criterion: shared screens behave correctly on Android and iOS while platform interactions retain native behavior.

## Initial shared boundaries

```kotlin
interface TextGenerationRuntime {
    val state: StateFlow<ModelRuntimeState>

    suspend fun load(model: ModelArtifact): LoadResult
    fun generate(request: TextGenerationRequest): Flow<TextGenerationEvent>
    suspend fun resetConversation()
    suspend fun unload()
}

interface AgentTool {
    val definition: ToolDefinition
    suspend fun execute(arguments: JsonObject): ToolResult
}
```

These are design targets, not APIs to copy unchanged. They should be introduced with the first migrated call sites and tests.

## Testing strategy

- `commonTest`: model rules, use cases, state reducers, orchestration, and fake runtimes.
- Android host tests: Android adapters and mappings.
- Android device tests: model lifecycle, permissions, and hardware inference.
- iOS simulator tests: shared integration and platform services that support simulation.
- Physical iPhone tests: memory, GPU/runtime behavior, large downloads, backgrounding, and thermal conditions.

Windows can verify the Android target and common metadata but cannot produce or validate the final iOS framework. A macOS machine with Xcode is required for iOS builds, signing, simulator execution, and device testing.

## Next implementation slice

1. Add `:shared` as an implementation dependency of `:app`.
2. Verify `ChevereShared.platformDescription()` from an Android host test.
3. Add portable `MediaReference` and text-generation contracts with tests.
4. Adapt, rather than move, the current Android inference implementation.
5. Create the Xcode `iosApp` only after the shared framework builds on macOS.

## Explicitly deferred

- Moving all current domain models at once.
- Migrating the entire Compose UI.
- Choosing a permanent iOS inference runtime before a device proof of concept.
- Porting ONNX diffusion before text generation is reliable.
- Replacing Hilt throughout the Android application.
- Publishing `:shared` as a standalone SDK.
