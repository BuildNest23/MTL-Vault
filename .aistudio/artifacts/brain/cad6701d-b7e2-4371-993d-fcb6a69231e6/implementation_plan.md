# Performance & Architecture Improvements Plan

A 6-step plan to significantly boost reading performance, smooth scroll framerate, reduce database writes, ensure backward-compatible schema migrations, optimize library queries, and modernize build configuration without changing existing UI design, gestures, or visual themes.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Execution Strategy**:
> - As confirmed, we will execute **Step 1 first**, run verification in the emulator, and await your confirmation before moving to each subsequent step.
> - **Paragraph parsing**: Using the standard novel paragraph parser (`TtsManager.parseParagraphs`) with stable item keys.

---

### Risk & Compatibility Analysis ("Will this break anything?")

| Step | Scope | Potential Risk | Mitigation in Plan |
| :--- | :--- | :--- | :--- |
| **Step 1: Reader LazyColumn** | `ReaderScreen.kt` | Item index offset (+1 for title) breaking TTS highlight or scroll-to-paragraph; gesture capture interference (single-tap menu toggle vs. double-tap TTS) | Title is at index `0`, paragraphs at `1..N`, footer at `N+1`. TTS index `i` cleanly maps to `i + 1`. `combinedClickable` preserved on each item and background click. |
| **Step 2: Debounced Position Save** | `ReaderScreen.kt`, `ReaderViewModel.kt` | Database write lag if user quickly exits app | `snapshotFlow` debounced to 800ms while scrolling; immediate save on `DisposableEffect` (screen exit) and `ViewModel.onCleared`. |
| **Step 3: Position Restore & Migration** | `AppDatabase.kt`, `ChapterEntity.kt`, Backup/Restore | Room schema failure if `exportSchema = true` without KSP schema directory; database wipe if migration is missing; breaking old backups | Configure Room schema location; implement strict `Migration(1, 2)` adding default `0` columns; remove `fallbackToDestructiveMigration`; backward-compatible JSON parser handling missing fields. |
| **Step 4: Library Query Optimization** | `NovelRepository.kt`, `NovelDao.kt` | Missing Flow updates when chapters marked as read | Single SQL query with `LEFT JOIN` and `GROUP BY` that Room observes on both `novels` and `chapters` tables, reactively refreshing on any read status change. |
| **Step 5: Replacement Caching** | `ReplacementEngine.kt` | Memory leak or stale text when novel rules change | Thread-safe LRU cache with eviction keyed by `(novelId, rulesHash)`. Explicit invalidation when rules are updated. |
| **Step 6: Build Config & Proguard** | `app/build.gradle.kts`, `proguard-rules.pro` | R8 stripping Room DAOs, Entities, or TTS Service | Add explicit `@Keep` and Proguard rules for Room, Kotlin serialization, and MediaSession services. Fall back release signing to debug keys when env vars are missing. |

---

