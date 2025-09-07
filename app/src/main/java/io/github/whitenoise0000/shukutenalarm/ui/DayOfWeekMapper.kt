package io.github.whitenoise0000.shukutenalarm.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.whitenoise0000.shukutenalarm.R
import java.time.DayOfWeek

/**
 * Composableなコンテキストで、DayOfWeekに対応する文字列リソースを取得します。
 */
@Composable
fun DayOfWeek.getLabel(): String = when (this) {
    DayOfWeek.MONDAY -> stringResource(R.string.dow_mon)
    DayOfWeek.TUESDAY -> stringResource(R.string.dow_tue)
    DayOfWeek.WEDNESDAY -> stringResource(R.string.dow_wed)
    DayOfWeek.THURSDAY -> stringResource(R.string.dow_thu)
    DayOfWeek.FRIDAY -> stringResource(R.string.dow_fri)
    DayOfWeek.SATURDAY -> stringResource(R.string.dow_sat)
    DayOfWeek.SUNDAY -> stringResource(R.string.dow_sun)
}

/**
 * 非Composableなコンテキストで、DayOfWeekに対応する文字列リソースを取得します。
 */
fun DayOfWeek.getLabel(context: Context): String = when (this) {
    DayOfWeek.MONDAY -> context.getString(R.string.dow_mon)
    DayOfWeek.TUESDAY -> context.getString(R.string.dow_tue)
    DayOfWeek.WEDNESDAY -> context.getString(R.string.dow_wed)
    DayOfWeek.THURSDAY -> context.getString(R.string.dow_thu)
    DayOfWeek.FRIDAY -> context.getString(R.string.dow_fri)
    DayOfWeek.SATURDAY -> context.getString(R.string.dow_sat)
    DayOfWeek.SUNDAY -> context.getString(R.string.dow_sun)
}