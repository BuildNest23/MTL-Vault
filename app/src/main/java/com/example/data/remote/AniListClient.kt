package com.example.data.remote

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Metadata found online for a novel. */
data class NovelMetadata(
    val title: String,
    val description: String,
    val coverUrl: String?,
    val author: String
)

/**
 * Free AniList GraphQL client (no API key required) used to look up a novel's
 * cover image and description by title.
 */
object AniListClient {

    private const val ENDPOINT = "https://graphql.anilist.co"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val QUERY = """
        query (${'$'}search: String) {
          Page(perPage: 8) {
            media(search: ${'$'}search, type: MANGA, format: NOVEL) {
              title { romaji english native }
              synonyms
              description(asHtml: false)
              coverImage { extraLarge large }
              staff(perPage: 3) { edges { role node { name { full } } } }
            }
          }
        }
    """

    /** Returns the best match for [title], or null if nothing similar was found. */
    suspend fun search(title: String): NovelMetadata? = withContext(Dispatchers.IO) {
        val query = title.trim()
        if (query.isEmpty()) return@withContext null

        val body = JSONObject()
            .put("query", QUERY)
            .put("variables", JSONObject().put("search", query))
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Accept", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 429) throw RateLimitException()
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body?.string() ?: return@withContext null)
            val media = json.optJSONObject("data")
                ?.optJSONObject("Page")
                ?.optJSONArray("media") ?: return@withContext null

            var best: JSONObject? = null
            var bestScore = 0.0
            for (i in 0 until media.length()) {
                val item = media.getJSONObject(i)
                val score = scoreMatch(query, item)
                if (score > bestScore) {
                    bestScore = score
                    best = item
                }
            }
            // Ignore weak matches so we never attach a wrong cover.
            if (best == null || bestScore < 0.6) return@withContext null
            toMetadata(best)
        }
    }

    /** Downloads [url] into [target]. Returns true on success. */
    suspend fun downloadImage(url: String, target: File): Boolean = withContext(Dispatchers.IO) {
        try {
            target.parentFile?.mkdirs()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val source = response.body?.byteStream() ?: return@withContext false
                target.outputStream().use { out -> source.copyTo(out) }
                target.length() > 0
            }
        } catch (e: Exception) {
            target.delete()
            false
        }
    }

    class RateLimitException : Exception("AniList rate limit reached, try again in a minute.")

    // ---- helpers -----------------------------------------------------------

    private fun toMetadata(item: JSONObject): NovelMetadata {
        val titles = item.optJSONObject("title")
        val name = titles?.optString("english").orEmpty().ifBlank { titles?.optString("romaji").orEmpty() }
        val cover = item.optJSONObject("coverImage")?.let {
            it.optString("extraLarge").ifBlank { it.optString("large") }
        }?.takeIf { it.isNotBlank() }

        var author = ""
        val edges = item.optJSONObject("staff")?.optJSONArray("edges")
        if (edges != null) {
            for (i in 0 until edges.length()) {
                val edge = edges.getJSONObject(i)
                val role = edge.optString("role")
                if (role.contains("Story", ignoreCase = true)) {
                    author = edge.optJSONObject("node")?.optJSONObject("name")?.optString("full").orEmpty()
                    break
                }
            }
        }

        return NovelMetadata(
            title = name,
            description = cleanDescription(item.optString("description")),
            coverUrl = cover,
            author = author
        )
    }

    private fun cleanDescription(raw: String): String {
        if (raw.isBlank() || raw == "null") return ""
        val withBreaks = raw.replace(Regex("(?i)<br\\s*/?>"), "\n")
        return Html.fromHtml(withBreaks, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun candidateTitles(item: JSONObject): List<String> {
        val out = mutableListOf<String>()
        item.optJSONObject("title")?.let { t ->
            listOf("english", "romaji", "native").forEach { k ->
                t.optString(k).takeIf { it.isNotBlank() && it != "null" }?.let(out::add)
            }
        }
        val syn: JSONArray? = item.optJSONArray("synonyms")
        if (syn != null) for (i in 0 until syn.length()) out.add(syn.optString(i))
        return out
    }

    /** 1.0 = exact title match, otherwise word-overlap ratio (0..0.95). */
    private fun scoreMatch(query: String, item: JSONObject): Double {
        val q = normalize(query)
        val qWords = q.split(" ").filter { it.isNotEmpty() }.toSet()
        if (qWords.isEmpty()) return 0.0
        var best = 0.0
        for (candidate in candidateTitles(item)) {
            val c = normalize(candidate)
            if (c == q) return 1.0
            val cWords = c.split(" ").filter { it.isNotEmpty() }.toSet()
            if (cWords.isEmpty()) continue
            val overlap = qWords.intersect(cWords).size.toDouble()
            val score = 0.95 * overlap / maxOf(qWords.size, cWords.size)
            if (score > best) best = score
        }
        return best
    }
}
