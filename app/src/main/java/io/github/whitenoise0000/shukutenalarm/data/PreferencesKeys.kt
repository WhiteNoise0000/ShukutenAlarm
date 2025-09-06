package io.github.whitenoise0000.shukutenalarm.data

/**
 * DataStoreで使用するキー定義。
 * 命名は human-readable かつプレフィックスでグルーピング。
 */
object PreferencesKeys {
    /** 緯度 */
    const val KEY_LAT = "lat"
    /** 経度 */
    const val KEY_LON = "lon"
    /** 現在地を使用するかどうかのフラグ */
    const val KEY_USE_CURRENT_LOCATION = "useCurrentLocation"
    /** 遅延時間（分） */
    const val KEY_DELAY_MINUTES = "delayMinutes"
    /** アラームデータのプレフィックス */
    const val KEY_ALARM_PREFIX = "alarm_"
    /** アラームデータのサフィックス（JSON） */
    const val KEY_ALARM_SUFFIX = "_json"
    /** 最後に取得した天気情報のJSON */
    const val KEY_LAST_WEATHER_JSON = "last_weather_json"
    /** 祝日情報を最後に取得した時刻（epoch） */
    const val KEY_HOLIDAYS_LAST_FETCH = "holidays_last_fetch_epoch"
    /** 都市名 */
    const val KEY_CITY_NAME = "cityName"
    /** 1回スキップの有効期限（epochMillis）、キーは prefix + id */
    const val KEY_SKIP_UNTIL_PREFIX = "skip_until_"
}