# 📖 MTL Vault

> **Translate • Read • Listen**  
> An offline-first novel library, reader, and native text-to-speech Android app built with Kotlin, Jetpack Compose, and Material 3. Inspired by ClassGrid's design language.

---

## 🚀 How to Build the APK on GitHub

This repository comes with pre-configured GitHub Actions (`.github/workflows/build-apk.yml`) to automatically build and package installable Android APKs.

### Option 1: Automatic Build on Push
1. Push this repository to GitHub (or export/push directly from Google AI Studio).
2. Every push to the `main` or `master` branch will automatically trigger the **Build Android APK** workflow.
3. Once the workflow finishes (takes ~1-2 minutes):
   - Go to the **Actions** tab on your GitHub repository.
   - Click on the latest workflow run.
   - Scroll down to the **Artifacts** section at the bottom.
   - Click **MTL-Vault-Debug-APK** to download the ZIP containing your `app-debug.apk`.
   - Install it directly on your Android phone!

### Option 2: Manual Trigger ("Run workflow")
1. Go to the **Actions** tab on GitHub.
2. Select **Build Android APK** from the left sidebar.
3. Click the **Run workflow** dropdown on the right.
4. Choose your build type (`debug`, `release`, or `both`) and click **Run workflow**.
5. Download your APK artifact from the summary page when complete.

---

## 💻 How to Build Locally

If you clone the repository locally:

```bash
# Clone the repository
git clone https://github.com/your-username/mtl-vault.git
cd mtl-vault

# Make the Gradle wrapper executable (macOS/Linux)
chmod +x gradlew

# Build the Debug APK
./gradlew assembleDebug

# The APK will be generated at:
# app/build/outputs/apk/debug/app-debug.apk
```

To run unit tests locally:
```bash
./gradlew testDebugUnitTest
```

---

## ✨ Features

- **Offline-First Library**: Local SQLite storage via Room. No cloud requirements, 100% private.
- **EPUB & TXT Importing**:
  - Automatically parses `.epub` metadata (title, author, description) and extracts XHTML chapters in spine order.
  - Multi-lingual auto-detection for `.txt` chapter splitting (`Chapter 1`, `Chapitre`, `Capítulo`, `باب`, `अध्याय`, etc.).
- **Immersive Typography Reader**:
  - Themes: Light, Warm Sepia, Dark Charcoal, and True Black OLED Night.
  - Custom font size, line height, Serif (Georgia) vs. Sans-serif, and foldable/tablet width constraints.
  - Persistent reading positions and progress bars.
  - Direct integration with **@Voice** via the Android share sheet.
- **Native Android Text-to-Speech (TTS)**:
  - Paragraph, sentence, and word boundary chunking for smooth reading of 10,000+ word chapters.
  - Audio focus handling (pauses/ducks on incoming calls or external audio interrupts).
  - Continuous chapter playback (auto-advances from Chapter 1 → Chapter 2 → Chapter 3).
  - Compact player bar with speed (0.5x–2.5x) and pitch adjustments.
- **Text Replacements System**:
  - Per-novel terminology rules (`find = replace`, `[cs]`, `[nww]`, `[ww]`, `# comments`).
  - Applied non-destructively in-memory (your original database content is never mutated).
  - Live test box to preview substitutions in real time.
- **Full Backup & Restore**:
  - Export and restore JSON backups compatible with web MTL Vault backups.
