package io.github.whitenoise0000.shukutenalarm.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** ウィジェットのサイズ判定が意図どおりかを確認するテスト。 */
class NextAlarmWidgetLayoutSelectorTest {

    @Test
    fun select_returnsCompact_whenWidthIsSmallerThanExpandedThreshold() {
        val actual = NextAlarmWidgetLayoutSelector.select(minWidthDp = 110, minHeightDp = 58)

        assertEquals(NextAlarmWidgetLayoutMode.COMPACT, actual)
    }

    @Test
    fun select_returnsCompact_whenHeightIsSmallerThanExpandedThreshold() {
        val actual = NextAlarmWidgetLayoutSelector.select(minWidthDp = 160, minHeightDp = 40)

        assertEquals(NextAlarmWidgetLayoutMode.COMPACT, actual)
    }

    @Test
    fun select_returnsExpanded_whenBothDimensionsMeetExpandedThreshold() {
        val actual = NextAlarmWidgetLayoutSelector.select(minWidthDp = 160, minHeightDp = 58)

        assertEquals(NextAlarmWidgetLayoutMode.EXPANDED, actual)
    }
}
