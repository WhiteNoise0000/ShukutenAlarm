package io.github.whitenoise0000.shukutenalarm.ui

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.Manifest
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
// AutoMirrored版の音量アイコンを利用（RTL対応）。
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import io.github.whitenoise0000.shukutenalarm.alarm.ScheduleManager
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import io.github.whitenoise0000.shukutenalarm.data.DataStoreAlarmRepository
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.HolidayPolicy
import io.github.whitenoise0000.shukutenalarm.data.model.VolumeMode
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import io.github.whitenoise0000.shukutenalarm.R
import io.github.whitenoise0000.shukutenalarm.settings.SettingsRepository
import io.github.whitenoise0000.shukutenalarm.ui.theme.HolidayAlermTheme
import io.github.whitenoise0000.shukutenalarm.weather.GeocodingRepository
import io.github.whitenoise0000.shukutenalarm.weather.GeoPlace
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast



/**
 * メイン画面のアクティビティ。
 * - Compose Navigation により一覧/編集/設定を遷移表示する。
 * - エッジ・トゥ・エッジと Dynamic Color を有効化し、モダンな外観に整える。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // システムバーを透過し、コンテンツをエッジまで描画する
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // アプリ共通の Material3 テーマ
            HolidayAlermTheme {
                AppRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    val backstack = navController.currentBackStackEntryAsState()
    val route = backstack.value?.destination?.route ?: "home"
    // 編集画面の保存アクションをトップバーから呼び出すために登録/保持
    val editSaveAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    // スクロール時にトップバーの影や高さが自然に変化する挙動
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            route.startsWith("edit") -> stringResource(R.string.title_add_alarm)
                            route == "settings" -> stringResource(R.string.title_settings)
                            else -> stringResource(R.string.title_alarm_list)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    // 編集/設定画面では戻るボタンを表示
                    if (route.startsWith("edit") || route == "settings") {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (route == "home") {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.label_settings))
                        }
                    } else if (route.startsWith("edit")) {
                        IconButton(onClick = { editSaveAction.value?.invoke() }) {
                            Icon(Icons.Outlined.Save, contentDescription = stringResource(R.string.text_save))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (route == "home") {
                FloatingActionButton(onClick = { navController.navigate("edit") }) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.label_add))
                }
            }
        }
    ) { innerPadding ->
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(innerPadding)
        ) {
            AppNavHost(navController, editSaveAction)
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, editSaveAction: androidx.compose.runtime.MutableState<(() -> Unit)?>) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            AlarmListScreen(
                onAdd = { navController.navigate("edit") },
                onSettings = { navController.navigate("settings") },
                onEdit = { id -> navController.navigate("edit/$id") }
            )
        }
        composable("edit") { EditAlarmScreenModern(alarmId = null, onDone = { navController.popBackStack() }, registerSave = { action -> editSaveAction.value = action }) }
        composable("edit/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull()
            EditAlarmScreenModern(alarmId = id, onDone = { navController.popBackStack() }, registerSave = { action -> editSaveAction.value = action })
        }
        composable("settings") { SettingsScreenModernNew() }
    }
}

@Composable
private fun AlarmListScreen(onAdd: () -> Unit, onSettings: () -> Unit, onEdit: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { DataStoreAlarmRepository(context) }
    val scheduler = remember { ScheduleManager(context) }
    val list = remember { mutableStateListOf<AlarmSpec>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val items = withContext(Dispatchers.IO) { repo.list() }
        val sorted = items.sortedBy { it.time }
        list.clear(); list.addAll(sorted)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (list.isEmpty()) {
            // 空状態のメッセージ（初回導線を明確に）
            EmptyState(
                icon = { Icon(Icons.Outlined.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.empty_alarms_title),
                message = stringResource(R.string.empty_alarms_message)
            )
        } else {
            LazyColumn {
                items(list, key = { it.id }) { spec ->
                    AlarmCard(
                        spec = spec,
                        onEdit = { onEdit(spec.id) },
                        onDelete = {
                            scope.launch {
                                val items = withContext(Dispatchers.IO) {
                                    repo.delete(spec.id)
                                    scheduler.cancel(spec.id)
                                    repo.list()
                                }
                                val sorted = items.sortedBy { it.time }
                                list.clear(); list.addAll(sorted)
                                Toast.makeText(context, context.getString(R.string.toast_alarm_deleted), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onToggle = { enable ->
                            val updated = spec.copy(enabled = enable)
                            scope.launch {
                                withContext(Dispatchers.IO) { repo.save(updated) }
                                if (enable) scheduler.scheduleNext(updated) else scheduler.cancel(updated.id)
                                val msg = if (enable) R.string.toast_alarm_enabled else R.string.toast_alarm_disabled
                                Toast.makeText(context, context.getString(msg), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * アラーム編集（モダンUI版）。
 * - セクション化されたカードで構成し、操作性と視認性を高める。
 */
