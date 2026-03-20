package io.github.whitenoise0000.shukutenalarm.data

import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.whitenoise0000.shukutenalarm.data.model.RepeatType
import io.github.whitenoise0000.shukutenalarm.settings.SettingsRepository
import io.github.whitenoise0000.shukutenalarm.settings.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * クイックタイマーの既定値保存と生成結果を確認する計測テスト。
 */
@RunWith(AndroidJUnit4::class)
class QuickTimerSettingsInstrumentedTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = runBlocking {
        context.appDataStore.edit { it.clear() }
    }

    @After
    fun tearDown() = runBlocking {
        context.appDataStore.edit { it.clear() }
    }

    @Test
    fun save_persistsQuickTimerRespectSilentModeDefault() = runBlocking {
        val repo = SettingsRepository(context)

        repo.save(
            UserSettings(
                latitude = 35.0,
                longitude = 135.0,
                useCurrentLocation = false,
                delayMinutes = 60,
                quickTimerRespectSilentMode = false
            )
        )

        val saved = repo.settingsFlow.first()

        assertFalse(saved.quickTimerRespectSilentMode)
    }

    @Test
    fun createQuickTimer_appliesRespectSilentMode() = runBlocking {
        val repo = DataStoreAlarmRepository(context)

        val newId = repo.createQuickTimer(
            time = LocalTime.of(7, 30),
            respectSilentMode = false
        )
        val quickTimer = newId?.let { repo.load(it) }

        assertNotNull(quickTimer)
        assertTrue(quickTimer?.isQuickTimer == true)
        assertEquals(RepeatType.ONE_SHOT, quickTimer?.repeatType)
        assertFalse(quickTimer?.respectSilentMode ?: true)
    }
}
