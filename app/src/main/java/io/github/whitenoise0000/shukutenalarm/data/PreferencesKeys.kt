package io.github.whitenoise0000.shukutenalarm.data

/**
 * DataStoreで使用するキーの定義。
 * できるだけhuman-readableなプレフィックスでグルーピング。
 */
object PreferencesKeys {
    /** 緯度 */
    const val KEY_LAT = "lat"
    /** 経度 */
    const val KEY_LON = "lon"
    /** 現在地を使用するかどうかのフラグ */
    const val KEY_USE_CURRENT_LOCATION = "useCurrentLocation"
    /** 遅延分（分） */
    const val KEY_DELAY_MINUTES = "delayMinutes"
    /** アラームデータのプレフィックス */
    const val KEY_ALARM_PREFIX = "alarm_"
    /** アラームデータのサフィックス（JSON） */
    const val KEY_ALARM_SUFFIX = "_json"
    /** 直近に取得した天気のJSON */
    const val KEY_LAST_WEATHER_JSON = "last_weather_json"
    /** 祝日情報の最終取得時刻（epoch） */
    const val KEY_HOLIDAYS_LAST_FETCH = "holidays_last_fetch_epoch"
    /** 都市名 */
    const val KEY_CITY_NAME = "cityName"
    /** 選択済みofficeコード（都市名検索の保存先） */
    const val KEY_SELECTED_OFFICE = "selectedOffice"
    /** 選択済みclass10コード（未設定時はoffice配下の先頭を使用） */
    const val KEY_SELECTED_CLASS10 = "selectedClass10"
    /** 1度スキップの有効期限（epochMillis）,キーは prefix + id */
    const val KEY_SKIP_UNTIL_PREFIX = "skip_until_"
    /** area.json の最終取得時刻（epochMillis） */
    const val KEY_AREA_LAST_FETCH = "area_last_fetch_epoch"
}
