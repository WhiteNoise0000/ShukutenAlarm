package io.github.whitenoise0000.shukutenalarm.weather

import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import io.github.whitenoise0000.shukutenalarm.data.model.toCategory

/**
 * WMO weather code をアプリの天気カテゴリに変換するユーティリティ。
 * 実装は拡張関数 `Int.toCategory()` に委譲して重複を避ける。
 */
object WeatherMapper {
    fun fromWmo(code: Int): WeatherCategory = code.toCategory()
}
