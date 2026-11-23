package com.example.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.MtlVaultApplication
import com.example.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs the Tasker action: saves the given text as a chapter of the chosen novel.
 *
 * - If Tasker passes the website's chapter number, the chapter is titled "Chapter N" and skipped
 *   when the novel already has a chapter N (so a repeated tap can never create a duplicate).
 * - If no number is passed, the app numbers it itself: one more than the highest chapter.
 * - A leading "Chapter N: ..." line in the text is dropped from the body, because the title
 *   already carries the number.
 * - Saves run one at a time, in the order they arrive, so a catch-up run keeps chapters in order.
 */
class TaskerFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskerPlugin.ACTION_FIRE_SETTING) return
        val bundle = intent.getBundleExtra(TaskerPlugin.EXTRA_BUNDLE) ?: return
        val novelId = bundle.getString(TaskerPlugin.KEY_NOVEL_ID).orEmpty()
        val rawText = bundle.getString(TaskerPlugin.KEY_TEXT).orEmpty().trim()
        val chapter = TaskerPlugin.parseChapterNumber(bundle.getString(TaskerPlugin.KEY_CHAPTER))
        val app = context.applicationContext as? MtlVaultApplication ?: return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val message = try {
                saveMutex.withLock { save(app, novelId, rawText, chapter) }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Tasker save failed", e)
                "Could not save chapter: ${e.message ?: "unknown error"}"
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(app, message, Toast.LENGTH_LONG).show()
            }
            pending.finish()
        }
    }

    private suspend fun save(app: MtlVaultApplication, novelId: String, rawText: String, chapter: Int?): String {
        if (novelId.isBlank()) return "MTL Vault: no novel selected in the Tasker action"
        if (rawText.isBlank() || TaskerPlugin.looksUnresolved(rawText)) {
            return "MTL Vault: no chapter text received (is the Tasker variable set?)"
        }

        val repository = app.novelRepository
        val novel = repository.getNovelByIdSync(novelId)
            ?: return "MTL Vault: that novel no longer exists. Pick it again in the Tasker action"

        val body = TaskerPlugin.stripHeading(rawText)

        if (chapter == null) {
            val title = repository.getNextDefaultChapterTitle(novelId)
            repository.addChapter(novelId, title, body)
            return "Saved to \"${novel.title}\": $title"
        }

        val existing = TaskerPlugin.chapterNumbersIn(app.database.chapterDao().getChapterTitles(novelId))
        if (chapter in existing) return "Chapter $chapter is already in \"${novel.title}\" (skipped)"

        repository.addChapter(novelId, "Chapter $chapter", body)
        val highest = existing.maxOrNull()
        return if (highest != null && highest > chapter) {
            "Saved Chapter $chapter to \"${novel.title}\", but the novel already has Chapter $highest, so it was added at the end"
        } else {
            "Saved to \"${novel.title}\": Chapter $chapter"
        }
    }

    private companion object {
        const val TAG = "TaskerFireReceiver"
        val saveMutex = Mutex()
    }
}
