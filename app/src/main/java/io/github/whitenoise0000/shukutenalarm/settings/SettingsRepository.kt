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
 * 設定の読み書きや、現在地使用可否などを DataStore に保存/読み出しするリポジトリ。
 */
class SettingsRepository(private val context: Context) {
    private val latKey = doublePreferencesKey(PreferencesKeys.KEY_LAT)
    private val lonKey = doublePreferencesKey(PreferencesKeys.KEY_LON)
    private val useCurrentKey = booleanPreferencesKey(PreferencesKeys.KEY_USE_CURRENT_LOCATION)
    private val delayKey = intPreferencesKey(PreferencesKeys.KEY_DELAY_MINUTES)
    private val holidayRefreshMonthlyKey = booleanPreferencesKey("holidayRefreshMonthly")
    private val holidayRefreshWifiOnlyKey = booleanPreferencesKey("holidayRefreshWifiOnly" )
    private val cityNameKey = stringPreferencesKey(PreferencesKeys.KEY_CITY_NAME)
    private val selectedOfficeKey = stringPreferencesKey(PreferencesKeys.KEY_SELECTED_OFFICE)
    private val selectedClass10Key = stringPreferencesKey(PreferencesKeys.KEY_SELECTED_CLASS10)
    private val masterRefreshIntervalDaysKey = intPreferencesKey("masterRefreshIntervalDays")

    /** 現在の設定のストリームを取得する。*/
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
                masterRefreshIntervalDays = prefs[masterRefreshIntervalDaysKey] ?: 30,
                cityName = prefs[cityNameKey],
                selectedOffice = prefs[selectedOfficeKey],
                selectedClass10 = prefs[selectedClass10Key]
            )
        }

    /** 設定を保存する。*/
    suspend fun save(settings: UserSettings) {
        context.appDataStore.edit { prefs ->
            prefs[latKey] = settings.latitude
            prefs[lonKey] = settings.longitude
            prefs[useCurrentKey] = settings.useCurrentLocation
            prefs[delayKey] = settings.delayMinutes
            prefs[holidayRefreshMonthlyKey] = settings.holidayRefreshMonthly
            prefs[holidayRefreshWifiOnlyKey] = settings.holidayRefreshWifiOnly
            prefs[masterRefreshIntervalDaysKey] = settings.masterRefreshIntervalDays
            if (settings.cityName.isNullOrBlank()) {
                prefs.remove(cityNameKey)
            } else {
                prefs[cityNameKey] = settings.cityName
            }
            if (settings.selectedOffice.isNullOrBlank()) {
                prefs.remove(selectedOfficeKey)
            } else {
                prefs[selectedOfficeKey] = settings.selectedOffice
            }
            if (settings.selectedClass10.isNullOrBlank()) {
                prefs.remove(selectedClass10Key)
            } else {
                prefs[selectedClass10Key] = settings.selectedClass10
            }
        }
    }
}

/**
 * ユーザー設定のデータクラス。
 */
data class UserSettings(
    val latitude: Double,
    val longitude: Double,
    val useCurrentLocation: Boolean,
    val delayMinutes: Int,
    val holidayRefreshMonthly: Boolean = false,
    val holidayRefreshWifiOnly: Boolean = true,
    /** マスタ自動更新の間隔（日）。既定=30（日） */
    val masterRefreshIntervalDays: Int = 30,
    val cityName: String? = null,
    /** 都市名検索で選択したofficeコード */
    val selectedOffice: String? = null,
    /** 都市名検索で選択したclass10コード（任意） */
    val selectedClass10: String? = null
)
