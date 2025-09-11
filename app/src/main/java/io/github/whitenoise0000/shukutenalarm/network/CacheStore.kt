package io.github.whitenoise0000.shukutenalarm.network

import android.content.Context
import okio.IOException
import java.io.File
import java.security.MessageDigest

/**
 * HTTPボディの簡易キャッシュを行うストア。
 * - URL毎にETag/Last-Modified/本文を保存し、304 Not Modified時に復元する。
 * - 永続化先は内部ストレージ(`filesDir/httpcache`)。
 * - 本キャッシュはJMAのarea.json/forecastのみに限定して利用する想定。
 */
class CacheStore(context: Context) {
    private val dir: File = File(context.filesDir, "httpcache").apply { mkdirs() }

    data class Entry(
        val url: String,
        val etag: String?,
        val lastModified: String?,
        val body: String,
        val lastFetchedAt: Long
    )

    /** URLから安全なファイル名(SHA-256)を生成する。 */
    private fun nameOf(url: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hex = md.digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return hex + ".json"
    }

    /** キャッシュの読み出し。存在しない場合はnull。 */
    fun read(url: String): Entry? {
        return try {
            val f = File(dir, nameOf(url))
            if (!f.exists()) return null
            val text = f.readText(Charsets.UTF_8)
            // フォーマットはタブ区切り: etag\tlastModified\tlastFetched\tbody
            val idx1 = text.indexOf('\t')
            val idx2 = text.indexOf('\t', startIndex = idx1 + 1)
            val idx3 = text.indexOf('\t', startIndex = idx2 + 1)
            if (idx1 <= 0 || idx2 <= idx1 || idx3 <= idx2) return null
            val etag = text.substring(0, idx1).ifBlank { null }
            val lastMod = text.substring(idx1 + 1, idx2).ifBlank { null }
            val ts = text.substring(idx2 + 1, idx3).toLongOrNull() ?: 0L
            val body = text.substring(idx3 + 1)
            Entry(url = url, etag = etag, lastModified = lastMod, body = body, lastFetchedAt = ts)
        } catch (_: Exception) {
            null
        }
    }

    /** キャッシュの保存。 */
    fun write(url: String, etag: String?, lastModified: String?, body: String) {
        try {
            val f = File(dir, nameOf(url))
            val safeEtag = etag ?: ""
            val safeLm = lastModified ?: ""
            val now = System.currentTimeMillis()
            val text = buildString {
                append(safeEtag); append('\t')
                append(safeLm); append('\t')
                append(now.toString()); append('\t')
                append(body)
            }
            f.writeText(text, Charsets.UTF_8)
        } catch (_: IOException) {
            // 書き込み失敗時は黙って無視（機能劣化のみ）
        }
    }
}
