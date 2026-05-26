# Privacy Policy for Chevere AI

**Effective Date:** October 26, 2023

Chevere AI ("we," "our," or "us") is committed to protecting your privacy. This Privacy Policy explains how we collect, use, and safeguard your information when you use our mobile application.

## 1. Local-First Philosophy
Chevere AI is designed with a "Local-First" architecture. This means:
*   **Chat Processing:** Your conversations with the AI are processed entirely on your device using local Large Language Models (LLMs). Your prompts and the AI's responses are not sent to our servers.
*   **Image Processing:** Images you attach or generate are processed locally.
*   **Storage:** Your chat history and downloaded models are stored in the application's private storage on your device.

## 2. Information We Collect
While the core experience is offline, we use certain third-party services for app stability and improvement:

### A. Telemetry and Analytics (Firebase)
We use Firebase Analytics and Crashlytics to monitor app performance and identify bugs. This may include:
*   Device information (model, OS version).
*   App usage statistics (e.g., which screens are visited).
*   Crash logs and stack traces.
*   Anonymized identifiers.

### B. Remote Configuration and Data (Firebase Firestore)
We use Firebase Firestore to fetch the catalog of available AI models. We do not store your personal chat data in Firestore.

## 3. Permissions
Chevere AI requests the following permissions:
*   **Camera:** Used only when you choose to take a photo to share with the AI.
*   **Internet:** Used to download AI models and send anonymized telemetry.
*   **Notifications:** Used to show the progress of model downloads.
*   **Foreground Service:** Used to ensure model downloads complete if you leave the app.
*   **Contacts:** Used locally by the Contacts Tool to lookup email addresses by name when you request app actions (e.g., drafting an email). This data is processed strictly on-device and is never uploaded.
*   **Location:** Used locally by the Weather Tool to retrieve forecasts for your location. Location coordinates are sent directly to the weather provider and are never collected or stored on our servers.
*   **Calendar:** Used locally by the Calendar Tool to draft and schedule events on your device calendar.

## 4. Data Security
Data stored on your device is protected by the Android operating system's sandbox. Deleting the app removes all local models and chat history.

## 5. Changes to This Policy
We may update our Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy on this page.

## 6. Contact Us
If you have any questions about this Privacy Policy, please contact us at [Your Contact Email].
