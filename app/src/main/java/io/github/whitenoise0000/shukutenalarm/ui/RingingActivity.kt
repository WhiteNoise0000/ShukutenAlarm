package io.github.whitenoise0000.shukutenalarm.ui

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.whitenoise0000.shukutenalarm.R
import io.github.whitenoise0000.shukutenalarm.alarm.AlarmGateway
import io.github.whitenoise0000.shukutenalarm.data.DataStoreAlarmRepository
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.VolumeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * 鳴動画面（フルスクリーン）。
 * - USAGE_ALARM で音を再生し、スヌーズ/停止操作を提供する。
 * - 現在の天気ラベル/祝日ラベルを簡潔に表示する。
 */
class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                RingingScreen(
                    alarmId = intent.getIntExtra("id", -1),
                    soundUri = intent.getStringExtra("soundUri")?.toUri(),
                    titleText = stringResource(R.string.text_ringing),
                    weatherLabel = intent.getStringExtra("weatherLabel").orEmpty(),
                    isHoliday = intent.getBooleanExtra("isHoliday", false),
                    holidayName = intent.getStringExtra("holidayName").orEmpty(),
                    volumeMode = runCatching { VolumeMode.valueOf(intent.getStringExtra("volumeMode") ?: VolumeMode.SYSTEM.name) }.getOrDefault(VolumeMode.SYSTEM),
                    volumePercent = intent.getIntExtra("volumePercent", 100).coerceIn(0,100),
                    vibrate = intent.getBooleanExtra("vibrate", false),
                    respectSilent = intent.getBooleanExtra("respectSilent", true),
                    onFinished = { finish() }
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun RingingScreen(
    alarmId: Int,
    soundUri: Uri?,
    titleText: String,
    weatherLabel: String,
    isHoliday: Boolean,
    holidayName: String,
    volumeMode: VolumeMode,
    volumePercent: Int,
    vibrate: Boolean,
    respectSilent: Boolean,
    onFinished: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // 音量・マナーモード制御用の AudioManager
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    // バイブレーション制御用の Vibrator
    val vibrator = remember {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    }

    // プレイヤー準備
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = soundUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_ALARM)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            setMediaItem(MediaItem.fromUri(uri))

            // マナーモード/サイレントモードを踏襲するか判定
            val isSilent = respectSilent && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
            // 最終的な音量を決定（サイレント or 個別設定 or システム設定）
            val baseVol = if (isSilent) 0f else if (volumeMode == VolumeMode.CUSTOM) (volumePercent / 100f) else 1f
            volume = baseVol

            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(Unit) {
        // バイブレーションが有効な場合、パターンで鳴動開始
        if (vibrate) {
            val timings = longArrayOf(0, 500, 500) // 0.5秒ON, 0.5秒OFF
            val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0)) // 繰り返し
        }

        // 画面破棄時にプレイヤーとバイブを停止
        onDispose {
            runCatching {
                player.stop()
                player.release()
                vibrator.cancel()
            }
        }
    }

    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val gradient = remember(primaryContainer) {
        Brush.verticalGradient(listOf(primaryContainer, Color.Black))
    }

    val snoozeMinutes = integerResource(id = R.integer.snooze_minutes_default)

    Surface(modifier = Modifier.fillMaxSize().background(gradient)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = titleText, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            // 天気ラベルは未取得でも明示的に表示して切り分けやすくする
            run {
                val label = weatherLabel.ifBlank { stringResource(R.string.text_unknown) }
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(R.string.label_weather_prefix, label), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (isHoliday) {
                Spacer(Modifier.height(4.dp))
                Text(text = holidayName.ifBlank { stringResource(R.string.label_today_holiday) }, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = {
                    // スヌーズ
                    player.stop()
                    if (alarmId > 0) NotificationManagerCompat.from(context).cancel(alarmId)
                    scope.launch {
                        val repo = DataStoreAlarmRepository(context)
                        val spec: AlarmSpec? = withContext(Dispatchers.IO) { repo.load(alarmId) }
                        spec?.let {
                            val at = LocalDateTime.now().plusMinutes(snoozeMinutes.toLong())
                            AlarmGateway(context).scheduleExactAlarm(it.copy(time = at.toLocalTime()), at.toLocalDate())
                        }
                        onFinished()
                    }
                }) { Text(stringResource(R.string.action_snooze)) }
                Button(onClick = {
                    player.stop()
                    if (alarmId > 0) NotificationManagerCompat.from(context).cancel(alarmId)
                    onFinished()
                }) { Text(stringResource(R.string.action_stop)) }
            }
        }
    }
}