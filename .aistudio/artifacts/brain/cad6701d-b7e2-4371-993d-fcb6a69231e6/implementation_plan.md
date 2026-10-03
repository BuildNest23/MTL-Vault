# MTL Vault - Native Android Novel Library & Reader

An offline-first, native Android novel library and reader designed to store, organize, edit, and read machine-translated and custom novels. It features native Android Text-to-Speech (TTS) with paragraph-aware chunking and background playback, per-novel regex/text replacement rules, multi-strategy chapter splitting, and a ClassGrid-inspired Material 3 aesthetic.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed during Phase 1 clarification:
> - **TTS Background Playback**: Enabled with Android audio focus management, allowing continuous listening when switching apps or locking the screen, with automatic progress tracking.
> - **Initial Library State**: Fresh, clean zero-state with informative onboarding tips and quick actions to create or import novels.
> - **Color Theme & Visual System**: Forest Jade primary palette (`#2F6F4E` / `#4E9670`) paired with ClassGrid-style warm cream/dark surfaces, supporting Light, Warm Sepia, Dark, and True Black (OLED Night) reader themes.

---

## 1. Overview & Core Concept

- **What It Does**: MTL Vault provides a local-first vault for web novels and translated fiction. Users can create novels, import raw `.txt` files with intelligent chapter boundary detection, edit chapters with live statistics, read in an immersive typography-focused reader, apply search-and-replace terminology rules, and listen via Android's native `TextToSpeech` engine.
- **Target Audience**: Readers and translators of web novels, light novels, and machine-translated (MTL) literature who need an offline personal library with custom terminology corrections and hands-free listening.
- **Key Value**: Replaces unwieldy web reader workflows with a native Android experience—instant cold boot, zero network latency, persistent reading positions, smooth 60fps scrolling for multi-thousand word chapters, and background TTS.

---

## 2. User Experience & Visual Design

### Design Direction (ClassGrid Synergy)
Inspired by the ClassGrid Android reference, MTL Vault adopts:
- **Spatial Hierarchy**: Generous content paddings (16dp), rounded card contours (16dp shape radius), and distinct tonal elevation layers without heavy borders.
- **M3 Navigation**: Fixed bottom navigation bar with active pill indicators for **Library**, **Stats**, and **Settings**, seamlessly hiding when entering full-screen Reader mode.
- **Micro-interactions & Surfaces**: Material 3 ripples, subtle spring animations on sheet presentation, and tactile status indicators (word count chips, read badges, progress bars).

### Color Themes & Tokens
1. **App Shell**:
   - Light: Warm paper surface (`#FBF9F4`), card surface (`#FFFFFF`), primary forest jade (`#2F6F4E`), muted text (`#6E6656`).
   - Dark: Charcoal surface (`#161616`), card surface (`#1E1E1E`), primary jade tint (`#7DC79A`), muted text (`#A79B85`).
2. **Reader Themes**:
   - **Light**: Crisp editorial layout with Georgia/Serif and warm white background.
   - **Sepia**: Eye-friendly parchment (`#F4ECD8`) with deep brown ink (`#3A2E1A`).
   - **Dark**: Low-glare dark charcoal (`#1C1B18`) with softened cream glyphs (`#EFE9DD`).
   - **OLED Night**: Pure pitch black (`#000000`) with muted silver typography (`#B8B8B8`) to conserve battery.

### Key Screens & User Flows

```
┌────────────────────────────────────────────────────────┐
│                      Bottom Nav                        │
│        [Library]        [Stats]        [Settings]      │
└───────────┬────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────┐      ┌─────────────────────────┐
│       Library View      │      │     Novel Details       │
│  - Novel Cards          │ ───► │  - Chapter List         │
│  - Progress Bars        │      │  - Multi-select actions │
│  - + New / Import File  │      │  - Replacement Rules    │
└─────────────────────────┘      └───────────┬─────────────┘
                                             │
                       ┌─────────────────────┴─────────────────────┐
                       ▼                                           ▼
         ┌───────────────────────────┐               ┌───────────────────────────┐
         │        Reader View        │               │      Chapter Editor       │
         │ - Tap-to-toggle top/bottom│               │ - Title & long content    │
         │ - Compact TTS Bar         │               │ - Real-time word counter  │
         │ - Sepia / Night / Dark    │               │ - Discard / Save actions  │
         │ - Aa Typography Sheet     │               └───────────────────────────┘
         └───────────────────────────┘
```

