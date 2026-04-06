# AI Assistant (LiteRT-LM Multimodal)

A futuristic, high-performance Android AI assistant powered by **Google AI Edge LiteRT-LM**. This application runs large language models (like Gemma) entirely on-device, supporting both text and image-based multimodal conversations.

## 🚀 Features

- **Local LLM Execution**: Run models locally on your device for maximum privacy and offline availability.
- **Multimodal Support**: Analyze images from your gallery or take new photos with the camera to discuss them with the AI.
- **Hardware Acceleration**: Automatic fallback logic attempting GPU acceleration first, with CPU fallback for stability.
- **Futuristic UI**: A high-tech, "cyberpunk" inspired interface built with Jetpack Compose.
- **Real-time Engine Status**: Detailed feedback during the neural engine initialization process.
- **Reliable Downloads**: Model management via WorkManager and Ktor for robust background downloading from Firebase Storage.

## 🛠 Tech Stack

- **UI**: Jetpack Compose
- **Local AI**: [LiteRT-LM (Google AI Edge)](https://ai.google.dev/edge/litert)
- **Architecture**: Clean Architecture + MVI (Model-View-Intent)
- **Dependency Injection**: Hilt
- **Networking**: Ktor + OkHttp
- **Database/Storage**: Firebase Firestore (for model metadata) & Firebase Storage (for model files)
- **Background Tasks**: WorkManager
- **Image Handling**: Coil & AndroidX Exifinterface

## 📋 Architecture

The project follows Clean Architecture principles:

- **Domain Layer**: Pure Kotlin business logic and repository interfaces.
- **Data Layer**: Concrete implementations of repositories, managing the LiteRT-LM engine lifecycle and model downloads.
- **UI Layer**: MVI-based ViewModels and modular Compose components.
- **Core Layer**: Shared infrastructure like the Base MVI class and image processing utilities.

## ⚙️ Setup & Configuration

1. **Firebase**:
   - Create a Firebase project and add your `google-services.json` to the `app/` directory.
   - Enable Firestore and create a `models` collection with `name` and `url` (gs://) fields.
2. **Model Files**:
   - The app expects `.litertlm` model files (e.g., Gemma 2B/4B).
   - Models can be uploaded to Firebase Storage and their paths added to Firestore.
3. **Hardware Requirements**:
   - For GPU acceleration, a device with a modern GPU and support for OpenCL/Vulkan is required.
   - Multimodal capabilities require at least 4GB+ of RAM depending on the model size.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
