package io.github.whitenoise0000.shukutenalarm.holiday

import android.content.Context
import androidx.datastore.preferences.core.edit
import io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 祝日データの読み込み/更新を担うリポジトリ。
 * - オフライン優先: まずローカルキャッシュ（filesDir/holidays.yml）→ なければ assets/holidays.yml。
 * - フォーマット: `YYYY-MM-DD: 祝日名` のYAMLを簡易パース。
 * - オンライン更新: 指定URLから最新YAMLを取得し、ローカルキャッシュに保存。
 */
class HolidayRepository(private val context: Context) {
    private val cacheFile: File by lazy { File(context.filesDir, CACHE_FILE_NAME) }
    @Volatile private var memoryMap: Map<String, String>? = null

    /** 指定日の祝日判定を返す。 */
    fun isHoliday(date: LocalDate): Boolean = ensureLoaded().containsKey(date.toString())

    /** 指定日の祝日名（存在しない場合は null）を返す。 */
    fun nameOrNull(date: LocalDate): String? = ensureLoaded()[date.toString()]

    /**
     * ローカルキャッシュ or assets から祝日データを読み込み、メモリに保持する。
     */
    private fun ensureLoaded(): Map<String, String> {
        memoryMap?.let { return it }
        synchronized(this) {
            memoryMap?.let { return it }
            val text = runCatching {
                if (cacheFile.exists()) cacheFile.readText(Charsets.UTF_8)
                else context.assets.open(ASSET_FILE_NAME).bufferedReader().use { it.readText() }
            }.getOrDefault("")
            val parsed = parseYaml(text)
            memoryMap = parsed
            return parsed
        }
    }

    /**
     * YAMLテキストを簡易パースして日付→名称のマップへ変換する。
     * - 行頭が `YYYY-MM-DD:` の行のみを対象とする。
     */
    private fun parseYaml(yaml: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        yaml.lineSequence().forEach { line0 ->
            val line = line0.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("---")) return@forEach
            val idx = line.indexOf(":")
            if (idx <= 0) return@forEach
            val date = line.substring(0, idx).trim()
            // 簡易バリデーション（yyyy-mm-dd）
            if (!DATE_REGEX.matches(date)) return@forEach
            val name = line.substring(idx + 1).trim()
            if (name.isNotEmpty()) map[date] = name
        }
        return map
    }

    /**
     * 一定間隔ごとにオンライン更新を試みる（成功時はローカルキャッシュを更新）。
     * @param maxAgeDays この日数を超えていたら更新する（デフォルト30日）。
     */
    suspend fun refreshIfStale(maxAgeDays: Int = 30, url: String = DEFAULT_YAML_URL) {
        val key = androidx.datastore.preferences.core.longPreferencesKey(PreferencesKeys.KEY_HOLIDAYS_LAST_FETCH)
        val last = context.appDataStore.data.map { it[key] ?: 0L }.first()
        val now = System.currentTimeMillis()
        val maxAgeMs = TimeUnit.DAYS.toMillis(maxAgeDays.toLong())
        if (now - last < maxAgeMs) return
        runCatching { fetchAndCache(url) }.onSuccess {
            // 成功時のみ更新
            context.appDataStore.edit { prefs -> prefs[key] = now }
        }
    }

    /** 最新のYAMLを取得してキャッシュへ保存（失敗は例外）。*/
    suspend fun forceRefresh(url: String = DEFAULT_YAML_URL) {
        fetchAndCache(url)
        val key = androidx.datastore.preferences.core.longPreferencesKey(PreferencesKeys.KEY_HOLIDAYS_LAST_FETCH)
        val now = System.currentTimeMillis()
        context.appDataStore.edit { prefs -> prefs[key] = now }
    }

    private suspend fun fetchAndCache(url: String) = withContext(Dispatchers.IO) {
        val client = httpClient
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code}")
            val body = res.body?.string() ?: error("empty body")
            // パースできることを確認してから書き込み
            val parsed = parseYaml(body)
            if (parsed.isEmpty()) error("empty parsed map")
            cacheFile.writeText(body, Charsets.UTF_8)
            memoryMap = parsed
        }
    }

    private val httpClient: OkHttpClient by lazy {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logger)
            .readTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val ASSET_FILE_NAME = "holidays.yml"
        private const val CACHE_FILE_NAME = "holidays.yml"
        private val DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        // holiday-jp 公式リポジトリのrawファイル
        const val DEFAULT_YAML_URL = "https://raw.githubusercontent.com/holiday-jp/holiday_jp/master/holidays.yml"
    }
}
