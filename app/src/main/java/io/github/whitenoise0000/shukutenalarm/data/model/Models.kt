package io.github.whitenoise0000.shukutenalarm.data.model

import android.net.Uri
import io.github.whitenoise0000.shukutenalarm.data.serializers.LocalTimeSerializer
import io.github.whitenoise0000.shukutenalarm.data.serializers.UriSerializer
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * 天気カテゴリの列挙。WMOコードをもとに分類する。
 */
enum class WeatherCategory { CLEAR, CLOUDY, RAIN, SNOW }

/**
 * 祝日ポリシーの列挙。
 * - SKIP: 祝日は鳴らさない
 * - DELAY: 祝日は遅らせて鳴らす
 * - SAME: 平日と同じ設定で鳴らす
 */
enum class HolidayPolicy { SKIP, DELAY, SAME }

/**
 * 音量設定のモード。
 * - SYSTEM: 端末（システム）のアラーム音量に従う
 * - CUSTOM: 個別の音量パーセンテージを適用する
 */
enum class VolumeMode { SYSTEM, CUSTOM }

/**
 * アラーム仕様を表すデータモデル。
 * - 仕様書のサンプルに準拠。
 */
@Serializable
data class AlarmSpec(
    val id: Int,
    /** アラーム名。区別しやすくするための任意名（空文字許容）。 */
    val name: String = "",
    @Serializable(with = LocalTimeSerializer::class)
    val time: LocalTime,
    val daysOfWeek: Set<DayOfWeek>,
    val holidayPolicy: HolidayPolicy,
    /** 音量モード（デフォルト=システム従属） */
    val volumeMode: VolumeMode = VolumeMode.SYSTEM,
    /** 個別音量（0..100, volumeMode=CUSTOM のとき有効） */
    val volumePercent: Int = 100,
    /** バイブレーションを併用するか */
    val vibrate: Boolean = false,
    /** 端末のマナー/サイレントモードを踏襲するか */
    val respectSilentMode: Boolean = true,
    /** 祝日のみ鳴動するかどうか（true の場合は非祝日をスキップ）。 */
    val holidayOnly: Boolean = false,
    val prefetchMinutes: Int = 45,
    val soundMapping: Map<WeatherCategory, @Serializable(with = UriSerializer::class) Uri>,
    /** デフォルトサウンド（天気別が未設定時のフォールバック）。既存互換のためプロパティ名は holidaySound を流用。 */
    @Serializable(with = UriSerializer::class)
    val holidaySound: Uri? = null,
    val enabled: Boolean = true
)

/**
 * WMOコードから天気カテゴリへ変換する拡張関数。
 * - 仕様書のルールに基づく。
 */
fun Int.toCategory(): WeatherCategory = when (this) {
    0 -> WeatherCategory.CLEAR
    in 1..3 -> WeatherCategory.CLOUDY
    in 51..57, in 61..65, in 80..82 -> WeatherCategory.RAIN
    in 71..75, 85, 86 -> WeatherCategory.SNOW
    in 95..99 -> WeatherCategory.RAIN // 雷は雨扱いにしておく
    else -> WeatherCategory.CLOUDY
}
