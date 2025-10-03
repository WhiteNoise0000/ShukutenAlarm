package io.github.whitenoise0000.shukutenalarm.ui

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import io.github.whitenoise0000.shukutenalarm.R
import io.github.whitenoise0000.shukutenalarm.alarm.AlarmGateway
import io.github.whitenoise0000.shukutenalarm.data.DataStoreAlarmRepository
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.VolumeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import android.media.AudioAttributes as SysAudioAttributes
import androidx.media3.common.AudioAttributes as ExoAudioAttributes

/**
 * 鳴動画面（フルスクリーン）。
 * - USAGE_ALARM で音を再生し、スヌーズ/停止操作を提供する。
 * - 現在の天気ラベル/祝日ラベルを簡潔に表示する。
 */
class RingingActivity : ComponentActivity() {
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ロック画面での表示と画面点灯を有効化
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                RingingScreen(
                    alarmId = intent.getIntExtra("id", -1),
                    soundUri = intent.getStringExtra("soundUri")?.toUri(),
                    titleText = stringResource(R.string.text_ringing),
                    weatherLabel = intent.getStringExtra("weatherLabel").orEmpty().also {
                        Log.d("RingingActivity", "Received weatherLabel: $it")
                    },
                    isHoliday = intent.getBooleanExtra("isHoliday", false),
                    holidayName = intent.getStringExtra("holidayName").orEmpty(),
                    alarmName = intent.getStringExtra("alarmName").orEmpty(),
                    volumeMode = runCatching {
                        VolumeMode.valueOf(
                            intent.getStringExtra("volumeMode") ?: VolumeMode.SYSTEM.name
                        )
                    }.getOrDefault(VolumeMode.SYSTEM),
                    volumePercent = intent.getIntExtra("volumePercent", 100).coerceIn(0, 100),
                    vibrate = intent.getBooleanExtra("vibrate", false),
                    respectSilent = intent.getBooleanExtra("respectSilent", true),
                    onFinished = { finish() }
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(UnstableApi::class)
@Composable
private fun RingingScreen(
    alarmId: Int,
    soundUri: Uri?,
    titleText: String,
    weatherLabel: String,
    isHoliday: Boolean,
    holidayName: String,
    alarmName: String,
    volumeMode: VolumeMode,
    volumePercent: Int,
    vibrate: Boolean,
    respectSilent: Boolean,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // AudioManager / Vibrator
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val vibrator = remember {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    }

    // --- どのプレイヤーを使うか判定（Ringtone専用URIはRingtoneで再生） ---
    val useRingtoneApi = remember(soundUri) {
        shouldUseRingtone(soundUri)
    }

    // --- Ringtone 準備（デフォルト/設定URI向け） ---
    val ringtonePlayer: Ringtone? = remember(soundUri, useRingtoneApi) {
        if (useRingtoneApi) {
            val uri = soundUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: Settings.System.DEFAULT_ALARM_ALERT_URI

            RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = SysAudioAttributes.Builder()
                    .setUsage(SysAudioAttributes.USAGE_ALARM)
                    .setContentType(SysAudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                isLooping = true
            }
        } else null
    }

    // --- ExoPlayer 準備（raw資源やMediaStore等の一般URI向け） ---
    val exoPlayer: ExoPlayer? = remember(soundUri, useRingtoneApi) {
        if (!useRingtoneApi && soundUri != null) {
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    ExoAudioAttributes.Builder()
                        .setUsage(C.USAGE_ALARM)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), false
                )
                setMediaItem(MediaItem.fromUri(soundUri))
                // ExoPlayerでは明示的にループ設定を行い、アラーム音が止まらないようにする
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
        } else null
    }

