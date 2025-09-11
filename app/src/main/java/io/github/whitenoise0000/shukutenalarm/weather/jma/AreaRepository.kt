package io.github.whitenoise0000.shukutenalarm.weather.jma

import android.content.Context
import androidx.datastore.preferences.core.edit
import io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * area.json の取得・キャッシュ・親子解決・検索を提供するリポジトリ。
 * - HTTPのETag/IMSキャッシュはOkHttpインターセプタ側で実施。
 * - 本クラスではメモリキャッシュと検索/親子マッピングを提供。
 */
class AreaRepository(
    private val context: Context,
    private val constApi: JmaConstApi
) {
    @Volatile
    private var areaCache: AreaMaster? = null
    private val lock = Mutex()

    /** area.json を取得（メモリキャッシュ有）。*/
    suspend fun getAreaMaster(forceRefresh: Boolean = false): AreaMaster = lock.withLock {
        if (!forceRefresh) areaCache?.let { return it }
        val fetched = constApi.area()
        areaCache = fetched
        return fetched
    }

    /** area.json を強制的に取得し、最終取得時刻を記録する（ETag適用で304の場合も本文復元で200扱い）。*/
    suspend fun refreshMaster(): AreaMaster = lock.withLock {
        val fetched = constApi.area()
        areaCache = fetched
        val now = System.currentTimeMillis()
        context.appDataStore.edit { prefs ->
            val key = androidx.datastore.preferences.core.longPreferencesKey(PreferencesKeys.KEY_AREA_LAST_FETCH)
            prefs[key] = now
        }
        return fetched
    }

    /** class20コードから class10コード と officeコード を解決。*/
    suspend fun resolveClass10AndOfficeFromClass20(class20: String): Pair<String, String>? {
        val area = getAreaMaster()
        val c20 = area.class20s[class20] ?: return null
        val childCode = c20.parent // 例: 130011
        // 親のclass10をchildrenにchildCodeを含むものから探索
        val class10Entry = area.class10s.entries.firstOrNull { (_, v) -> v.children.contains(childCode) }
        val class10Code = class10Entry?.key ?: return null
        val officeCode = class10Entry.value.parent
        return class10Code to officeCode
    }

    /** オフィス名/クラス20名の部分一致で検索。*/
    suspend fun searchByName(query: String): List<SearchResult> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val area = getAreaMaster()
        val list = mutableListOf<SearchResult>()
        area.offices.forEach { (code, v) ->
            if (v.name.contains(q)) list += SearchResult.Office(code, v.name)
        }
        area.class20s.forEach { (code, v) ->
            if (v.name.contains(q)) {
                val resolved = resolveClass10AndOfficeFromClass20(code)
                list += SearchResult.Class20(code, v.name, resolved?.first, resolved?.second)
            }
        }
        return list
    }

    /** 検索結果の型。*/
    sealed class SearchResult {
        data class Office(val code: String, val name: String) : SearchResult()
        data class Class20(
            val code: String,
            val name: String,
            val class10Code: String?,
            val officeCode: String?
        ) : SearchResult()
    }
}
