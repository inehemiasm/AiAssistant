# Chevere AI

Chevere AI is a privacy-first Android assistant that runs local AI models on device. It combines chat, vision, agent tools, device actions, sensors, model management, and local image generation in one Kotlin app.

The project is built for experimenting with capable on-device assistants: small enough to run locally, but structured enough to support real app features like downloads, permissions, background work, model health, and a Compose UI.

---

## What It Can Do

- **Local LLM Chat**: Chat with local LiteRT-LM models such as Gemma 2B.
- **Multimodal Vision**: Attach camera or gallery images for vision-capable models.
- **Natural Language Agent**: Run an agent reasoning loop (Reason-Act-Observe) that can search, check weather, summarize text, manage tasks, draft emails, open apps, copy/share text, and use Android intents.
- **Hardware Sensors**: Read ambient light, atmospheric pressure, battery level/thermals, CPU temperatures, compass heading, accelerometer, gyroscope, posture, flatness, magnetic field strength, and ambient sound level.
- **Visual Radar Dashboard**: Launch real-time visual tools: stud finder/metal detector, spirit level (bubble level), light meter, and proximity detector.
- **Model Marketplace**: Discover, download, verify (SHA-256), and extract models atomically.
- **Stable Diffusion**: Generate images locally from ONNX Stable Diffusion bundles.
- **Data & RAM Savers**: Background context summarization to keep context windows small, automatic background memory unloading, and Wi-Fi-only download controls.
- **Local Benchmarking**: Measure warmup times, Time to First Token (TTFT), tokens per second throughput, and RAM usage on your device.

---

## User Guide: How to Use Chevere AI

### 1. First-Time Setup & Downloading Models
1. **Launch the App**: When you first open the app, it will search for installed models. If none are found, you will be prompted to download one.
2. **Go to the Marketplace**: Navigate to the **Marketplace** screen to discover available chat/vision and image-generation models.
3. **Configure Download Constraints**: Open the **Settings** screen (gear icon) and ensure **"Download models over Wi-Fi only"** is toggled on if you want to avoid cellular data charges. When active, WorkManager will restrict large model downloads (often > 1GB) to Wi-Fi networks and require that your device is not low on battery or storage.
4. **Download and Activate**: Tap download on a chat model (like `Gemma 2B`). Once verified and extracted, it will automatically activate as the default chat model.

### 2. Multi-Modal Chat & Visual Interaction
* **Attach Images**: Tap the paperclip icon in the chat bar to capture a photo using your **Camera** or select an image from your **Gallery**. Ask the model to describe or explain the image (requires a vision-supported model).
* **Copy & Share**: Long-press or tap the share icon on any assistant message to copy the text to your clipboard or share it via Android's native share sheet.

### 3. Slash Commands & Autocomplete
Chevere AI features an **interactive autocomplete popup** for quick shortcuts. Type a slash (`/`) in the chat bar to open the popup menu and select a command:
* `/image <prompt>`, `/img <prompt>`, or `/imagine <prompt>`: Directly triggers local image generation using your installed Stable Diffusion model. *(Note: This bypasses LLM prompt rewriting, prompts for age verification on explicit prompts, and uses FileProvider to share the generated PNG).*
* `/sensors`: Navigates to the full **Sensor Radar** dashboard.
* `/stud` or `/metal`: Launches the visual **Stud Finder** & magnet detector screen.
* `/level`: Launches the **Spirit Level** / pitch & roll bubble level.
* `/light`: Opens the **Light Meter** to view ambient lux.
* `/proximity`: Opens the **Proximity Detector**.

### 4. Natural Language Agent Tools
You can trigger complex local actions simply by chatting with the assistant:
* **Check the Weather**: Ask *"How's the weather in Seattle?"* or *"What's the weather like here?"* (The agent will geocode locations online or ask for location permission to query Seattle or your device coordinates via Open-Meteo).
* **Look Up & Email Contacts**: Say *"Find Bob's email"* or *"Draft an email to Alice"*. The assistant will ask for contacts permission, resolve the name, retrieve the email, and draft the email ready to send.
* **Manage Tasks**: Speak *"Add buy groceries to my tasks"* or *"Show my pending tasks"*. The agent connects with the local database to create, list, toggle, or delete items.
* **Hardware Sensors**: Ask *"What is my battery level?"*, *"Is my phone face down?"*, or *"Can you detect metal?"*. The agent will invoke `read_sensors` to query hardware.
* **Device Control**: Ask the agent to *"turn on Do Not Disturb"* or *"mute my media volume"*.

### 5. Memory Management & Background Unloading
* **Background Context summarization**: As your conversation gets longer, Chevere AI automatically condenses older messages into a compact background memory capsule, keeping Gemma's context footprint small to prevent model stuttering.
* **Automatic RAM Reclaim**: Local LLMs and ONNX runtimes occupy significant system RAM. If Chevere AI is sent to the background (app minimized) for **more than 3 minutes**, the app automatically unloads active models to free up resources. They will reload transparently when you return.

### 6. Local Benchmarking
Want to test how fast local models run on your hardware?
1. Navigate to **Settings** -> **Benchmark**.
2. Tap **Run Benchmark**.
3. The screen will report hardware statistics (Acceleration type, available RAM) and run test inference loops to measure **warmup time**, **Time-To-First-Token (TTFT)**, and **Tokens-Per-Second (TPS)** throughput.

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose, Material 3
- **Architecture**: Clean Architecture, MVI
- **Dependency injection**: Hilt
- **Local LLM runtime**: Google AI Edge LiteRT-LM
- **Image runtime**: ONNX Runtime Android (limiting thread allocations to avoid UI starvation)
- **Storage**: Room
- **Background work**: WorkManager foreground services for safe, resumable downloads
- **Networking**: Ktor and OkHttp
- **Modules**: `:app`, `:ui-designsystem`

---

## Model Support

Chevere AI currently supports:

- `.litertlm` chat/vision models.
- `.bin` legacy/local LiteRT-compatible models.
- Extracted ONNX diffusion directories containing:
  - `text_encoder/model.ort` (or `model.onnx`)
  - `tokenizer/vocab.json`
  - `tokenizer/merges.txt`
  - `unet/model.ort` (or `model.onnx`)
  - `vae_decoder/model.ort` (or `model.onnx`)

---

## Getting Started

Clone the repository and build the debug APK:

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"; .\gradlew.bat assembleDebug --no-daemon
```

Then install the debug APK, open the model marketplace, and download or add a supported local model.
*Note: Firestore-backed discovery catalog support requires a `google-services.json` file in `app/`.*

---

## Common Development Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Run code linter
./gradlew lint

# Clean build directory
./gradlew clean
```

---

## Project Layout

```text
app/src/main/java/com/neo/chevere/
  data/       agent tools, inference, model downloads, Room database, repositories
  domain/     shared models, repository interfaces, model metadata, permission wrappers
  ui/         Compose screens, ViewModels, and MVI contracts

ui-designsystem/
  shared theme, typography, and reusable UI components
```

---

## Notes

- **Offline-First**: Most AI inference happens locally. Network access is used strictly for model downloads, weather geocoding, and Serper web search.
- **Image Generation Speeds**: Running Stable Diffusion on mobile CPU/GPU is heavy. Speed varies significantly depending on hardware acceleration.
- **Release Content Safeguards**: Release builds block explicit image prompts before invoking the diffusion engine.

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
