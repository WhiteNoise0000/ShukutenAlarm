package io.github.whitenoise0000.shukutenalarm.ui

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.whitenoise0000.shukutenalarm.R
import io.github.whitenoise0000.shukutenalarm.alarm.ScheduleManager
import io.github.whitenoise0000.shukutenalarm.data.DataStoreAlarmRepository
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.HolidayPolicy
import io.github.whitenoise0000.shukutenalarm.data.model.VolumeMode
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import io.github.whitenoise0000.shukutenalarm.data.model.RepeatType
import io.github.whitenoise0000.shukutenalarm.settings.SettingsRepository
import io.github.whitenoise0000.shukutenalarm.widget.NextAlarmWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * アラーム編集画面。
 * - セクション化されたカードで構成し、操作性と視認性を高める。
 */
@SuppressLint("DefaultLocale")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EditAlarmScreen(
    alarmId: Int?,
    onDone: () -> Unit,
    registerSave: ((() -> Unit) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { DataStoreAlarmRepository(context) }
    val scheduler = remember { ScheduleManager(context) }
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }

    val nameState = remember { mutableStateOf("") }
    val hour = remember { mutableStateOf("7") }
    val minute = remember { mutableStateOf("0") }
    val policy = remember { mutableStateOf(HolidayPolicy.SAME) }
    // 繰り返し種別（週ごと / 1回のみ）
    val repeatType = remember { mutableStateOf(RepeatType.WEEKLY) }
    val volumeModeState = remember { mutableStateOf(VolumeMode.SYSTEM) }
    val volumePercentState = remember { mutableFloatStateOf(100f) }
    val vibrateState = remember { mutableStateOf(false) }
    val respectSilentState = remember { mutableStateOf(true) }
    val soundMap = remember {
        mutableStateMapOf<WeatherCategory, Uri?>().apply {
            WeatherCategory.entries.forEach {
                put(
                    it,
                    null
                )
            }
        }
    }
    val holidaySound = remember { mutableStateOf<Uri?>(null) }
    val holidayOnly = remember { mutableStateOf(false) }

    val onPicked = remember { mutableStateOf<(Uri?) -> Unit>({}) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri: Uri? = result.data?.getParcelableExtra(
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java
            )
            onPicked.value(uri)
        }
    val days = remember {
        mutableStateListOf(
            DayOfWeek.MONDAY to true,
            DayOfWeek.TUESDAY to true,
            DayOfWeek.WEDNESDAY to true,
            DayOfWeek.THURSDAY to true,
            DayOfWeek.FRIDAY to true,
            DayOfWeek.SATURDAY to false,
            DayOfWeek.SUNDAY to false
        )
    }

    // 設定から祝日DELAY分を取得（ラベル動的化のため）
    val delayMins = remember { mutableIntStateOf(60) }
    LaunchedEffect(Unit) {
        val s = withContext(Dispatchers.IO) { settingsRepo.settingsFlow.first() }
        delayMins.intValue = s.delayMinutes
    }

    // 既存アラームの読み込み（編集時）
    LaunchedEffect(alarmId) {
        if (alarmId != null) {
            val spec = withContext(Dispatchers.IO) { repo.load(alarmId) }
            spec?.let {
                nameState.value = it.name
                hour.value = it.time.hour.toString()
                minute.value = it.time.minute.toString()
                policy.value = it.holidayPolicy
                volumeModeState.value = it.volumeMode
                volumePercentState.floatValue = it.volumePercent.toFloat()
                vibrateState.value = it.vibrate
                respectSilentState.value = it.respectSilentMode
                holidayOnly.value = it.holidayOnly
                soundMap.clear(); WeatherCategory.entries.forEach { k ->
                soundMap[k] = it.soundMapping[k]
            }
                holidaySound.value = it.holidaySound
                repeatType.value = it.repeatType
                val selected = it.daysOfWeek
                val new = listOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
                ).map { d -> d to selected.contains(d) }
                days.clear(); days.addAll(new)
            }
        }
    }

    // 画面内で共通の保存処理を定義し、トップバーからも呼べるように登録
    fun doSave() {
        val h = hour.value.toIntOrNull()?.coerceIn(0, 23) ?: 7
        val m = minute.value.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val time = LocalTime.of(h, m)
        val selectedDays = days.filter { it.second }.map { it.first }.toSet()
        // バリデーション: 祝日のみ以外では曜日を1つ以上必須
        if (!holidayOnly.value && selectedDays.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.error_days_required),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val spec = AlarmSpec(
            id = alarmId ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            name = nameState.value.trim(),
            time = time,
            daysOfWeek = selectedDays,
            holidayPolicy = policy.value,
            holidayOnly = holidayOnly.value,
            volumeMode = volumeModeState.value,
            volumePercent = volumePercentState.floatValue.toInt().coerceIn(0, 100),
            vibrate = vibrateState.value,
            respectSilentMode = respectSilentState.value,
            prefetchMinutes = 45,
            soundMapping = soundMap.filterValues { it != null }.mapValues { it.value!! },
            holidaySound = holidaySound.value,
            enabled = true,
            repeatType = repeatType.value
        )
        scope.launch {
            withContext(Dispatchers.IO) { repo.save(spec) }
            scheduler.scheduleNext(spec)
            Toast.makeText(
                context,
                context.getString(R.string.toast_alarm_saved),
                Toast.LENGTH_SHORT
            ).show()
            onDone()
            // ウィジェットへ更新通知（新規/編集保存）
            context.sendBroadcast(
                Intent(context, NextAlarmWidgetProvider::class.java).setAction(
                    NextAlarmWidgetProvider.ACTION_REFRESH
                )
            )
        }
    }

    // トップバーの保存アイコン用に登録
    LaunchedEffect(Unit) { registerSave?.invoke { doSave() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 基本情報
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 画面名は TopAppBar で表示済みのため、ここでの見出しは省略
                OutlinedTextField(
                    value = nameState.value,
                    onValueChange = { nameState.value = it },
                    label = { Text(stringResource(R.string.label_alarm_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Outlined.Alarm, contentDescription = null) },
                    trailingIcon = {
                        if (nameState.value.isNotBlank()) IconButton(onClick = {
                            nameState.value = ""
                        }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.text_cancel)
                            )
                        }
                    },
                    singleLine = true
                )
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        val timeText = remember(hour.value, minute.value) {
                            val h = hour.value.toIntOrNull() ?: 7
                            val m = minute.value.toIntOrNull() ?: 0
                            LocalTime.of(h, m).formatHHMM()
                        }
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            val h = hour.value.toIntOrNull() ?: 7
                            val m = minute.value.toIntOrNull() ?: 0
                            TimePickerDialog(context, { _, hh, mm ->
                                hour.value = hh.toString(); minute.value = mm.toString()
                            }, h, m, true).show()
                        }) { Text(stringResource(R.string.action_pick_time)) }
                    }
                }
            }
        }

        // 繰り返し（週ごと / 1回のみ）
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.label_repeat),
                    style = MaterialTheme.typography.titleMedium
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val opts = listOf(RepeatType.WEEKLY, RepeatType.ONE_SHOT)
                    opts.forEachIndexed { index, rt ->
                        val shape = SegmentedButtonDefaults.itemShape(index, opts.size)
                        val selected = repeatType.value == rt
                        val label = when (rt) {
                            RepeatType.WEEKLY -> stringResource(R.string.repeat_weekly)
                            RepeatType.ONE_SHOT -> stringResource(R.string.repeat_one_shot)
                        }
                        SegmentedButton(
                            modifier = Modifier.weight(1f),
                            selected = selected,
                            onClick = { repeatType.value = rt },
                            shape = shape,
                            label = {
                                Text(
                                    label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
                if (repeatType.value == RepeatType.ONE_SHOT) {
                    Text(
                        text = stringResource(R.string.repeat_one_shot_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 曜日選択
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.label_days),
                    style = MaterialTheme.typography.titleMedium
                )
                RowAlignCenter {
                    Text(
                        stringResource(R.string.label_holiday_only),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = holidayOnly.value,
                        onCheckedChange = { holidayOnly.value = it })
                }
                if (!holidayOnly.value) {
                    DayOfWeekChips(days = days)
                } else {
                    Text(
                        text = stringResource(R.string.hint_holiday_only_days_ignored),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 祝日ポリシー（セグメント化）
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.label_holiday_policy),
                    style = MaterialTheme.typography.titleMedium
                )
                val options = listOf(HolidayPolicy.SAME, HolidayPolicy.DELAY, HolidayPolicy.SKIP)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, item ->
                        val selected = policy.value == item
                        val shape =
                            SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                        val (icon, label) = when (item) {
                            // ラベルはすべて短く1行で揃える（ヒントで詳細を補足）
                            HolidayPolicy.SAME -> Icons.Outlined.Schedule to stringResource(R.string.policy_same)
                            HolidayPolicy.DELAY -> Icons.Outlined.Snooze to stringResource(R.string.policy_delay)
                            HolidayPolicy.SKIP -> Icons.Outlined.Block to stringResource(R.string.policy_skip)
                        }
                        SegmentedButton(
                            modifier = Modifier.weight(1f),
                            selected = selected,
                            onClick = {
                                if (!(holidayOnly.value && item == HolidayPolicy.SKIP)) policy.value =
                                    item
                            },
                            shape = shape,
                            label = {
                                Text(
                                    label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            icon = { Icon(icon, contentDescription = null) },
                            enabled = !(holidayOnly.value && item == HolidayPolicy.SKIP)
                        )
                    }
                }
                Text(
                    text = when (policy.value) {
                        HolidayPolicy.SAME -> stringResource(R.string.hint_policy_same)
                        HolidayPolicy.DELAY -> stringResource(
                            R.string.hint_policy_delay_format,
                            delayMins.intValue
                        )

                        HolidayPolicy.SKIP -> stringResource(R.string.hint_policy_skip)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 天気別サウンド（デフォルト含む）
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.label_weather_sounds),
                    style = MaterialTheme.typography.titleMedium
                )
                // デフォルトサウンド（カード表示）
                // 仕様変更: 行タップで編集呼出し。右端アイコンは「削除→プレビュー」の順。
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val current = holidaySound.value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 行タップで着信音ピッカーを起動（編集）
                                onPicked.value = { picked -> holidaySound.value = picked }
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    // 端末内蔵アラーム音と通知サウンドを同時に候補へ並べるため ALARM/RINGTONE の両方を指定
                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_TYPE,
                                        RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE
                                    )
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                        current
                                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                                    )
                                }
                                picker.launch(intent)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // アイコンとラベル部
                        Icon(
                            Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.label_default_sound),
                                style = MaterialTheme.typography.titleMedium
                            )
                            val sub = current?.let { ringtoneTitle(ctx, it) }
                                ?: stringResource(R.string.text_device_default)
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 右端アクション: 削除（選択時のみ）→ プレビュー（常時）
                        if (current != null) {
                            IconButton(onClick = { holidaySound.value = null }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_delete)
                                )
                            }
                        }
                        IconButton(onClick = {
                            // プレビューは選択済み→端末デフォルトの順で解決
                            val chosen = current ?: (
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            )
                            val intent = Intent(ctx, RingingActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                putExtra("id", -999)
                                putExtra("soundUri", chosen?.toString() ?: "")
                                putExtra("weatherLabel", ctx.getString(R.string.label_default_sound))
                                putExtra("isHoliday", false)
                                putExtra("holidayName", "")
                                putExtra("alarmName", nameState.value)
                                putExtra("volumeMode", volumeModeState.value.name)
                                putExtra("volumePercent", volumePercentState.floatValue.toInt())
                                putExtra("vibrate", vibrateState.value)
                                putExtra("respectSilent", respectSilentState.value)
                            }
                            ContextCompat.startActivities(ctx, arrayOf(intent))
                        }) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = stringResource(R.string.action_preview)
                            )
                        }
                    }
                }

                // 天気別サウンド（1行1天気, 雷は除外）
                val weatherCats = listOf(
                    WeatherCategory.CLEAR,
                    WeatherCategory.CLOUDY,
                    WeatherCategory.RAIN,
                    WeatherCategory.SNOW
                )
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    weatherCats.forEach { cat ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            // 仕様変更: 行タップで編集呼出し。右端アイコンは「削除→プレビュー」。
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val current = soundMap[cat]
                                        onPicked.value = { picked -> soundMap[cat] = picked }
                                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                            // アラーム用プリセットを逃さないよう ALARM/RINGTONE の両方を許可する
                                            putExtra(
                                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE
                                            )
                                            putExtra(
                                                RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,
                                                false
                                            )
                                            putExtra(
                                                RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,
                                                true
                                            )
                                            putExtra(
                                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                current
                                            )
                                        }
                                        picker.launch(intent)
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val (icon, tint) = when (cat) {
                                    WeatherCategory.CLEAR -> Icons.Outlined.WbSunny to MaterialTheme.colorScheme.tertiary
                                    WeatherCategory.CLOUDY -> Icons.Outlined.Cloud to MaterialTheme.colorScheme.secondary
                                    WeatherCategory.RAIN -> Icons.Outlined.WaterDrop to MaterialTheme.colorScheme.primary
                                    WeatherCategory.SNOW -> Icons.Outlined.AcUnit to MaterialTheme.colorScheme.primary
                                }
                                Icon(icon, contentDescription = null, tint = tint)
                                val weatherLabel = cat.getLabel()
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = weatherLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val current = soundMap[cat]
                                    val sub = current?.let { ringtoneTitle(ctx, it) }
                                        ?: stringResource(R.string.text_inherit_default)
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val current = soundMap[cat]
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // 削除（選択済み時のみ表示）
                                    if (current != null) {
                                        IconButton(onClick = { soundMap[cat] = null }) {
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = stringResource(R.string.action_delete)
                                            )
                                        }
                                    }
                                    // プレビュー（常時表示）
                                    IconButton(onClick = {
                                        val uri: Uri? = current ?: holidaySound.value
                                        val chosen = uri ?: (
                                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                        )
                                        val intent = Intent(ctx, RingingActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                            putExtra("id", -999)
                                            putExtra("soundUri", chosen?.toString() ?: "")
                                            putExtra("weatherLabel", weatherLabel)
                                            putExtra("isHoliday", false)
                                            putExtra("holidayName", "")
                                            putExtra("alarmName", nameState.value)
                                            putExtra("volumeMode", volumeModeState.value.name)
                                            putExtra("volumePercent", volumePercentState.floatValue.toInt())
                                            putExtra("vibrate", vibrateState.value)
                                            putExtra("respectSilent", respectSilentState.value)
                                        }
                                        ContextCompat.startActivities(ctx, arrayOf(intent))
                                    }) {
                                        Icon(
                                            Icons.Outlined.PlayArrow,
                                            contentDescription = stringResource(R.string.action_preview)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 鳴動オプション（音量/バイブ/マナー）
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.label_alarm_options),
                    style = MaterialTheme.typography.titleMedium
                )
                // 音量モード
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val opts = listOf(VolumeMode.SYSTEM, VolumeMode.CUSTOM)
                    opts.forEachIndexed { index, vm ->
                        val shape = SegmentedButtonDefaults.itemShape(index, opts.size)
                        val selected = volumeModeState.value == vm
                        val label = when (vm) {
                            VolumeMode.SYSTEM -> stringResource(R.string.volume_mode_default)
                            VolumeMode.CUSTOM -> stringResource(R.string.volume_mode_custom)
                        }
                        SegmentedButton(
                            modifier = Modifier.weight(1f),
                            selected = selected,
                            onClick = { volumeModeState.value = vm },
                            shape = shape,
                            label = {
                                Text(
                                    label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
                // 個別音量
                if (volumeModeState.value == VolumeMode.CUSTOM) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = stringResource(R.string.label_volume))
                        // RTL（右から左）レイアウトにも自動対応させるため、AutoMirrored版へ置換
                        Icon(Icons.AutoMirrored.Outlined.VolumeDown, contentDescription = null)
                        Slider(
                            modifier = Modifier.weight(1f),
                            value = volumePercentState.floatValue / 100f,
                            onValueChange = {
                                volumePercentState.floatValue = (it * 100).coerceIn(0f, 100f)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors()
                        )
                        // 非推奨APIの置換：AutoMirrored版を利用して将来の互換性とRTL対応を確保
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null)
                        Text(
                            text = stringResource(
                                R.string.volume_percent_format,
                                volumePercentState.floatValue.toInt()
                            )
                        )
                    }
                }
                // バイブレーション
                RowAlignCenter {
                    Text(stringResource(R.string.label_vibrate), modifier = Modifier.weight(1f))
                    Switch(
                        checked = vibrateState.value,
                        onCheckedChange = { vibrateState.value = it })
                }
                // マナー/サイレント踏襲
                RowAlignCenter {
                    Text(
                        stringResource(R.string.label_respect_silent),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = respectSilentState.value,
                        onCheckedChange = { respectSilentState.value = it })
                }
                Text(
                    text = stringResource(R.string.hint_respect_silent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 保存
        Button(
            onClick = { doSave() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.text_save)) }
    }
}