@SuppressLint("DefaultLocale")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EditAlarmScreenModern(alarmId: Int?, onDone: () -> Unit, registerSave: ((() -> Unit) -> Unit)? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { DataStoreAlarmRepository(context) }
    val scheduler = remember { ScheduleManager(context) }
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }

    val nameState = remember { mutableStateOf("") }
    val hour = remember { mutableStateOf("7") }
    val minute = remember { mutableStateOf("0") }
    val policy = remember { mutableStateOf(HolidayPolicy.SAME) }
    val volumeModeState = remember { mutableStateOf(VolumeMode.SYSTEM) }
    val volumePercentState = remember { mutableFloatStateOf(100f) }
    val vibrateState = remember { mutableStateOf(false) }
    val respectSilentState = remember { mutableStateOf(true) }
    val soundMap = remember { mutableStateMapOf<WeatherCategory, Uri?>().apply { WeatherCategory.entries.forEach { put(it, null) } } }
    val holidaySound = remember { mutableStateOf<Uri?>(null) }
    val holidayOnly = remember { mutableStateOf(false) }

    val onPicked = remember { mutableStateOf<(Uri?) -> Unit>({}) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri: Uri? = if (android.os.Build.VERSION.SDK_INT >= 33) result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) else @Suppress("DEPRECATION") result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        onPicked.value(uri)
    }
    val days = remember { mutableStateListOf(
        DayOfWeek.MONDAY to true,
        DayOfWeek.TUESDAY to true,
        DayOfWeek.WEDNESDAY to true,
        DayOfWeek.THURSDAY to true,
        DayOfWeek.FRIDAY to true,
        DayOfWeek.SATURDAY to false,
        DayOfWeek.SUNDAY to false
    ) }

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
                soundMap.clear(); WeatherCategory.entries.forEach { k -> soundMap[k] = it.soundMapping[k] }
                holidaySound.value = it.holidaySound
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
            enabled = true
        )
        scope.launch {
            withContext(Dispatchers.IO) { repo.save(spec) }
            scheduler.scheduleNext(spec)
            Toast.makeText(context, context.getString(R.string.toast_alarm_saved), Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    // トップバーの保存アイコン用に登録
    LaunchedEffect(Unit) { registerSave?.invoke { doSave() } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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
                    trailingIcon = { if (nameState.value.isNotBlank()) IconButton(onClick = { nameState.value = "" }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.text_cancel)) } },
                    singleLine = true
                )
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        val timeText = remember(hour.value, minute.value) { String.format("%02d:%02d", hour.value.toIntOrNull() ?: 7, minute.value.toIntOrNull() ?: 0) }
                        Text(text = timeText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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

        // 曜日選択
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.label_days), style = MaterialTheme.typography.titleMedium)
                RowAlignCenter {
                    Text(stringResource(R.string.label_holiday_only), modifier = Modifier.weight(1f))
                    Switch(checked = holidayOnly.value, onCheckedChange = { holidayOnly.value = it })
                }
                if (!holidayOnly.value) {
                    DayOfWeekChips(days = days)
                } else {
                    Text(text = stringResource(R.string.hint_holiday_only_days_ignored), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 祝日ポリシー（セグメント化）
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.label_holiday_policy), style = MaterialTheme.typography.titleMedium)
                val options = listOf(HolidayPolicy.SAME, HolidayPolicy.DELAY, HolidayPolicy.SKIP)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, item ->
                        val selected = policy.value == item
                        val shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                        val (icon, label) = when (item) {
                            // ラベルはすべて短く1行で揃える（ヒントで詳細を補足）
                            HolidayPolicy.SAME -> Icons.Outlined.Schedule to stringResource(R.string.policy_same)
                            HolidayPolicy.DELAY -> Icons.Outlined.Snooze to stringResource(R.string.policy_delay)
                            HolidayPolicy.SKIP -> Icons.Outlined.Block to stringResource(R.string.policy_skip)
                        }
                        SegmentedButton(
                            modifier = Modifier.weight(1f),
                            selected = selected,
                            onClick = { if (!(holidayOnly.value && item == HolidayPolicy.SKIP)) policy.value = item },
                            shape = shape,
                            label = { Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                            icon = { Icon(icon, contentDescription = null) },
                            enabled = !(holidayOnly.value && item == HolidayPolicy.SKIP)
                        )
                    }
                }
                Text(
                    text = when (policy.value) {
                        HolidayPolicy.SAME -> stringResource(R.string.hint_policy_same)
                        HolidayPolicy.DELAY -> stringResource(R.string.hint_policy_delay_format, delayMins.intValue)
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
                Text(stringResource(R.string.label_weather_sounds), style = MaterialTheme.typography.titleMedium)
                // デフォルトサウンド（カード表示）
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.label_default_sound), style = MaterialTheme.typography.titleMedium)
                            val current = holidaySound.value
                            val sub = current?.let { ringtoneTitle(ctx, it) }
                                ?: stringResource(R.string.text_device_default)
                            Text(text = sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val current = holidaySound.value
                        IconButton(onClick = {
                            onPicked.value = { picked -> holidaySound.value = picked }
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                            }
                            picker.launch(intent)
                        }) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit)) }
                        if (current != null) {
                            IconButton(onClick = { holidaySound.value = null }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.text_cancel)) }
                        }
                    }
                }

                // 天気別サウンド（1行1天気, 雷は除外）
                val weatherCats = listOf(WeatherCategory.CLEAR, WeatherCategory.CLOUDY, WeatherCategory.RAIN, WeatherCategory.SNOW)
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    weatherCats.forEach { cat ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val (icon, tint) = when (cat) {
                                    WeatherCategory.CLEAR -> Icons.Outlined.WbSunny to MaterialTheme.colorScheme.tertiary
                                    WeatherCategory.CLOUDY -> Icons.Outlined.Cloud to MaterialTheme.colorScheme.secondary
                                    WeatherCategory.RAIN -> Icons.Outlined.WaterDrop to MaterialTheme.colorScheme.primary
                                    WeatherCategory.SNOW -> Icons.Outlined.AcUnit to MaterialTheme.colorScheme.primary
                                    else -> Icons.Outlined.WbSunny to MaterialTheme.colorScheme.primary
                                }
                                Icon(icon, contentDescription = null, tint = tint)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = weatherLabel(cat), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val current = soundMap[cat]
                                    val sub = current?.let { ringtoneTitle(ctx, it) } ?: stringResource(R.string.text_inherit_default)
                                    Text(text = sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                val current = soundMap[cat]
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = {
                                        onPicked.value = { picked -> soundMap[cat] = picked }
                                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                                        }
                                        picker.launch(intent)
                                    }) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit)) }
                                    IconButton(onClick = {
                                        val uri: Uri? = current ?: holidaySound.value
                                        val chosen = uri ?: (RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                                        val intent = Intent(ctx, RingingActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                            putExtra("id", -999)
                                            putExtra("soundUri", chosen.toString())
                                            putExtra("weatherLabel", weatherLabel(ctx, cat))
                                            putExtra("isHoliday", false)
                                            putExtra("holidayName", "")
                                            putExtra("volume", if (volumeModeState.value == VolumeMode.CUSTOM) (volumePercentState.floatValue / 100f) else 1f)
                                        }
                                        ContextCompat.startActivity(ctx, intent, null)
                                    }) { Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.action_preview)) }
                                    if (current != null) {
                                        IconButton(onClick = { soundMap[cat] = null }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.text_cancel)) }
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
                Text(stringResource(R.string.label_alarm_options), style = MaterialTheme.typography.titleMedium)
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
                            label = { Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
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
                            onValueChange = { volumePercentState.floatValue = (it * 100).coerceIn(0f, 100f) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors()
                        )
                        // 非推奨APIの置換：AutoMirrored版を利用して将来の互換性とRTL対応を確保
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null)
                        Text(text = "${volumePercentState.floatValue.toInt()}%")
                    }
                }
                // バイブレーション
                RowAlignCenter {
                    Text(stringResource(R.string.label_vibrate), modifier = Modifier.weight(1f))
                    Switch(checked = vibrateState.value, onCheckedChange = { vibrateState.value = it })
                }
                // マナー/サイレント踏襲
                RowAlignCenter {
                    Text(stringResource(R.string.label_respect_silent), modifier = Modifier.weight(1f))
                    Switch(checked = respectSilentState.value, onCheckedChange = { respectSilentState.value = it })
                }
                Text(text = stringResource(R.string.hint_respect_silent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 下部プレビューは各天気カードに統合したため削除

        // 保存
        Button(onClick = { doSave() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.text_save)) }
    }
}

