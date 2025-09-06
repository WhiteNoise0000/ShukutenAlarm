package io.github.whitenoise0000.shukutenalarm.alarm

import android.net.Uri
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.HolidayPolicy
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory

/**
 * 祝日ポリシーと天気カテゴリに基づき、再生するサウンド URI を選択するユーティリティ。
 * - 仕様の優先度: 失敗/未取得時はデフォルト音。
 * - 祝日: HolidayPolicy と holidaySound 設定を考慮。
 */
object SoundSelector {
    /**
     * 鳴動すべきかどうかの判定。
     * - 祝日かつ SKIP の場合は鳴らさない。
     */
    fun shouldRing(spec: AlarmSpec, isHoliday: Boolean): Boolean {
        return !(isHoliday && spec.holidayPolicy == HolidayPolicy.SKIP)
    }

    /**
     * 再生する音源 URI を選択する。
     * - 祝日専用サウンドは使用せず、天気カテゴリのマッピングを用いる（UI方針に合わせる）。
     * - 天気カテゴリが null or ヒットしなければ defaultUriProvider を使用。
     */
    fun selectSound(
        spec: AlarmSpec,
        isHoliday: Boolean,
        weather: WeatherCategory?,
        defaultUriProvider: () -> Uri
    ): Uri {
        val uri = weather?.let { spec.soundMapping[it] }
        // 天気別が未設定のときはデフォルトサウンド（holidaySound プロパティを流用）
        return uri ?: spec.holidaySound ?: defaultUriProvider()
    }
}