### System Architecture & Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                       ReaderScreen                          │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ LazyColumn (LazyListState)                            │  │
│  │  ├─ Item 0: Chapter Title (key = "header_title")      │  │
│  │  ├─ Items 1..N: Paragraphs (key = "para_idx")         │  │
│  │  │   • Double-tap: Play TTS from paragraph            │  │
│  │  │   • Single-tap: Toggle reading controls            │  │
│  │  │   • Active TTS highlight + Auto-scroll into view   │  │
│  │  └─ Item N+1: Prev / Next Buttons                     │  │
│  └──────────────────────────┬────────────────────────────┘  │
│                             │                               │
│              derivedStateOf │ snapshotFlow (debounce 800ms) │
│                             ▼                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                  ReaderViewModel                      │  │
│  │  • Save paragraph index + offset (survives font size) │  │
│  │  • Single 85% read trigger without coroutine spam     │  │
│  └──────────────────────────┬────────────────────────────┘  │
└─────────────────────────────┼───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                        AppDatabase                          │
│  • Migration(1, 2): scrollParagraph & scrollOffset          │
│  • No destructive migration — library data 100% safe        │
└─────────────────────────────────────────────────────────────┘
```

---

### Step-by-Step Implementation Roadmap

#### Step 1: Reader Scroll Performance (`ReaderScreen.kt`) — *Executing Now*
- Replace `Column` + `verticalScroll` with a high-performance `LazyColumn(state = lazyListState)`.
- Use stable keys: `"header_title"`, `"para_$index"`, and `"footer_nav"`.
- `remember` parsed paragraphs, typography styles, and theme highlight colors to prevent recomposition churn.
- Compute reading progress using `derivedStateOf` from `firstVisibleItemIndex` and `firstVisibleItemScrollOffset`.
- When TTS moves to a new chunk index, auto-scroll to `index + 1` with `animateScrollToItem`, but **only if off-screen**.
- Retain all gesture interactions: single tap on content/background toggles controls; double-tap on paragraph triggers TTS.

#### Step 2: Efficient Reading Position Saving (`ReaderScreen.kt`, `ReaderViewModel.kt`)
- Throttle database position updates with `snapshotFlow` + `debounce(800ms)` + `distinctUntilChanged`.
- Save immediately on screen exit (`DisposableEffect`) and ViewModel cleanup.
- Track 85% reading progress flag in memory so mark-as-read executes exactly once per chapter.

#### Step 3: Reliable Position Restore & Room Migration (v1 ➔ v2)
- Add `scrollParagraph: Int = 0` and `scrollOffset: Int = 0` to `ChapterEntity`.
- Create `Migration(1, 2)`:
  `ALTER TABLE chapters ADD COLUMN scrollParagraph INTEGER NOT NULL DEFAULT 0`
  `ALTER TABLE chapters ADD COLUMN scrollOffset INTEGER NOT NULL DEFAULT 0`
- Remove `fallbackToDestructiveMigration` so user databases are never wiped.
- Restore position only after paragraph list is populated and non-empty.
- Update backup/restore JSON handlers to handle older backups safely with fallback defaults.

#### Step 4: Library Query Optimization (`NovelRepository.kt`, `NovelDao.kt`)
- Eliminate N+1 loop queries in `getNovelSummaries`.
- Implement a single SQL `SELECT` with `LEFT JOIN chapters GROUP BY novels.id` returning total chapters, read chapters, word count, and last chapter info.
- Verify reactive Flow emission when a chapter's read status is updated.

#### Step 5: Text Replacement Caching (`ReplacementEngine.kt`)
- Implement an in-memory cache for parsed rules and compiled regex `Pattern` instances.
- Cache processed chapter content keyed by `(chapterId, rulesVersion)`.
- Evict/invalidate cache on novel rule updates or replacement toggle.

#### Step 6: Production Build Config & Proguard (`app/build.gradle.kts`)
- Enable R8 shrinking and minification (`isMinifyEnabled = true`, `isShrinkResources = true`).
- Add keep rules for Room entities, DAOs, Kotlin serialization, and TTS service components.
- Make release signing gracefully fall back to debug signing when environment variables are unset.
- Safely clean up unused Google Services/Firebase plugins.

---

### Step 1 Verification Checklist (For Emulator Testing)
1. **Smooth Scrolling**: Open any chapter with 100+ paragraphs and fling-scroll up and down. Confirm zero frame drops or stutter.
2. **Reading Controls Toggle**: Single-tap anywhere on the text or between paragraphs to toggle the top bar and bottom reader controls on/off.
3. **TTS Double-Tap**: Double-tap any paragraph to verify TTS starts playing from that exact paragraph with the highlight box and speaker icon visible.
4. **TTS Auto-Scroll**: Let TTS advance to a paragraph off-screen; verify the reader smoothly scrolls to bring the active paragraph into view.
5. **Theme & Font Consistency**: Switch between Light, Dark, Sepia, and Night themes, and toggle Serif/Sans fonts; verify paragraph styling adapts instantly.
