package io.github.whitenoise0000.shukutenalarm.weather.jma

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * 気象庁 予報APIのRetrofitインターフェース。
 * - 例: https://www.jma.go.jp/bosai/forecast/data/forecast/{office}.json
 */
interface JmaForecastApi {
    @GET("/bosai/forecast/data/forecast/{office}.json")
    suspend fun forecast(@Path("office") office: String): List<ForecastRoot>
}

/** 予報JSONのトップ要素（配列の各要素）。*/
@Serializable
data class ForecastRoot(
    @SerialName("publishingOffice") val publishingOffice: String? = null,
    @SerialName("reportDatetime") val reportDatetime: String? = null,
    @SerialName("timeSeries") val timeSeries: List<TimeSeries> = emptyList()
)

/** 時系列データ。*/
@Serializable
data class TimeSeries(
    @SerialName("timeDefines") val timeDefines: List<String> = emptyList(),
    @SerialName("areas") val areas: List<AreaValues> = emptyList()
)

/** エリア別の値郡。*/
@Serializable
data class AreaValues(
    @SerialName("area") val area: AreaRef,
    @SerialName("weathers") val weathers: List<String>? = null,
    @SerialName("weatherCodes") val weatherCodes: List<String>? = null,
    // 必要に応じて pops/temps などは無視
)

/** エリア参照。*/
@Serializable
data class AreaRef(
    @SerialName("name") val name: String? = null,
    @SerialName("code") val code: String
)

