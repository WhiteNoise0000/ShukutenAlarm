package io.github.whitenoise0000.shukutenalarm.weather

import android.content.Context
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import io.github.whitenoise0000.shukutenalarm.weather.jma.AreaRepository
import io.github.whitenoise0000.shukutenalarm.weather.jma.ForecastRepository
import io.github.whitenoise0000.shukutenalarm.weather.jma.GsiApi
import io.github.whitenoise0000.shukutenalarm.weather.jma.JmaForecastApi
import io.github.whitenoise0000.shukutenalarm.weather.jma.JmaMapper

/**
 * 天気取得の上位調停リポジトリ（JMA仕様）。
 * - 現在地連動時: GSI逆ジオ→muniCd→class20→class10→office→予報取得
 * - 都市名検索選択時: office/class10指定で予報取得（class10未指定ならoffice配下の先頭を使用）
 * - 取得後は DataStore に直近のカテゴリとJMA文言を保存（ForecastRepository側に委譲）。
 */
class WeatherRepository(
    private val context: Context,
    private val forecastApi: JmaForecastApi,
    private val gsiApi: GsiApi,
    private val areaRepository: AreaRepository
) {
    private val forecastRepo by lazy { ForecastRepository(context, forecastApi) }
    private val mapper by lazy { JmaMapper(areaRepository) }

    /**
     * 現在地連動で予報を取得して保存。
     * 返り値はカテゴリとJMAの天気文言（可能な場合）のスナップショット。
     */
    suspend fun prefetchByCurrentLocation(lat: Double, lon: Double): WeatherSnapshot? {
        // muniCd取得
        val gsi = gsiApi.lonLatToAddress(lat = lat, lon = lon)
        val muniCd = gsi.results.muniCd
        val resolved = mapper.resolveFromMuniCd(muniCd) ?: return null
        val class10 = resolved.second
        val office = resolved.third
        val snap = forecastRepo.fetchCategoryAndText(office = office, class10 = class10)
        forecastRepo.cacheSnapshot(snap.category, snap.text)
        return snap
    }

    /**
     * office/class10を用いて予報を取得して保存。
     * 返り値はカテゴリとJMAの天気文言（可能な場合）のスナップショット。
     */
    suspend fun prefetchByOffice(office: String, class10OrNull: String? = null): WeatherSnapshot? {
        val class10 = class10OrNull ?: run {
            val area = areaRepository.getAreaMaster()
            area.offices[office]?.children?.firstOrNull()
        } ?: return null
        val snap = forecastRepo.fetchCategoryAndText(office = office, class10 = class10)
        forecastRepo.cacheSnapshot(snap.category, snap.text)
        return snap
    }
}

/**
 * 予報取得結果のスナップショット。
 * - category: アプリ内マッピングで使用する天気カテゴリ
 * - text: 気象庁発表の天気文言（例: 「くもり時々雨」）。取得できない場合は null
 */
data class WeatherSnapshot(
    val category: WeatherCategory?,
    val text: String?
)
