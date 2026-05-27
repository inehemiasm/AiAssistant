# Chevere AI

Chevere AI is a privacy-first Android assistant that runs local AI models on device. It combines chat, vision, agent tools, device actions, sensors, model management, and local image generation in one Kotlin app.

The project is built for experimenting with capable on-device assistants: small enough to run locally, but structured enough to support real app features like downloads, permissions, background work, model health, and a Compose UI.

## What It Can Do

- Chat with local LiteRT-LM models such as Gemma.
- Attach camera or gallery images for vision-capable chat models.
- Run an agent loop that can search, check weather, summarize text, manage tasks, draft emails, open apps, copy/share text, and use Android intents.
- Read device context through sensors such as light, pressure, battery, compass, accelerometer, gyroscope, proximity, thermals, and optional microphone-based noise level.
- Download, verify, install, select, and protect local models through a marketplace-style UI.
- Generate images locally from ONNX Stable Diffusion bundles.
- Keep older chat context compact so small on-device models stay usable.
- Benchmark local model performance with warmup, TTFT, throughput, RAM, and acceleration details.

## Screens And Flows

- **Chat**: local conversation, image attachments, assistant actions, explicit image safeguards, and shareable responses.
- **Models**: separate chat/vision models from image-generation models, track installs, and prevent active model deletion.
- **Tasks**: local checklist management, including agent-created tasks.
- **Settings**: safety, privacy, theme controls, and benchmarking.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose, Material 3
- **Architecture**: Clean Architecture, MVI
- **Dependency injection**: Hilt
- **Local LLM runtime**: Google AI Edge LiteRT-LM
- **Image runtime**: ONNX Runtime Android
- **Storage**: Room
- **Background work**: WorkManager foreground downloads
- **Networking**: Ktor and OkHttp
- **Modules**: `:app`, `:ui-designsystem`

## Model Support

Chevere AI currently supports:

- `.litertlm` chat models
- `.bin` legacy/local LiteRT-compatible models
- extracted ONNX diffusion directories with:
  - `text_encoder/model.ort`
  - `tokenizer/vocab.json`
  - `tokenizer/merges.txt`
  - `unet/model.ort`
  - `vae_decoder/model.ort`

ZIP model bundles are extracted atomically into app-private storage. The first healthy chat model activates automatically, and the first healthy image model becomes available without replacing the active chat model.

## Getting Started

Clone the repo and build the debug app:

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"; .\gradlew.bat assembleDebug --no-daemon
```

Then install the debug APK, open the model marketplace, and download or add a supported local model.

Firestore-backed catalog support requires `google-services.json` in `app/`.

## Common Commands

```bash
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest
./gradlew lint
./gradlew clean
```

## Project Layout

```text
app/src/main/java/com/neo/chevere/
  data/       agent tools, inference, model downloads, Room, repositories
  domain/     shared models, repository contracts, model metadata
  ui/         Compose screens and MVI contracts

ui-designsystem/
  shared theme, typography, and reusable UI components
```

## Notes

- Most AI work happens locally. Network access is used for model discovery/downloads, web search, weather, and other explicitly online tools.
- Image generation on mobile hardware can be slow, especially on CPU.
- ONNX diffusion support is experimental and depends on bundle compatibility.
- Release builds block explicit image generation before the backend is invoked.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
