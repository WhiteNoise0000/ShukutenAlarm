package io.github.whitenoise0000.shukutenalarm.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.whitenoise0000.shukutenalarm.R
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory

/**
 * Composableなコンテキストで、WeatherCategoryに対応する文字列リソースを取得します。
 */
@Composable
fun WeatherCategory.getLabel(): String {
    return when (this) {
        WeatherCategory.CLEAR -> stringResource(R.string.weather_clear)
        WeatherCategory.CLOUDY -> stringResource(R.string.weather_cloudy)
        WeatherCategory.RAIN -> stringResource(R.string.weather_rain)
        WeatherCategory.SNOW -> stringResource(R.string.weather_snow)
    }
}

/**
 * 非Composableなコンテキストで、WeatherCategoryに対応する文字列リソースを取得します。
 */
fun WeatherCategory.getLabel(context: Context): String {
    return when (this) {
        WeatherCategory.CLEAR -> context.getString(R.string.weather_clear)
        WeatherCategory.CLOUDY -> context.getString(R.string.weather_cloudy)
        WeatherCategory.RAIN -> context.getString(R.string.weather_rain)
        WeatherCategory.SNOW -> context.getString(R.string.weather_snow)
    }
}
