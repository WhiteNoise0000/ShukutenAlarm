package io.github.whitenoise0000.shukutenalarm.weather.jma

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * TELOPS（短い定型天気語句）の取得/キャッシュを担当するリポジトリ。
 * - 現段階ではアセット同梱のスナップショットを読み込み、DataStoreへキャッシュする。
 * - 将来的にオンライン抽出（R.TELOPS のスクレイピング）を追加予定。
 */
class TelopsRepository(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    @Volatile
    private var memory: Map<String, String>? = null

    /** TELOPS辞書を取得（メモリ→DataStore→アセットの順）。*/
    suspend fun getTelopsMap(): Map<String, String> = withContext(Dispatchers.IO) {
        memory?.let { return@withContext it }
        val key = stringPreferencesKey(KEY_TELOPS_CACHE)
        // DataStore から復元
        runCatching {
            val cached = context.appDataStore.data.first()[key]
            if (!cached.isNullOrBlank()) {
                val map = json.decodeFromString(TelopsMap.serializer(), cached).map
                memory = map
                return@withContext map
            }
        }
        // アセットから読み込み（初回）
        val assetText = context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val map = json.decodeFromString(TelopsMap.serializer(), assetText).map
        context.appDataStore.edit { p -> p[key] = json.encodeToString(TelopsMap.serializer(), TelopsMap(map)) }
        memory = map
        map
    }

    @Serializable
    private data class TelopsMap(val map: Map<String, String>)

    companion object {
        private const val KEY_TELOPS_CACHE = "jma_telops_cache_json"
        private const val ASSET_FILE = "jma_telops.json"
    }

    // オンライン取得はサイト構成変更の影響が大きいため当面無効化（ローカルアセット/キャッシュのみ利用）
}
