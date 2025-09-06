package io.github.whitenoise0000.shukutenalarm.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DataStoreAlarmRepository(private val context: Context) {
    private fun keyOf(id: Int) = stringPreferencesKey(PreferencesKeys.KEY_ALARM_PREFIX + id + PreferencesKeys.KEY_ALARM_SUFFIX)

    suspend fun save(spec: AlarmSpec) {
        val json = Json.encodeToString(spec)
        context.appDataStore.edit { prefs ->
            prefs[keyOf(spec.id)] = json
        }
    }

    suspend fun load(id: Int): AlarmSpec? {
        val data = context.appDataStore.data.map { prefs -> prefs[keyOf(id)] }.first()
        return data?.let { Json.decodeFromString<AlarmSpec>(it) }
    }

    suspend fun delete(id: Int) {
        context.appDataStore.edit { prefs ->
            prefs.remove(keyOf(id))
        }
    }

    suspend fun list(): List<AlarmSpec> {
        val map = context.appDataStore.data.first().asMap()
        val out = ArrayList<AlarmSpec>()
        for ((k, v) in map) {
            val name = k.name
            if (name.startsWith(PreferencesKeys.KEY_ALARM_PREFIX) && name.endsWith(PreferencesKeys.KEY_ALARM_SUFFIX)) {
                val json = v as? String ?: continue
                runCatching { Json.decodeFromString<AlarmSpec>(json) }.getOrNull()?.let { out.add(it) }
            }
        }
        return out.sortedBy { it.id }
    }
}