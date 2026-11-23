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

/**
 * Runs the Tasker action: saves the given text as the next chapter of the chosen novel.
 *
 * The chapter title is numbered by the app ("Chapter 2527" after the highest chapter already
 * in the novel), so chapters always go in ascending order. A leading "Chapter N: ..." line in the
 * text is dropped from the body because the title already carries the number.
 */
class TaskerFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskerPlugin.ACTION_FIRE_SETTING) return
        val bundle = intent.getBundleExtra(TaskerPlugin.EXTRA_BUNDLE) ?: return
        val novelId = bundle.getString(TaskerPlugin.KEY_NOVEL_ID).orEmpty()
        val rawText = bundle.getString(TaskerPlugin.KEY_TEXT).orEmpty().trim()
        val app = context.applicationContext as? MtlVaultApplication ?: return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val message = try {
                save(app, novelId, rawText)
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

    private suspend fun save(app: MtlVaultApplication, novelId: String, rawText: String): String {
        if (novelId.isBlank()) return "MTL Vault: no novel selected in the Tasker action"
        if (rawText.isBlank() || TaskerPlugin.looksUnresolved(rawText)) {
            return "MTL Vault: no chapter text received (is the Tasker variable set?)"
        }

        val repository = app.novelRepository
        val novel = repository.getNovelByIdSync(novelId)
            ?: return "MTL Vault: that novel no longer exists. Pick it again in the Tasker action"

        val title = repository.getNextDefaultChapterTitle(novelId)
        val body = TaskerPlugin.stripHeading(rawText)
        repository.addChapter(novelId, title, body)
        return "Saved to \"${novel.title}\": $title"
    }

    private companion object {
        const val TAG = "TaskerFireReceiver"
    }
}
