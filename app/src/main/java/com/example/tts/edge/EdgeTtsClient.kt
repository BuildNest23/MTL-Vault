package com.example.tts.edge

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Error from the Edge speech service. [retryable] errors are tried again with a short back-off. */
class EdgeTtsException(
    message: String,
    val httpCode: Int? = null,
    val serverDate: String? = null,
    val retryable: Boolean = true
) : IOException(message)

/**
 * Talks to Microsoft's Read Aloud (Edge TTS) WebSocket endpoint and returns MP3 audio.
 * No API key or account is needed.
 *
 * Kotlin/OkHttp port of the protocol in pagina_reader's rust-core (itself a port of the Python
 * `edge-tts` package). If Microsoft ever changes the handshake, the constants below are the
 * place to update.
 */
class EdgeTtsClient(
    private val http: OkHttpClient = defaultHttpClient()
) {
    companion object {
        private const val WSS_ENDPOINT =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        private const val CHROMIUM_MAJOR_VERSION = "143"
        private const val SEC_MS_GEC_VERSION = "2-143.0.3650.75"
        private const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"
        private const val MAX_TEXT_BYTES = 4096
        private const val MAX_RETRIES = 2

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        private fun connectId(): String = UUID.randomUUID().toString().replace("-", "")

        private fun timestamp(): String {
            val fmt = SimpleDateFormat(
                "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US
            )
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.format(Date())
        }

        /** Replaces control characters the service rejects with spaces. */
        internal fun removeIncompatibleCharacters(input: String): String {
            val sb = StringBuilder(input.length)
            for (c in input) {
                val code = c.code
                if (code <= 8 || code == 11 || code == 12 || code in 14..31) sb.append(' ') else sb.append(c)
            }
            return sb.toString()
        }

        internal fun escapeXml(input: String): String =
            input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        /** Splits escaped text into pieces of at most [limit] UTF-8 bytes, never inside an XML entity. */
        internal fun splitByByteLength(text: String, limit: Int = MAX_TEXT_BYTES): List<String> {
            var remaining = text.toByteArray(Charsets.UTF_8)
            val parts = ArrayList<String>()
            while (remaining.size > limit) {
                var splitAt = -1
                var i = limit - 1
                while (i >= 0) {
                    if (remaining[i] == '\n'.code.toByte()) { splitAt = i; break }
                    i--
                }
                if (splitAt < 0) {
                    i = limit - 1
                    while (i >= 0) {
                        if (remaining[i] == ' '.code.toByte()) { splitAt = i; break }
                        i--
                    }
                }
                if (splitAt < 0) {
                    splitAt = limit
                    // do not cut a multi-byte UTF-8 character in half
                    while (splitAt > 0 && (remaining[splitAt].toInt() and 0xC0) == 0x80) splitAt--
                }
                // do not cut an XML entity such as &amp; in half
                while (splitAt > 0) {
                    var amp = -1
                    var k = splitAt - 1
                    while (k >= 0) {
                        if (remaining[k] == '&'.code.toByte()) { amp = k; break }
                        k--
                    }
                    if (amp < 0) break
                    var hasSemicolon = false
                    for (m in amp until splitAt) {
                        if (remaining[m] == ';'.code.toByte()) { hasSemicolon = true; break }
                    }
                    if (hasSemicolon) break
                    splitAt = amp
                }
                val piece = String(remaining, 0, splitAt, Charsets.UTF_8).trim()
                if (piece.isNotEmpty()) parts.add(piece)
                val advance = if (splitAt > 0) splitAt else 1
                remaining = remaining.copyOfRange(advance, remaining.size)
            }
            val tail = String(remaining, Charsets.UTF_8).trim()
            if (tail.isNotEmpty()) parts.add(tail)
            return parts
        }

        internal fun buildSsml(voice: String, rate: String, pitch: String, volume: String, escapedText: String): String {
            val bits = voice.split("-")
            val lang = if (bits.size >= 2 && bits[0].length == 2 && bits[1].length == 2) {
                bits[0] + "-" + bits[1]
            } else {
                "en-US"
            }
            return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$lang'>" +
                "<voice name='$voice'><prosody pitch='$pitch' rate='$rate' volume='$volume'>" +
                escapedText + "</prosody></voice></speak>"
        }

        /** Reads one "Name:value" header out of a service message header block. */
        private fun headerValue(headerBlock: String, name: String): String? {
            for (line in headerBlock.split("\r\n")) {
                val idx = line.indexOf(':')
                if (idx > 0 && line.substring(0, idx).trim().equals(name, ignoreCase = true)) {
                    return line.substring(idx + 1).trim()
                }
            }
            return null
        }
    }

    /**
     * Converts [text] to MP3 bytes. [rate] looks like "+20%", [pitch] like "+10Hz", [volume] like "+0%".
     * Throws [EdgeTtsException] when the service cannot be reached or returns no audio.
     */
    suspend fun synthesize(
        text: String,
        voice: String,
        rate: String = "+0%",
        pitch: String = "+0Hz",
        volume: String = "+0%"
    ): ByteArray {
        val cleaned = escapeXml(removeIncompatibleCharacters(text))
        val parts = splitByByteLength(cleaned)
        if (parts.isEmpty()) throw EdgeTtsException("Nothing to speak", retryable = false)
        val out = ByteArrayOutputStream()
        for (part in parts) {
            out.write(synthesizePart(part, voice, rate, pitch, volume))
        }
        return out.toByteArray()
    }

    private suspend fun synthesizePart(
        escapedText: String, voice: String, rate: String, pitch: String, volume: String
    ): ByteArray {
        var attempt = 0
        var skewAdjusted = false
        while (true) {
            try {
                return requestOnce(escapedText, voice, rate, pitch, volume)
            } catch (e: EdgeTtsException) {
                if (e.httpCode == 403 && !skewAdjusted) {
                    // Handshake refused: usually the phone clock is off. Learn the server time and retry.
                    EdgeDrm.adjustClockSkewFromServerDate(e.serverDate)
                    skewAdjusted = true
                    continue
                }
                if (!e.retryable || attempt >= MAX_RETRIES) throw e
                delay(minOf(300L shl attempt, 5000L))
                attempt++
            }
        }
    }

    private suspend fun requestOnce(
        escapedText: String, voice: String, rate: String, pitch: String, volume: String
    ): ByteArray = suspendCancellableCoroutine { cont ->
        val audio = ByteArrayOutputStream()
        val done = AtomicBoolean(false)
        var socket: WebSocket? = null

        fun complete() {
            if (done.compareAndSet(false, true)) {
                val bytes = synchronized(audio) { audio.toByteArray() }
                if (bytes.isEmpty()) {
                    cont.resumeWithException(EdgeTtsException("No audio was received from the Edge service"))
                } else {
                    cont.resume(bytes)
                }
            }
        }

        fun fail(error: EdgeTtsException) {
            if (done.compareAndSet(false, true)) {
                cont.resumeWithException(error)
                socket?.cancel()
            }
        }

        val url = WSS_ENDPOINT +
            "?TrustedClientToken=" + EdgeDrm.TRUSTED_CLIENT_TOKEN +
            "&ConnectionId=" + connectId() +
            "&Sec-MS-GEC=" + EdgeDrm.generateSecMsGec() +
            "&Sec-MS-GEC-Version=" + SEC_MS_GEC_VERSION

        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/$CHROMIUM_MAJOR_VERSION.0.0.0 Safari/537.36 Edg/$CHROMIUM_MAJOR_VERSION.0.0.0"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Cookie", "muid=" + EdgeDrm.generateMuid() + ";")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val configMessage = "X-Timestamp:" + timestamp() + "\r\n" +
                    "Content-Type:application/json; charset=utf-8\r\n" +
                    "Path:speech.config\r\n\r\n" +
                    "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":" +
                    "{\"sentenceBoundaryEnabled\":\"true\",\"wordBoundaryEnabled\":\"false\"}," +
                    "\"outputFormat\":\"" + OUTPUT_FORMAT + "\"}}}}\r\n"
                webSocket.send(configMessage)

                val ssml = buildSsml(voice, rate, pitch, volume, escapedText)
                val ssmlMessage = "X-RequestId:" + connectId() + "\r\n" +
                    "Content-Type:application/ssml+xml\r\n" +
                    "X-Timestamp:" + timestamp() + "Z\r\n" +
                    "Path:ssml\r\n\r\n" + ssml
                webSocket.send(ssmlMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val headerEnd = text.indexOf("\r\n\r\n")
                val headers = if (headerEnd >= 0) text.substring(0, headerEnd) else text
                if (headerValue(headers, "Path") == "turn.end") {
                    webSocket.close(1000, null)
                    complete()
                }
                // turn.start, response and audio.metadata messages are not needed for audio only
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (bytes.size < 2) {
                    fail(EdgeTtsException("Binary message is missing its header length"))
                    return
                }
                val headerLength = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
                if (headerLength + 2 > bytes.size) {
                    fail(EdgeTtsException("Binary header is longer than the message"))
                    return
                }
                val headers = bytes.substring(2, 2 + headerLength).utf8()
                if (headerValue(headers, "Path") != "audio") return
                val contentType = headerValue(headers, "Content-Type")
                val body = bytes.substring(2 + headerLength).toByteArray()
                if (contentType == "audio/mpeg" && body.isNotEmpty()) {
                    synchronized(audio) { audio.write(body) }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                complete()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                fail(
                    EdgeTtsException(
                        t.message ?: "WebSocket failure",
                        httpCode = response?.code,
                        serverDate = response?.header("Date")
                    )
                )
            }
        }

        socket = http.newWebSocket(request, listener)
        cont.invokeOnCancellation {
            done.set(true)
            socket?.cancel()
        }
    }
}
