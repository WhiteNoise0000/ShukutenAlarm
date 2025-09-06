package io.github.whitenoise0000.shukutenalarm.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open‑Meteo の JMA エンドポイント定義（Retrofit）。
 * - APIキー不要（個人利用・仕様準拠）。
 */
interface OpenMeteoApi {
    @GET("/v1/jma")
    suspend fun jma(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("hourly") hourly: String = "weathercode,precipitation_probability",
        @Query("timezone") tz: String = "Asia/Tokyo"
    ): JmaResponse
}

/**
 * 最低限のレスポンスモデル（天気コードと降水確率）。
 */
@Serializable
data class JmaResponse(
    val hourly: Hourly
)

@Serializable
data class Hourly(
    @SerialName("time") val time: List<String> = emptyList(),
    @SerialName("weathercode") val weathercode: List<Int> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int> = emptyList()
)

