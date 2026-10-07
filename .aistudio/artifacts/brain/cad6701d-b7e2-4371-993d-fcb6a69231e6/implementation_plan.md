# Comprehensive Bug Fix Implementation Plan

Address remaining issues discovered across global settings synchronization, import session state management, cross-platform EPUB zip path resolution, and replacement rule processing.

---

## User Review Required

> [!NOTE]
> Per your selections, TTS speed, pitch, and voice settings from the Settings screen and DataStore will be synchronized automatically across the entire application lifecycle (on app startup and in background services) without requiring manual adjustments inside the Reader screen.

---

## Proposed Changes

### Audio & TTS Synchronization
#### `MtlVaultApplication.kt` & `TtsManager.kt`
- **Synchronize Saved TTS Preferences on Launch**:
  - In `MtlVaultApplication.onCreate()`, collect `settingsDataStore.settingsFlow` and automatically synchronize `ttsRate`, `ttsPitch`, and `ttsVoice` to `ttsManager`.
  - In `TtsManager.onInit()`, ensure initial engine setup applies the currently stored rate and pitch rather than resetting to hardcoded `1.0f`.
- **Direct Updates from Settings Screen**:
  - In `SettingsViewModel.kt`, ensure `updateTtsRate()` and `updateTtsPitch()` update `ttsManager` directly in addition to persisting in `SettingsDataStore`.

---

### Import & Session State
#### `LibraryViewModel.kt`
- **Clear Target Novel State on Successful Import**:
  - In `completeImport()`, clear `_targetNovelId.value = null` alongside `_pendingImportText.value = null` so subsequent text imports do not append into an unintended previous novel.

---

### Cross-Platform EPUB Parser Resilience
#### `EpubParser.kt`
- **Windows Path Separator Normalization**:
  - Normalize all zip entry paths by replacing `\\` with `/` before looking up `META-INF/container.xml`, OPF rootfile paths, and chapter hrefs.
- **Empty Spine Fallback**:
  - If an EPUB lacks `<itemref>` elements in its spine or uses non-standard metadata, fall back to parsing valid XHTML/HTML files listed in the OPF manifest to prevent false "No chapters found" rejections.

---

### Replacement Rule Safety
#### `ReplacementEngine.kt`
- **Robust Group Reference Handling in Regex Mode**:
  - In `applySingleRule()`, guard `matcher.appendReplacement()` against malformed group references (such as literal `$` prices like `$100` in replacements) by falling back to quoted literal replacement when group indices do not exist in the source regex.

---

## Verification Plan

### Automated Tests
- Run Gradle unit tests:
  ```bash
  gradle :app:testDebugUnitTest
  ```
- Verify:
  - `EpubParserTest` validates path normalization and spine extraction.
  - `ReplacementEngineTest` validates literal dollar sign replacement and rule execution.
  - `TtsManagerTest` validates speed/pitch configuration.

### Manual Verification
1. **TTS Settings Sync**:
   - Open Settings screen, adjust TTS speed to 1.5x and pitch to 1.2x.
   - Open reader and start TTS; verify speech rate and pitch reflect the settings immediately without touching reader sliders.
2. **Library Import Isolation**:
   - Import text into an existing novel.
   - Return to library and import a new text file; verify it prompts for or creates a new novel rather than merging into the prior novel.
3. **EPUB Import**:
   - Import EPUB files with varying path separators and verify all chapters are extracted in proper order.
