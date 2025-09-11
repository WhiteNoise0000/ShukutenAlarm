package io.github.whitenoise0000.shukutenalarm.weather.jma

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * GSI 逆ジオコーディングAPI（LonLatToAddress）のRetrofitインターフェース。
 * - 現在地連動時に muniCd（市区町村コード5桁）を取得するために使用する。
 */
interface GsiApi {
    @GET("/reverse-geocoder/LonLatToAddress")
    suspend fun lonLatToAddress(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): GsiReverseResponse
}

/** GSI LonLatToAddressのレスポンス。*/
@Serializable
data class GsiReverseResponse(
    @SerialName("results") val results: GsiResults
)

/** 住所要素。*/
@Serializable
data class GsiResults(
    /** 市区町村コード（5桁の文字列。例: 渋谷区=13113） */
    @SerialName("muniCd") val muniCd: String,
    /** 市区町村名（漢字） */
    @SerialName("lv01Nm") val municipalityName: String? = null
)