1. **Library Screen**:
   - Displays novel cards showing title, author/translator, chapter count, read completion percentage, and last-read chapter bookmark.
   - Action bar for **+ New Novel**, **📁 Import File**, and **📥 Paste Import**.
   - Engaging empty-library illustration with instructions for first-time users.
2. **Novel Details Screen**:
   - Header with cover art icon, metadata, cumulative words, and reading progress bar.
   - Action chips for **Add Chapter**, **Import**, **Export .txt**, **Replacements (Rule Count)**, and **Share**.
   - Performant `LazyColumn` chapter list with read checkboxes, word counts, and long-press selection mode for bulk export/sharing.
3. **Immersive Reader Screen**:
   - Edge-to-edge reading with dynamic top bar and bottom scroll progress bar.
   - Floating compact TTS bar: `[Prev Chapter | Play / Pause | Stop | Next Chapter]` with collapsible sliders for Speech Rate (0.5x–2.5x) and Pitch.
   - `Aa` Appearance modal: Theme selection (Light/Sepia/Dark/Night), Font Size (12sp–36sp), Line Spacing (1.2–2.2), and Font Family (Serif / Sans-serif).
   - Reading position automatically memorized on scroll and restored upon return.
4. **Chapter Editor Screen**:
   - Optimized for large text handling with live word count calculation and reading time estimate.
   - Auto-titled with smart numbering (e.g., detecting "Chapter 5" to suggest "Chapter 6").
5. **Replacements Management**:
   - Per-novel terminology rules (e.g. `Elder = Senior`, `[cs] Lord = Lord God`, `[nww] mana = Mana`).
   - Live testing preview box: users paste sample text and see rules applied in real time without altering source database entries.
6. **Statistics Screen**:
   - Summary metric cards (Total Novels, Total Chapters, Chapters Read, Total Word Count, and Estimated Reading Time).
   - Per-novel progress breakdown cards.
7. **Settings Screen**:
   - Global default reader typography and reading theme presets.
   - Global toggle for text replacements.
   - JSON Full Backup export & restore (compatible with MTL Vault web export structure).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Native TextToSpeech Engine with Utterance Chunking**
  - *Chosen Approach*: Use Android's `android.speech.tts.TextToSpeech` wrapped in a lifecycle-aware manager. Text is parsed into clean paragraph/sentence boundaries (under 3,500 characters per chunk) and dispatched via sequential utterance IDs with an `UtteranceProgressListener`.
  - *Why*: Android TTS engines fail silently or throw errors when passed strings over 4,000 characters. Intelligent chunking ensures seamless narration through chapters of 10,000+ words.
  - *Alternative Considered*: Android `MediaPlayer` with audio file rendering (rejected because offline TTS on arbitrary novel text must be synthesized on-the-fly).
- **Decision 2: Room Local Database with Full Unicode & UTF-8 Support**
  - *Chosen Approach*: SQLite via Room (`NovelEntity`, `ChapterEntity`) with foreign keys, cascade deletes, and indexing on `novelId` and `orderIndex`.
  - *Why*: Web novel chapters often contain non-Latin scripts (Chinese, Japanese, Korean, Urdu, Arabic). Room with proper UTF-8 handling prevents text truncation or encoding corruption.
- **Decision 3: Non-Destructive Text Replacements**
  - *Chosen Approach*: Raw chapter text stored in Room remains pristine and untouched. Replacement rules are evaluated dynamically in-memory when loading content into the reader, TTS speech buffer, or export/share pipelines.
  - *Why*: Avoids permanent data loss or accidental regex destruction of original chapter text.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Jetpack Compose UI                            │
