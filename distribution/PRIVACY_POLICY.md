# Privacy Policy for Chevere AI

**Effective Date:** October 17, 2026

Chevere AI ("we," "our," or "us") is committed to protecting your privacy. This Privacy Policy explains how we collect, use, and safeguard your information when you use our mobile application.

## 1. Local-First Philosophy
Chevere AI is designed with a "Local-First" architecture. This means:
*   **Chat Processing:** Your conversations with the AI are processed entirely on your device using local Large Language Models (LLMs). Your prompts and the AI's responses are not sent to our servers during normal use.
*   **Image Analysis:** Images you attach for multimodal analysis are processed locally on your device.
*   **Storage:** Your chat history, tasks, and downloaded models are stored in the application's private storage on your device.

## 2. Information We Collect
While the core experience is offline, we use certain third-party services for app stability, safety, and model discovery:

### A. Telemetry and Analytics (Firebase)
We use Firebase Analytics and Crashlytics to monitor app performance and identify bugs. This may include:
*   Device information (model, OS version).
*   App usage statistics and model initialization metrics.
*   Crash logs and non-fatal error stack traces.
*   Anonymized installation identifiers.

### B. Remote Model Catalog & AI Content Reports (Firebase Firestore)
*   **Model Catalog:** We use Firebase Firestore and Hugging Face Hub to fetch the catalog of downloadable AI models.
*   **User-Initiated AI Content Reports:** In compliance with Google Play's AI-Generated Content policy, you can flag/report any AI response using the in-app report button. When you submit a report, the selected reason, optional notes, active model identifier, and PII-scrubbed excerpts of the reported response and prompt are transmitted to our moderation database to improve safety filters.

## 3. Permissions
Chevere AI requests the following permissions only when you invoke the corresponding feature:
*   **Camera:** Used only when you choose to take a photo to attach to a chat message.
*   **Microphone (`RECORD_AUDIO`):** Used only when you activate voice input (speech-to-text) or explicitly ask the assistant to measure ambient room noise levels (dB SPL). Audio is processed in real time on your device and is never recorded or uploaded.
*   **Internet:** Used to download AI models, perform optional web/weather queries, and send anonymized telemetry.
*   **Notifications & Foreground Service (`dataSync`):** Used to show progress and ensure multi-gigabyte AI model downloads complete reliably if you switch apps.
*   **Contacts:** Used locally by the Contacts Tool to look up email addresses by name when you ask the assistant to draft an email. Contact data is processed strictly on-device and is never uploaded.
*   **Location:** Used locally by the Weather Tool to retrieve forecasts for your area. Coordinates are sent directly to the weather API (Open-Meteo) and are never stored on our servers.
*   **Calendar:** Used locally by the Calendar Tool to read or create events on your device calendar at your request.

## 4. Data Security & Retention
Data stored on your device is protected by the Android operating system's application sandbox and optional biometric lock. Deleting your conversation history or uninstalling the app permanently removes all local models, tasks, and chat sessions from your device.

## 5. Changes to This Policy
We may update our Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy on this page.

## 6. Contact Us
If you have any questions about this Privacy Policy, please contact us at support@chevere.ai.
