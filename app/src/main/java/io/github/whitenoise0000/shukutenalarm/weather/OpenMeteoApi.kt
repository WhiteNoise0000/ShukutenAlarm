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
 * Open‑Meteo の JMA モデルでは、変数未提供の時間帯が `null` になることがあるため、
 * 配列の要素およびプロパティ自体を null 許容にしてデコード失敗を防ぐ。
 */
@Serializable
data class JmaResponse(
    val hourly: Hourly
)

@Serializable
data class Hourly(
    // 時刻は常に提供される想定だが安全のためデフォルトを持つ
    @SerialName("time") val time: List<String> = emptyList(),
    // 要素が null のケースやプロパティ自体が欠落/ null のケースに対応
    @SerialName("weathercode") val weathercode: List<Int?>? = emptyList(),
    // アプリでは未使用だが、JMA では未対応で null が入ることがあるため同様に許容
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?>? = emptyList()
)