│  LibraryScreen │ NovelDetailScreen │ ReaderScreen │ SettingsScreen    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ UI State & User Events
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          ViewModel Layer                               │
│  LibraryViewModel │ NovelDetailViewModel │ ReaderViewModel │ StatsVM  │
└──────────────┬────────────────────┬────────────────────┬───────────────┘
               │                    │                    │
               ▼                    ▼                    ▼
┌───────────────────────────┐ ┌───────────────┐ ┌────────────────────────┐
│     NovelRepository       │ │  TTS Engine   │ │   ReplacementEngine    │
│  - AppDatabase (Room)     │ │  - TTSManager │ │  - Regex Rule Parser   │
│  - NovelDao / ChapterDao  │ │  - ChunkQueue │ │  - In-Memory Matcher   │
│  - DataStore Preferences  │ │  - AudioFocus │ │  - Whole-word Bounds   │
└───────────────────────────┘ └───────────────┘ └────────────────────────┘
```

### Database Schema (Room)

```kotlin
// Novel Entity
@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String = "",
    val translator: String = "",
    val description: String = "",
    val coverUri: String? = null,
    val lastChapterId: String? = null,
    val replacements: String = "",
    val replacementsEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// Chapter Entity
@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(
        entity = NovelEntity::class,
        parentColumns = ["id"],
        childColumns = ["novelId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["novelId"]), Index(value = ["novelId", "orderIndex"])]
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val novelId: String,
    val orderIndex: Int,
    val title: String,
    val content: String,
    val wordCount: Int,
    val scrollPosition: Int = 0,
    val read: Boolean = false,
    val readAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

### Text-to-Speech (TTS) Engine Flow
1. **Preparation**: When user taps Play, retrieve current chapter content with active novel replacement rules applied.
2. **Chunking Algorithm**:
   - Split by double-newlines (paragraphs).
   - If a paragraph exceeds 1,500 characters, split into sentences (`[.!?。！？]`).
   - If a sentence exceeds 1,500 characters, split by whitespace words.
3. **Queue Execution**:
   - Request audio focus via `AudioManager.requestAudioFocus`.
   - Dispatch first chunk with `QUEUE_FLUSH`, subsequent chunks with `QUEUE_ADD`.
   - Update `currentChunkIndex` and high-level playback state (`IDLE`, `PLAYING`, `PAUSED`).
4. **Auto-Advance**: When the final chunk triggers `onDone`, if "Continue Reading" is enabled, transition to `orderIndex + 1`, update Reader screen, and commence reading the next chapter automatically.

### File Import & Backup Strategy
- **File Import**: Storage Access Framework (`ActivityResultContracts.OpenDocument` / `GetContent`) for `.txt` files with UTF-8 decoding and interactive split previews (Auto Regex, Custom Marker, Single Chapter).
- **JSON Backup/Restore**: Matches MTL Vault's data model (`novels`, `chapters`, `settings`, `replacements`) using Moshi/Kotlinx serialization.
- **Export & Share**: Android `Intent.ACTION_SEND` with plain text or cache file provider to directly pipe chapters into TTS readers like @Voice or external apps.

---

## 5. Verification Plan

1. **Build & Compilation**: Run `compile_applet` to verify Android Gradle build, KSP code generation for Room entities and DAOs, and Compose Material 3 dependencies.
2. **CRUD & Offline DB**: Verify novel creation, chapter ordering, bulk import, chapter editing, and cascading deletions.
3. **Chapter Splitter**: Validate regex patterns against varied formats (`Chapter 1`, `Chapter 1: The Beginning`, `CHAPTER IV`, `باب 1`, `अध्याय 1`).
4. **TTS Lifecycle & Chunking**: Verify TTS initialization, chunk progression, pause/resume, pitch/speed controls, audio focus handling, and automatic progression across chapter boundaries.
5. **UI Polish & Themes**: Verify Light, Sepia, Dark, and OLED Night modes, fluid scrolling on large texts, typography size adjustments, and back press navigation.
