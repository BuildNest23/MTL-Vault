# 📖 MTL Vault

> **Save • Read • Listen**
> An offline-first novel library, reader, and text-to-speech app for Android, built with Kotlin, Jetpack Compose and Material 3.

MTL Vault keeps your machine-translated (or any other) web novels in one private library on your phone. Import chapters, clean up the text with your own replacement rules, read in a comfortable reader, or just press play and listen.

---

## 📥 Download

Get the latest installable APK from the **(littlevase/MTL-Vault)** page.

1. Open the newest release and download `MTL-Vault-vX.Y.apk`.
2. Open the file on your phone and allow "Install from this source" if Android asks.
3. Updates install over the old version and keep your library, as long as you install APKs from the same releases.

**Requirements:** Android 7.0 (API 24) or newer.

---

## ✨ Features

### Library
- Stores everything on your device (Room database). No account, no cloud, 100% private.
- Add novels by importing files, pasting text, or creating chapters by hand.
- Chapter list with read/unread marks, word counts, estimated reading time and multi-select actions.
- Hide chapters you have already read.

### Importing and exporting
- **EPUB:** reads title, author and description, and extracts chapters in the correct order.
- **TXT:** splits long files into chapters automatically. Recognises headings such as `Chapter 1`, `Chapitre`, `Capítulo`, `باب`, `अध्याय` and more.
- **Paste text:** add a chapter straight from the clipboard.
- **Export to EPUB:** export a whole novel or only the chapters you select.

### Reader
- Four themes: Light, Warm Sepia, Dark Charcoal and True Black (OLED).
- Adjustable font size, line height, Serif or Sans-serif, and maximum text width for tablets and foldables.
- Remembers your place in every chapter and shows your progress.
- Built-in chapter editor for fixing typos.

### Text-to-speech
- Uses your phone's own Android text-to-speech engine and voices.
- Reads long chapters smoothly by splitting them into paragraphs and sentences, and highlights the part being read.
- Continues automatically into the next chapter.
- Adjustable speed (0.5x to 2.5x) and pitch.
- Keeps playing with the screen off and shows controls in the notification panel and on the lock screen.
- **Notification next/previous buttons move line by line.** Use the reader to change chapters. At the very end or start of a chapter they move to the next or previous chapter.
- Pauses on incoming calls and when headphones are unplugged.
- **Resume where you left off:** if you close the app from the recent apps list while it is reading, opening it again takes you back to the chapter that is playing. Press back to see that novel's chapter list.
- **Now-playing marker:** in the chapter list, the chapter being read aloud has a play icon (a pause icon when paused) and a highlighted title.

### Text replacements
- Per-novel rules to fix names, terms and wording, for example `Mc = Main Character`.
- Rule options: `[cs]` case-sensitive, `[nww]` match inside words, `[ww]` whole word only, `[re]` regular expression. Lines starting with `#` are comments.
- Applied while reading and listening only. Your original text is never changed.
- Built-in test box to preview a rule before you use it.

### Stats
- A statistics screen to see how much you have read.

### Backup and restore
- Export your whole library to a JSON file and import it again later or on another phone.
- Compatible with web MTL Vault backups.

### Diagnostics
- Optional detailed log you can share or save to a file when something goes wrong.
