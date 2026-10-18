# Google Play Console Submission Checklist — Chevere AI (`dev.neo.chevereai`)

Use this step-by-step reference when submitting **Chevere AI** to Google Play Console (Internal Testing, Closed Testing, or Production).

---

## 1. Recommended Rollout Track

1. **Start with Internal Testing** (up to 100 testers, available within minutes, no full Play policy wait):
   - Build signed release bundle:
     ```powershell
     $env:GRADLE_USER_HOME='C:\Users\nehem\.gradle'; .\gradlew.bat bundleRelease --no-daemon
     ```
   - Upload `app/build/outputs/bundle/release/app-release.aab` to **Testing → Internal testing**.
2. **Promote to Closed Testing / Production** once you have verified model downloads, chat inference, and Play Console declarations below.
   - *Note for Personal Developer Accounts (created after Nov 2023):* Google Play requires **12 testers opted-in for 14 continuous days** on a Closed Testing track before unlocking Production access.

---

## 2. App Content Declarations (Policy → App content)

### A. AI-Generated Content Policy
- **Does your app contain AI-generated content?** → **Yes**
- **In-App User Reporting:** Implemented via the **Flag / Report** icon on every assistant message bubble (`ReportAiContentDialog` → `AiContentReportRepository`), which scrubs PII and logs reports to Firebase Analytics (`ai_content_reported`) and Firestore (`ai_content_reports`).

### B. Foreground Service Permissions (`FOREGROUND_SERVICE_DATA_SYNC`)
Google Play requires a short justification and video link for `FOREGROUND_SERVICE_DATA_SYNC` (used by `ModelDownloadWorker` via `WorkManager`):
- **Use case selection:** Large file downloads initiated by the user (`dataSync`).
- **Description to paste in Play Console:**
  > Chevere AI runs Large Language Models (1 GB – 4 GB) entirely on-device. When the user taps "Download" on a model in the Model Marketplace, `WorkManager` starts a `dataSync` foreground service with a visible progress notification so the multi-gigabyte model download and SHA-256 verification do not fail if the user switches apps or turns off the screen.
- **Video demonstration (30 seconds):**
  1. Open Chevere AI → navigate to **Model Marketplace**.
  2. Tap **Download** on a model.
  3. Swipe to the Android home screen and pull down the notification shade to show the active **"Downloading Chevere AI model"** progress notification.

### C. Data Safety Form Answers
| Data Category | Collected (Off-Device)? | Shared with Third Parties? | Purpose & Notes |
| :--- | :--- | :--- | :--- |
| **Crash logs & Diagnostics** | **Yes** | No | Firebase Crashlytics (App functionality, Analytics). Ephemeral/anonymized. |
| **App interactions & Performance** | **Yes** | No | Firebase Analytics (model init timing, tool success rates, report events). |
| **Device or other IDs** | **Yes** | No | Firebase Installation ID for Analytics & Crashlytics. |
| **User-reported AI messages** | **Yes (Optional)** | No | Sent **only** when the user explicitly submits the "Report AI Response" dialog (PII-scrubbed). |
| **Approximate / Precise Location** | **No** (Ephemeral) | No | Used only when the user asks for local weather; coordinates are sent directly to Open-Meteo and never stored. |
| **Contacts / Calendar / Microphone / Photos** | **No** (Local only) | No | Processed strictly on-device (`READ_CONTACTS`, `READ_CALENDAR`, `WRITE_CALENDAR`, `RECORD_AUDIO`, attached images). Never uploaded to servers. |

- **Is all collected data encrypted in transit?** → **Yes** (HTTPS/TLS).
- **Do you provide a way for users to request that their data be deleted?** → **No account is created**; local chat/task data is deleted directly inside the app (Clear Conversation / Delete Session) or by uninstalling the app.

### D. Target Audience & Content Rating (IARC)
- **Target Age Group:** `18 and over` (or `16–17` and `18 and over`—do **not** select under-13 to avoid Families Policy restrictions).
- **Content Rating Questionnaire:** Category = **Utility, Productivity, Communication, or Other**; interacts with open-ended AI responses, no user-to-user chat, no purchases.

### E. Advertising ID (`AD_ID`)
- If prompted about Advertising ID due to `firebase-analytics`:
  - **Does your app use Advertising ID?** → Select **Yes → Analytics only** (or disable `AD_ID` in `AndroidManifest.xml` if you do not use attribution).

---

## 3. Firebase Setup (Optional before Production)

1. **Enable Cloud Firestore API** in the [Firebase Console](https://console.firebase.google.com/) for project `aiassistant-88f75` so user-submitted `ai_content_reports` and remote model catalog documents sync to the cloud.
2. **Deploy Firestore Security Rules & Privacy Policy Hosting:**
   ```powershell
   npx -y firebase-tools@latest deploy --only firestore:rules,hosting
   ```
3. **Optional Web Search Proxy for Production:**
   - By default, release builds strip `SERPER_API_KEY` from the APK/AAB and fall back to key-less DuckDuckGo Instant Answer queries.
   - If you deploy a Cloud Function or proxy for Serper, pass `-PWEB_SEARCH_PROXY_URL=https://your-proxy-url` (or set the `WEB_SEARCH_PROXY_URL` environment variable) at build time.