/**
 * 曜日チップの操作コンポーネント。
 * - 週の各曜日をチップで表示し、タップでON/OFF切り替え。
 * - 2行に分けて視認性を確保。
 */
@Composable
private fun DayOfWeekChips(days: MutableList<Pair<DayOfWeek, Boolean>>) {
    val firstRow = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    )
    val secondRow = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    RowAlignCenter {
        firstRow.forEach { d ->
            val idx = days.indexOfFirst { it.first == d }
            val selected = if (idx >= 0) days[idx].second else false
            FilterChip(
                selected = selected,
                onClick = { if (idx >= 0) days[idx] = days[idx].copy(second = !selected) },
                label = { Text(d.getLabel()) },
                colors = FilterChipDefaults.filterChipColors()
            )
        }
    }
    RowAlignCenter {
        secondRow.forEach { d ->
            val idx = days.indexOfFirst { it.first == d }
            val selected = if (idx >= 0) days[idx].second else false
            FilterChip(
                selected = selected,
                onClick = { if (idx >= 0) days[idx] = days[idx].copy(second = !selected) },
                label = { Text(d.getLabel()) },
                colors = FilterChipDefaults.filterChipColors()
            )
        }
    }
}

/**
 * Ringtone のタイトルを取得するユーティリティ。
 * - タイトル取得に失敗した場合は「不明」を返す。
 */
private fun ringtoneTitle(context: android.content.Context, uri: Uri): String =
    runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
        .getOrNull() ?: context.getString(R.string.text_unknown)

@Composable
private fun RowAlignCenter(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}