    // 再生とクリーンアップ
    DisposableEffect(ringtonePlayer, exoPlayer, volumeMode, volumePercent, respectSilent) {
        val isSilent = respectSilent && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
        val targetVolume = when {
            isSilent -> 0f
            volumeMode == VolumeMode.CUSTOM -> (volumePercent / 100f)
            else -> 1f
        }

        // Ringtone は個別音量設定がないので STREAM_ALARM の音量を一時変更
        val originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val newAlarmVolume = (targetVolume * maxAlarmVolume).toInt().coerceIn(0, maxAlarmVolume)

        // 再生開始
        if (ringtonePlayer != null) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, newAlarmVolume, 0)
            if (!isSilent) ringtonePlayer.play() else ringtonePlayer.stop()
        } else if (exoPlayer != null) {
            exoPlayer.volume = targetVolume
            if (!isSilent) exoPlayer.play() else exoPlayer.pause()
        }

        // バイブレーション
        if (vibrate) {
            val timings = longArrayOf(0, 500, 500) // 0.5秒ON→0.5秒OFF
            val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
        }

        onDispose {
            try {
                ringtonePlayer?.stop()
            } catch (_: Throwable) { }
            try {
                exoPlayer?.stop()
                exoPlayer?.release()
            } catch (_: Throwable) { }
            vibrator.cancel()
            // 音量を元へ
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
        }
    }

    // ===== UI =====
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val gradient = remember(primaryContainer) {
        Brush.verticalGradient(listOf(primaryContainer, Color.Black))
    }

    val snoozeMinutes = integerResource(id = R.integer.snooze_minutes_default)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        // 中央にコンテンツを配置しつつ、背面に控えめなパルスを描画してモダンな雰囲気を演出
        Box(Modifier.fillMaxSize()) {
            // 背景パルス（ゆっくり拡大縮小する円）
            val infinite = rememberInfiniteTransition(label = "pulse")
            val scale by infinite.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAnim"
            )
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.Center)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f), CircleShape)
            )

            // メインUI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // タイトル + アイコン
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // アラーム名（大きめ、2行上限）
                if (alarmName.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = alarmName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2
                    )
                }

                // 天気・祝日の情報ピル（チップより大きく視認性の高い表示）
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val weather = weatherLabel.ifBlank { stringResource(R.string.text_unknown) }
                    InfoPill(
                        icon = Icons.Outlined.CloudQueue,
                        text = weather,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (isHoliday) {
                        InfoPill(
                            icon = Icons.Outlined.Event,
                            text = holidayName.ifBlank { stringResource(R.string.label_today_holiday) },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(Modifier.height(36.dp))

                // 操作用ボタン（STOPを強調、SNOOZEはトーナル）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            // スヌーズ処理（既定分だけ後ろへずらして再登録）
                            try { ringtonePlayer?.stop() } catch (_: Throwable) {}
                            try { exoPlayer?.stop() } catch (_: Throwable) {}
                            if (alarmId > 0) NotificationManagerCompat.from(context).cancel(alarmId)

                            scope.launch {
                                val repo = DataStoreAlarmRepository(context)
                                val spec: AlarmSpec? = withContext(Dispatchers.IO) { repo.load(alarmId) }
                                spec?.let {
                                    val at = LocalDateTime.now().plusMinutes(snoozeMinutes.toLong())
                                    AlarmGateway(context).scheduleExactAlarm(
                                        it.copy(time = at.toLocalTime()),
                                        at.toLocalDate()
                                    )
                                }
                                onFinished()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Icon(Icons.Outlined.Snooze, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.action_snooze), style = MaterialTheme.typography.titleLarge)
                    }

                    Button(
                        onClick = {
                            // 停止処理（最優先で目立つ配色）
                            try { ringtonePlayer?.stop() } catch (_: Throwable) {}
                            try { exoPlayer?.stop() } catch (_: Throwable) {}
                            if (alarmId > 0) NotificationManagerCompat.from(context).cancel(alarmId)
                            onFinished()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Outlined.StopCircle, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.action_stop), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

/**
 * 情報ピル（大きめのラベル表示）。
 * - アイコン＋テキストを丸みのあるサーフェス上に表示し、視認性を高める。
 */
@Composable
private fun InfoPill(
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = containerColor.copy(alpha = 0.24f),
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(10.dp))
            Text(text = text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, maxLines = 2)
        }
    }
}

/**
 * Ringtone専用URIかどうかを判定。
 * - null（＝デフォルト）や「デフォルト着信音の象徴URI」
 * - content://settings/...（Settings.AUTHORITY）
 * は Ringtone でのみ再生可能。
 */
private fun shouldUseRingtone(uri: Uri?): Boolean {
    if (uri == null) return true
    // デフォルト着信音等の象徴URIか？
    if (RingtoneManager.isDefault(uri)) return true
    // Settingsプロバイダ配下（content://settings/...）
    if (uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == Settings.AUTHORITY) {
        return true
    }
    return false
}
