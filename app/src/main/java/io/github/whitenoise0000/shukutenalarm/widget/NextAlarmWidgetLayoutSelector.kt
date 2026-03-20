package io.github.whitenoise0000.shukutenalarm.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle

/**
 * ウィジェットの割り当てサイズに応じて表示モードを決める。
 * 2x1 ではコンパクト表示、既存の詳細レイアウトが収まるサイズでは拡張表示を使う。
 */
internal object NextAlarmWidgetLayoutSelector {
    /**
     * 既存の詳細レイアウトを崩さず表示できる下限サイズ。
     * 現行デザインの実寸に合わせているため、変更時はレイアウト XML と併せて見直す。
     */
    private const val EXPANDED_MIN_WIDTH_DP = 160
    private const val EXPANDED_MIN_HEIGHT_DP = 58

    /** AppWidget のオプション Bundle から表示モードを判定する。 */
    fun select(options: Bundle?): NextAlarmWidgetLayoutMode {
        val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        return select(minWidthDp, minHeightDp)
    }

    /** 幅と高さの dp 値から表示モードを判定する。 */
    fun select(minWidthDp: Int, minHeightDp: Int): NextAlarmWidgetLayoutMode {
        return if (minWidthDp >= EXPANDED_MIN_WIDTH_DP && minHeightDp >= EXPANDED_MIN_HEIGHT_DP) {
            NextAlarmWidgetLayoutMode.EXPANDED
        } else {
            NextAlarmWidgetLayoutMode.COMPACT
        }
    }
}

/** ウィジェットの表示モード。 */
internal enum class NextAlarmWidgetLayoutMode {
    COMPACT,
    EXPANDED
}
