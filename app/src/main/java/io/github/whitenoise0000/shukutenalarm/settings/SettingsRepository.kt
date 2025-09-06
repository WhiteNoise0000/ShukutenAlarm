package io.github.whitenoise0000.shukutenalarm.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 險ｭ螳夲ｼ育ｷｯ蠎ｦ/邨悟ｺｦ縲∫樟蝨ｨ蝨ｰ菴ｿ逕ｨ蜿ｯ蜷ｦ・峨ｒ DataStore 縺ｫ菫晏ｭ・蜿門ｾ励☆繧九Μ繝昴ず繝医Μ縲・ */
class SettingsRepository(private val context: Context) {
    private val latKey = doublePreferencesKey(PreferencesKeys.KEY_LAT)
    private val lonKey = doublePreferencesKey(PreferencesKeys.KEY_LON)
    private val useCurrentKey = booleanPreferencesKey(PreferencesKeys.KEY_USE_CURRENT_LOCATION)
    private val delayKey = intPreferencesKey(PreferencesKeys.KEY_DELAY_MINUTES)
    private val holidayRefreshMonthlyKey = booleanPreferencesKey("holidayRefreshMonthly")
    private val holidayRefreshWifiOnlyKey = booleanPreferencesKey("holidayRefreshWifiOnly" )
    private val cityNameKey = stringPreferencesKey(PreferencesKeys.KEY_CITY_NAME)

    /** 迴ｾ蝨ｨ縺ｮ險ｭ螳壹せ繝医Μ繝ｼ繝繧定ｿ斐☆縲・*/
    val settingsFlow: Flow<UserSettings> = context.appDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            UserSettings(
                latitude = prefs[latKey] ?: 35.0,
                longitude = prefs[lonKey] ?: 135.0,
                useCurrentLocation = prefs[useCurrentKey] ?: false,
                delayMinutes = prefs[delayKey] ?: 60,
                holidayRefreshMonthly = prefs[holidayRefreshMonthlyKey] ?: false,
                holidayRefreshWifiOnly = prefs[holidayRefreshWifiOnlyKey] ?: true,
                cityName = prefs[cityNameKey]
            )
        }

    /** 險ｭ螳壹ｒ菫晏ｭ倥☆繧九・*/
    suspend fun save(settings: UserSettings) {
        context.appDataStore.edit { prefs ->
            prefs[latKey] = settings.latitude
            prefs[lonKey] = settings.longitude
            prefs[useCurrentKey] = settings.useCurrentLocation
            prefs[delayKey] = settings.delayMinutes
            prefs[holidayRefreshMonthlyKey] = settings.holidayRefreshMonthly
            prefs[holidayRefreshWifiOnlyKey] = settings.holidayRefreshWifiOnly
            prefs[cityNameKey] = settings.cityName ?: ""
        }
    }
}

/**
 * 繝ｦ繝ｼ繧ｶ險ｭ螳壹・繝・・繧ｿ繧ｯ繝ｩ繧ｹ縲・ */
data class UserSettings(
    val latitude: Double,
    val longitude: Double,
    val useCurrentLocation: Boolean,
    val delayMinutes: Int,
    val holidayRefreshMonthly: Boolean = false,
    val holidayRefreshWifiOnly: Boolean = true,
    val cityName: String? = null
)


