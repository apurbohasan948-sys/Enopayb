# J.A.R.V.I.S. — Personal AI Assistant for Android

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-purple.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![Gemini](https://img.shields.io/badge/Google%20Gemini-2.5%20Flash-orange.svg)](https://aistudio.google.com/)

**J.A.R.V.I.S.** is a next-generation on-device and cloud-hybrid personal AI assistant engineered for Android (optimized for Snapdragon 685 / Redmi Note 12 architecture and all modern Android devices). It features a real-time holographic HUD console, local quantized GGUF inference, Google Gemini Cloud Teacher supervision, associative Room vector memory, an offline RAG knowledge base, defensive prompt-injection security shields, and sandboxed device tool execution.

---

## ⚡ Key Capabilities

- 🎙️ **Holographic HUD & Voice Console**: Live audio waveform visualizer, text-to-speech (TTS), speech-to-text (STT), bilingual support (English & Bengali), and streaming token telemetry.
- 🧠 **Dual Brain Architecture**:
  - **Local On-Device Engine**: 100% offline, zero data egress, sub-60ms response latency for device actions.
  - **Gemini Cloud Teacher Supervisor**: Real-time integration with Google Gemini 2.5 Flash / 2.0 Flash / 1.5 Pro for deep reasoning and automated skill distillation.
- 🔑 **In-App Gemini API Key Manager**: Add, edit, test, and save your Google AI Studio Gemini API Key directly inside the app with live connection diagnostics.
- 🛡️ **Defensive Security & Privacy Shield**: Real-time prompt-injection quarantine, sensitive token masking, air-gap firewall toggles, and risk-rated tool execution confirmations.
- 📚 **Offline RAG & Vector Knowledge Store**: On-device document chunking, TF-IDF / term-frequency semantic ranking, and instant retrieval without cloud dependencies.
- 🛠️ **Autonomous Skills Engine**: Native Android hardware control (Flashlight, App Launcher, Battery Health Diagnostics, Telephony, SMS, and Security Audits).

---

## 🤖 Automatic GitHub APK Builds (CI/CD)

This repository comes pre-configured with **GitHub Actions CI/CD** (`.github/workflows/build.yml`).

### How to download the automatically built APK from GitHub:
1. **On Every Push/PR**: GitHub Actions automatically compiles both debug and release APKs.
2. **Download Artifacts**:
   - Navigate to your repository's **Actions** tab on GitHub.
   - Click the latest **Build Android APKs** workflow run.
   - Under **Artifacts** at the bottom of the page, download **`JARVIS-Debug-APK`**, **`JARVIS-Release-APK`**, or **`JARVIS-All-APKs`**.
3. **Automated GitHub Releases**:
   - Push any version tag (e.g., `git tag v1.0.0 && git push origin v1.0.0`).
   - GitHub Actions will create a new release on the **Releases** page and attach the compiled APKs.

### 🔐 Optional GitHub Repository Secrets
You can configure the following in **Settings > Secrets and variables > Actions**:
- `GEMINI_API_KEY`: Injects your Gemini API key during the build.
- `RELEASE_KEYSTORE_BASE64`: Base64 encoded `.jks` release keystore for custom signing.
- `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`: Keystore credentials (optional, defaults to standard debug credentials if not provided).

---

## 🚀 Building & Exporting the APK Locally

### 1. Clone the GitHub Repository
```bash
git clone https://github.com/your-username/jarvis-android-assistant.git
cd jarvis-android-assistant
```

### 2. Open in Android Studio
1. Open **Android Studio** (Koala / Ladybug or newer recommended).
2. Select **File > Open** and choose the cloned project root directory.
3. Allow Gradle to sync dependencies automatically.

### 3. Build APK via Command Line
To build the debug APK:
```bash
./gradlew assembleDebug
```
The generated APK will be available at:
`app/build/outputs/apk/debug/app-debug.apk`

To build the release APK:
```bash
./gradlew assembleRelease
```
The generated APK will be available at:
`app/build/outputs/apk/release/app-release.apk`

---

## 🔑 Configuring Your Gemini API Key

You can configure your Google Gemini API key in two convenient ways:

### Method 1: Directly Inside the App (Recommended)
1. Launch the **J.A.R.V.I.S.** app on your Android device or emulator.
2. Tap the **MODELS** tab in the bottom navigation bar (or the **OFFLINE / API READY** chip in the top bar).
3. Under the **GEMINI API KEY & CONFIG** section:
   - Paste your API key from [Google AI Studio](https://aistudio.google.com/app/apikey).
   - Select your preferred model (e.g. `gemini-2.5-flash`, `gemini-2.0-flash`, `gemini-1.5-flash`, or `gemini-1.5-pro`).
   - Adjust the Temperature slider (0.0 to 1.0) according to your preference.
4. Tap **Test API** to verify your connection and see latency.
5. Tap **Save Config** to store your configuration securely.

### Method 2: Via `.env` File (Build-Time)
1. Copy `.env.example` to `.env` in the root project directory:
   ```bash
   cp .env.example .env
   ```
2. Edit `.env` and set your key:
   ```env
   GEMINI_API_KEY=AIzaSyYourActualKeyHere...
   ```
3. Rebuild the app.

---

## 📱 Hardware & Target Specifications

- **Target Architecture**: ARM64 / ARMv8 / x86_64
- **Optimized Device**: Redmi Note 12 (Snapdragon 685 8-Core Kryo)
- **Minimum OS**: Android 7.0 (API 24)
- **Target OS**: Android 16 (API 36)
- **Local Inference Memory Budget**: ~800MB RAM footprint
- **UI Framework**: 100% Jetpack Compose with Material Design 3

---

## 🔒 Security & Privacy Policy

- All chats, episodic memories, skills, and RAG knowledge chunks are stored **locally on-device in an encrypted Room SQLite database**.
- When **Local Only Mode** is selected, zero network requests are made.
- When **Hybrid Mode** is active, cloud queries only transit via encrypted TLS 1.3 to Google Gemini endpoints.
- Any potentially dangerous device interaction (such as dialing numbers or sending SMS) triggers a mandatory manual user confirmation prompt.

---

## 📄 License
Licensed under the Apache License 2.0.
