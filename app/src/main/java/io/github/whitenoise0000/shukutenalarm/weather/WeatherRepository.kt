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
 * - 取得後はDataStoreに直近カテゴリを保存（ForecastRepository側に委譲）。
 */
class WeatherRepository(
    private val context: Context,
    private val forecastApi: JmaForecastApi,
    private val gsiApi: GsiApi,
    private val areaRepository: AreaRepository
) {
    private val forecastRepo by lazy { ForecastRepository(context, forecastApi) }
    private val mapper by lazy { JmaMapper(areaRepository) }

    /** 現在地連動で予報カテゴリを取得して保存。*/
    suspend fun prefetchByCurrentLocation(lat: Double, lon: Double): WeatherCategory? {
        // muniCd取得
        val gsi = gsiApi.lonLatToAddress(lat = lat, lon = lon)
        val muniCd = gsi.results.muniCd
        val resolved = mapper.resolveFromMuniCd(muniCd) ?: return null
        val class10 = resolved.second
        val office = resolved.third
        val cat = forecastRepo.fetchCategory(office = office, class10 = class10)
        forecastRepo.cacheCategory(cat)
        return cat
    }

    /** office/class10を用いて予報カテゴリを取得して保存。*/
    suspend fun prefetchByOffice(office: String, class10OrNull: String? = null): WeatherCategory? {
        val class10 = class10OrNull ?: run {
            val area = areaRepository.getAreaMaster()
            area.offices[office]?.children?.firstOrNull()
        } ?: return null
        val cat = forecastRepo.fetchCategory(office = office, class10 = class10)
        forecastRepo.cacheCategory(cat)
        return cat
    }
}

