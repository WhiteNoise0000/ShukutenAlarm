package io.github.whitenoise0000.shukutenalarm.weather.jma

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * 気象庁 定数マスタAPI（area.json）のRetrofitインターフェース。
 */
interface JmaConstApi {
    @GET("/bosai/common/const/area.json")
    suspend fun area(): AreaMaster
}

/** area.json のルート。必要最小限のフィールドのみ定義。*/
@Serializable
data class AreaMaster(
    /** 予報官署（officeコード -> 情報） */
    @SerialName("offices") val offices: Map<String, OfficeEntry> = emptyMap(),
    /** クラス10（class10コード -> 情報）。forecastのareas.area.codeに合致するのはこちら */
    @SerialName("class10s") val class10s: Map<String, Class10Entry> = emptyMap(),
    /** クラス20（class20コード -> 情報）。GSI muniCd からこちらへ辿る。 */
    @SerialName("class20s") val class20s: Map<String, Class20Entry> = emptyMap()
)

/** 予報官署(offices)の要素。*/
@Serializable
data class OfficeEntry(
    /** 名称 */
    val name: String,
    /** 子（class10のコード配列） */
    val children: List<String> = emptyList()
)

/** クラス10の要素。*/
@Serializable
data class Class10Entry(
    /** 名称 */
    val name: String,
    /** 親（officeコード） */
    val parent: String,
    /** 子（class10内部のエリアコード、例: 130011 など） */
    val children: List<String> = emptyList()
)

/** クラス20の要素。*/
@Serializable
data class Class20Entry(
    /** 名称 */
    val name: String,
    /** 親（class10内のエリアコード。例: 130011 など） */
    val parent: String
)

