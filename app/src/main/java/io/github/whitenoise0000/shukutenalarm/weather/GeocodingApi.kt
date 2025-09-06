package io.github.whitenoise0000.shukutenalarm.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open‑Meteo Geocoding API インターフェース。
 * - 都市名から緯度経度を検索する（APIキー不要）。
 */
interface GeocodingApi {
    @GET("/v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("language") language: String = "ja",
        @Query("count") count: Int = 8
    ): GeocodingResponse
}

/** 検索結果レスポンス */
@Serializable
data class GeocodingResponse(
    val results: List<GeoPlace> = emptyList()
)

/** 都市候補 */
@Serializable
data class GeoPlace(
    val id: Int? = null,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("admin1") val admin1: String? = null,
    val timezone: String? = null
)
