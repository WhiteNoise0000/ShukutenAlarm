package io.github.whitenoise0000.shukutenalarm.ui

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.JAPAN)

fun LocalTime.formatHHMM(): String {
    return this.format(TIME_FORMATTER)
}