/**
 * 設定画面（モダンUI）。
 * - 都市名/現在地が「天気予報取得のため」に使われることを先頭で明示。
 * - 取得元はセグメント切替で直感的に選択。
 * - 祝日のDELAY分、祝日データ自動更新もカードで整理。
 */
@Composable
private fun SettingsScreenModernNew() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val geocoding = remember { GeocodingRepository(context) }

    val lat = remember { mutableStateOf("35.0") }
    val lon = remember { mutableStateOf("135.0") }
    val useCurrent = remember { mutableStateOf(false) }
    val saving = remember { mutableStateOf(false) }
    val delayMinutes = remember { mutableStateOf("60") }
    val cityQuery = remember { mutableStateOf("") }
    val cityResults = remember { mutableStateListOf<GeoPlace>() }
    val selectedCityName = remember { mutableStateOf("") }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val searching = remember { mutableStateOf(false) }
    val holidayMonthly = remember { mutableStateOf(false) }
    val holidayWifiOnly = remember { mutableStateOf(true) }
    val holidayLastUpdatedText = remember { mutableStateOf<String?>(null) }
    val holidayRefreshing = remember { mutableStateOf(false) }
    val holidayRefreshMessage = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val s = withContext(Dispatchers.IO) { repo.settingsFlow.first() }
        lat.value = s.latitude.toString()
        lon.value = s.longitude.toString()
        useCurrent.value = s.useCurrentLocation
        selectedCityName.value = s.cityName ?: ""
        delayMinutes.value = s.delayMinutes.toString()
        holidayMonthly.value = s.holidayRefreshMonthly
        holidayWifiOnly.value = s.holidayRefreshWifiOnly

        // 最終更新の読み込み
        val key = androidx.datastore.preferences.core.longPreferencesKey(io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys.KEY_HOLIDAYS_LAST_FETCH)
        val last = withContext(Dispatchers.IO) { context.appDataStore.data.map { it[key] ?: 0L }.first() }
        holidayLastUpdatedText.value = if (last > 0L) {
            val dt = java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            context.getString(R.string.label_holiday_last_updated, dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")))
        } else context.getString(R.string.label_holiday_last_updated_never)
    }

    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            scope.launch {
                val loc = withContext(Dispatchers.IO) { getLastKnownCoarse(context) }
                loc?.let { lat.value = it.latitude.toString(); lon.value = it.longitude.toString() }
                useCurrent.value = true
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 天気の取得元
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RowAlignCenter {
                    Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = stringResource(R.string.settings_weather_header), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                Text(text = stringResource(R.string.settings_weather_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val items = listOf(
                        stringResource(R.string.settings_source_city),
                        stringResource(R.string.settings_source_current)
                    )
                    items.forEachIndexed { index, label ->
                        val selected = (index == if (useCurrent.value) 1 else 0)
                        SegmentedButton(
                            modifier = Modifier.weight(1f),
                            selected = selected,
                            onClick = { useCurrent.value = index == 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size)
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                // 現在地は「アラーム直前の天気先読み」で取得します
                Text(text = stringResource(R.string.settings_weather_timing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // 検索実行処理（ボタン/IME共通）
                fun performCitySearch() {
                    searching.value = true; errorMessage.value = null; cityResults.clear()
                    val query = cityQuery.value.trim()
                    scope.launch {
                        try {
                            if (query.isBlank()) {
                                errorMessage.value = context.getString(R.string.error_empty_query)
                            } else {
                                val results = withContext(Dispatchers.IO) { geocoding.searchCity(query) }
                                if (results.isEmpty()) errorMessage.value = context.getString(R.string.error_no_results) else cityResults.addAll(results)
                            }
                        } catch (e: Exception) {
                            errorMessage.value = context.getString(R.string.error_network_generic)
                        } finally { searching.value = false }
                    }
                }

                if (!useCurrent.value) {
                    OutlinedTextField(
                        value = cityQuery.value,
                        onValueChange = { cityQuery.value = it },
                        label = { Text(stringResource(R.string.hint_city_search)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        maxLines = 1,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performCitySearch() })
                    )
                    RowAlignCenter {
                        Button(enabled = !searching.value && cityQuery.value.isNotBlank(), onClick = { performCitySearch() }) {
                            Text(if (searching.value) stringResource(R.string.action_searching) else stringResource(R.string.action_search))
                        }
                    }
                    errorMessage.value?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                    if (selectedCityName.value.isNotBlank()) {
                        Text(text = stringResource(R.string.label_selected_city, selectedCityName.value), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (cityResults.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            items(cityResults, key = { it.id ?: (it.name + it.latitude + it.longitude).hashCode() }) { place ->
                                RowAlignCenter {
                                    val subtitle = listOfNotNull(place.admin1, place.country).joinToString(" / ")
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(place.name)
                                        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Button(onClick = {
                                        lat.value = place.latitude.toString(); lon.value = place.longitude.toString(); selectedCityName.value = place.name; cityResults.clear()
                                        Toast.makeText(context, context.getString(R.string.toast_city_selected), Toast.LENGTH_SHORT).show()
                                    }) { Text(stringResource(R.string.action_select)) }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                } else {
                    RowAlignCenter {
                        Button(onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                scope.launch {
                                    val loc = withContext(Dispatchers.IO) { getLastKnownCoarse(context) }
                                    loc?.let { lat.value = it.latitude.toString(); lon.value = it.longitude.toString() }
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        }) { Text(stringResource(R.string.action_detect_current_location)) }
                    }
                    Text(text = stringResource(R.string.settings_weather_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // 緯度経度のプレビューは非表示（UI簡素化）
            }
        }

        // 祝日のDELAY分（スライダー＋クイックプリセット）
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 現在値の表示（分）
                val delayValue = delayMinutes.value.toIntOrNull()?.coerceIn(0, 180) ?: 60
                RowAlignCenter {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.label_delay_minutes_holiday) + "（現在: ${delayValue}分）",
                        modifier = Modifier.weight(1f)
                    )
                }
                // 5分刻みで調整できるスライダー（0..180）
                Slider(
                    value = delayValue.toFloat(),
                    onValueChange = { v ->
                        val rounded = (v / 5f).roundToInt() * 5
                        delayMinutes.value = rounded.coerceIn(0, 180).toString()
                    },
                    valueRange = 0f..180f,
                    steps = 35,
                    modifier = Modifier.fillMaxWidth()
                )
                // クイックプリセット
                val presets = listOf(0, 30, 60, 90, 120)
                RowAlignCenter {
                    presets.forEach { m ->
                        FilterChip(
                            selected = delayValue == m,
                            onClick = { delayMinutes.value = m.toString() },
                            label = { Text("${m}分") },
                            modifier = Modifier
                        )
                    }
                }
                // 効果の説明（現在値を反映）
                Text(
                    text = stringResource(R.string.hint_policy_delay_format, delayValue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 祝日データ 自動更新
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.title_holiday_refresh), style = MaterialTheme.typography.titleMedium)
                RowAlignCenter {
                    Text(stringResource(R.string.label_holiday_refresh_monthly), modifier = Modifier.weight(1f))
                    Switch(checked = holidayMonthly.value, onCheckedChange = { holidayMonthly.value = it })
                }
                if (holidayMonthly.value) {
                    RowAlignCenter {
                        Text(stringResource(R.string.label_wifi_only), modifier = Modifier.weight(1f))
                        Switch(checked = holidayWifiOnly.value, onCheckedChange = { holidayWifiOnly.value = it })
                    }
                    Text(text = stringResource(R.string.hint_holiday_refresh), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                holidayLastUpdatedText.value?.let { txt ->
                    Text(text = txt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RowAlignCenter {
                    Button(
                        enabled = !holidayRefreshing.value,
                        onClick = {
                            holidayRefreshing.value = true
                            holidayRefreshMessage.value = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { io.github.whitenoise0000.shukutenalarm.holiday.HolidayRepository(context).forceRefresh() }
                                    val key = androidx.datastore.preferences.core.longPreferencesKey(io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys.KEY_HOLIDAYS_LAST_FETCH)
                                    val last = withContext(Dispatchers.IO) { context.appDataStore.data.map { it[key] ?: 0L }.first() }
                                    if (last > 0L) {
                                        val dt = java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                                        holidayLastUpdatedText.value = context.getString(R.string.label_holiday_last_updated, dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")))
                                    }
                                    holidayRefreshMessage.value = context.getString(R.string.text_refreshed)
                                    Toast.makeText(context, context.getString(R.string.toast_holidays_refreshed), Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    holidayRefreshMessage.value = context.getString(R.string.error_refresh_failed)
                                    Toast.makeText(context, context.getString(R.string.error_refresh_failed), Toast.LENGTH_SHORT).show()
                                } finally {
                                    holidayRefreshing.value = false
                                }
                            }
                        }
                    ) { Text(if (holidayRefreshing.value) stringResource(R.string.action_refreshing) else stringResource(R.string.action_refresh_now)) }
                }
                holidayRefreshMessage.value?.let { msg ->
                    Text(text = msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Button(
            enabled = !saving.value,
            onClick = {
                saving.value = true
                val latD = lat.value.toDoubleOrNull() ?: 35.0
                val lonD = lon.value.toDoubleOrNull() ?: 135.0
                val delay = delayMinutes.value.toIntOrNull()?.coerceIn(0, 180) ?: 60
                scope.launch {
                    withContext(Dispatchers.IO) {
                        repo.save(
                            io.github.whitenoise0000.shukutenalarm.settings.UserSettings(
                                latD, lonD, useCurrent.value, delay,
                                holidayRefreshMonthly = holidayMonthly.value,
                                holidayRefreshWifiOnly = holidayWifiOnly.value,
                                cityName = if (useCurrent.value) null else selectedCityName.value.takeIf { it.isNotBlank() }
                            )
                        )
                    }
                    if (holidayMonthly.value) {
                        io.github.whitenoise0000.shukutenalarm.work.HolidaysRefreshScheduler.schedule(context, wifiOnly = holidayWifiOnly.value)
                    } else {
                        io.github.whitenoise0000.shukutenalarm.work.HolidaysRefreshScheduler.cancel(context)
                    }
                    saving.value = false
                    Toast.makeText(context, context.getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.text_save)) }
    }
}
/**
 * アラーム1件分を表示するカードUI。
 * - 時刻/名称/曜日/祝日ポリシーを整然と表示し、有効スイッチと操作アイコンを提供する。
 */
@Composable
private fun AlarmCard(
    spec: AlarmSpec,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RowAlignCenter {
                Icon(Icons.Outlined.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                val title = spec.name.ifBlank { "#${spec.id}" }
                Text(
                    text = String.format("%02d:%02d", spec.time.hour, spec.time.minute),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit)) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
            }

            // 曜日と祝日ポリシーの表示をコンパクトにまとめる
            val daysLabel = run {
                val map = mapOf(
                    DayOfWeek.MONDAY to stringResource(R.string.dow_mon),
                    DayOfWeek.TUESDAY to stringResource(R.string.dow_tue),
                    DayOfWeek.WEDNESDAY to stringResource(R.string.dow_wed),
                    DayOfWeek.THURSDAY to stringResource(R.string.dow_thu),
                    DayOfWeek.FRIDAY to stringResource(R.string.dow_fri),
                    DayOfWeek.SATURDAY to stringResource(R.string.dow_sat),
                    DayOfWeek.SUNDAY to stringResource(R.string.dow_sun)
                )
                val ordered = listOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
                )
                val selected = if (spec.daysOfWeek.isEmpty()) ordered else ordered.filter { it in spec.daysOfWeek }
                selected.joinToString(" ") { map[it].orEmpty() }
            }
            val policyLabel = when (spec.holidayPolicy) {
                HolidayPolicy.SAME -> stringResource(R.string.policy_same)
                HolidayPolicy.DELAY -> stringResource(R.string.policy_delay)
                HolidayPolicy.SKIP -> stringResource(R.string.policy_skip)
            }

            RowAlignCenter {
                Text(
                    text = stringResource(R.string.label_days) + ": " + daysLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = policyLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            RowAlignCenter {
                Text(stringResource(R.string.label_enabled_toggle), modifier = Modifier.weight(1f))
                val checked = remember { mutableStateOf(spec.enabled) }
                val enabledState = checked
                Switch(
                    checked = checked.value,
                    onCheckedChange = { enabled ->
                        checked.value = enabled
                        enabledState.value = enabled
                        onToggle(enabled)
                    }
                )
            }
        }
    }
}

/**
 * 空状態の案内を表示する汎用コンポーネント。
 */
@Composable
private fun EmptyState(
    icon: @Composable (() -> Unit)? = null,
    title: String,
    message: String
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon?.invoke()
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * 曜日チップの操作コンポーネント。
 * - 週の各曜日をチップで表示し、タップでON/OFF切り替え。
 * - 2行に分けて視認性を確保。
 */
@Composable
private fun DayOfWeekChips(days: MutableList<Pair<DayOfWeek, Boolean>>) {
    val firstRow = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val secondRow = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    @Composable
    fun labelFor(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> stringResource(R.string.dow_mon)
        DayOfWeek.TUESDAY -> stringResource(R.string.dow_tue)
        DayOfWeek.WEDNESDAY -> stringResource(R.string.dow_wed)
        DayOfWeek.THURSDAY -> stringResource(R.string.dow_thu)
        DayOfWeek.FRIDAY -> stringResource(R.string.dow_fri)
        DayOfWeek.SATURDAY -> stringResource(R.string.dow_sat)
        DayOfWeek.SUNDAY -> stringResource(R.string.dow_sun)
    }

    RowAlignCenter {
        firstRow.forEach { d ->
            val idx = days.indexOfFirst { it.first == d }
            val selected = if (idx >= 0) days[idx].second else false
            FilterChip(
                selected = selected,
                onClick = { if (idx >= 0) days[idx] = days[idx].copy(second = !selected) },
                label = { Text(labelFor(d)) },
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
                label = { Text(labelFor(d)) },
                colors = FilterChipDefaults.filterChipColors()
            )
        }
    }
}

@Composable
private fun weatherLabel(cat: WeatherCategory): String = when (cat) {
    WeatherCategory.CLEAR -> stringResource(R.string.weather_clear)
    WeatherCategory.CLOUDY -> stringResource(R.string.weather_cloudy)
    WeatherCategory.RAIN -> stringResource(R.string.weather_rain)
    WeatherCategory.SNOW -> stringResource(R.string.weather_snow)
}

private fun weatherLabel(context: android.content.Context, cat: WeatherCategory): String = when (cat) {
    WeatherCategory.CLEAR -> context.getString(R.string.weather_clear)
    WeatherCategory.CLOUDY -> context.getString(R.string.weather_cloudy)
    WeatherCategory.RAIN -> context.getString(R.string.weather_rain)
    WeatherCategory.SNOW -> context.getString(R.string.weather_snow)
}

/**
 * Ringtone のタイトルを取得するユーティリティ。
 * - タイトル取得に失敗した場合は「不明」を返す。
 */
private fun ringtoneTitle(context: android.content.Context, uri: Uri): String =
    runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
        .getOrNull() ?: context.getString(R.string.text_unknown)

private fun getLastKnownCoarse(context: android.content.Context): Location? {
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
    for (p in providers) {
        runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let { return it }
    }
    return null
}

@Composable
private fun RowAlignCenter(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

















