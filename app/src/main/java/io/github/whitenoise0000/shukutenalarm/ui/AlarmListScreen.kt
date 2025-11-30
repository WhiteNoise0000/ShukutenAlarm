package io.github.whitenoise0000.shukutenalarm.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete

import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.whitenoise0000.shukutenalarm.R
import io.github.whitenoise0000.shukutenalarm.alarm.ScheduleManager
import io.github.whitenoise0000.shukutenalarm.data.DataStoreAlarmRepository
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.HolidayPolicy
import io.github.whitenoise0000.shukutenalarm.widget.NextAlarmWidgetProvider
import io.github.whitenoise0000.shukutenalarm.data.model.RepeatType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * アラーム一覧画面。
 * - アラームの追加、編集、削除、有効/無効切り替えを行う。
 */
@Composable
fun AlarmListScreen(onEdit: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { DataStoreAlarmRepository(context) }
    val scheduler = remember { ScheduleManager(context) }
    val list = remember { mutableStateListOf<AlarmSpec>() }
    val scope = rememberCoroutineScope()
    // 画面再表示（RESUME）時の最新化用トリガ。
    val refreshTick = remember { mutableIntStateOf(0) }
    // クイックタイマーダイアログ表示状態
    val showQuickTimerDialog = remember { mutableStateOf(false) }
    // クイックタイマー上書き確認ダイアログ表示状態
    val showConfirmDialog = remember { mutableStateOf(false) }
    // 選択された時間（確認ダイアログ用）
    val selectedTimeForConfirm = remember { mutableStateOf<LocalTime?>(null) }

    LaunchedEffect(Unit) {
        val items = withContext(Dispatchers.IO) { repo.list() }
        val sorted = items.sortedBy { it.time }
        list.clear(); list.addAll(sorted)
    }

    // 画面復帰（ON_RESUME）時に一覧を最新化し、カード側のスキップ表示も再計算させる。
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    val items = withContext(Dispatchers.IO) { repo.list() }
                    val sorted = items.sortedBy { it.time }
                    list.clear(); list.addAll(sorted)
                    refreshTick.intValue++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ウィジェット等からの更新ブロードキャストで即時再読込
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                scope.launch {
                    val items = withContext(Dispatchers.IO) { repo.list() }
                    val sorted = items.sortedBy { it.time }
                    list.clear(); list.addAll(sorted)
                    refreshTick.intValue++
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(NextAlarmWidgetProvider.ACTION_REFRESH)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickTimerDialog.value = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
            ) {
                Icon(Icons.Outlined.Timer, contentDescription = stringResource(R.string.action_add_quick_timer))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (list.isEmpty()) {
                // 空状態のメッセージ（初回導線を明確に）
                EmptyState(
                    icon = {
                        Icon(
                            Icons.Outlined.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = stringResource(R.string.empty_alarms_title),
                    message = stringResource(R.string.empty_alarms_message)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(list, key = { it.id }) { spec ->
                        AlarmCard(
                            spec = spec,
                            onEdit = { onEdit(spec.id) },
                            onDuplicate = {
                                scope.launch {
                                    val newId = withContext(Dispatchers.IO) { repo.duplicate(spec.id) }
                                    if (newId != null) {
                                        val items = withContext(Dispatchers.IO) { repo.list() }
                                        val sorted = items.sortedBy { it.time }
                                        list.clear(); list.addAll(sorted)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_alarm_duplicated),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        // ウィジェットへ更新通知（一覧変化）
                                        context.sendBroadcast(
                                            Intent(
                                                context,
                                                NextAlarmWidgetProvider::class.java
                                            ).setAction(NextAlarmWidgetProvider.ACTION_REFRESH)
                                        )
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    val items = withContext(Dispatchers.IO) {
                                        repo.delete(spec.id)
                                        scheduler.cancel(spec.id)
                                        repo.list()
                                    }
                                    val sorted = items.sortedBy { it.time }
                                    list.clear(); list.addAll(sorted)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_alarm_deleted),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // ウィジェットへ更新通知（一覧変化）
                                    context.sendBroadcast(
                                        Intent(
                                            context,
                                            NextAlarmWidgetProvider::class.java
                                        ).setAction(NextAlarmWidgetProvider.ACTION_REFRESH)
                                    )
                                }
                            },
                            onToggle = { enable ->
                                val updated = spec.copy(enabled = enable)
                                // UIの状態を即座に更新して見た目（カード色など）を反映させる
                                val index = list.indexOfFirst { it.id == spec.id }
                                if (index != -1) {
                                    list[index] = updated
                                }

                                scope.launch {
                                    withContext(Dispatchers.IO) { repo.save(updated) }
                                    if (enable) scheduler.scheduleNext(updated) else scheduler.cancel(
                                        updated.id
                                    )
                                    val msg =
                                        if (enable) R.string.toast_alarm_enabled else R.string.toast_alarm_disabled
                                    Toast.makeText(context, context.getString(msg), Toast.LENGTH_SHORT)
                                        .show()
                                    // ウィジェットへ更新通知（有効/無効切替）
                                    context.sendBroadcast(
                                        Intent(
                                            context,
                                            NextAlarmWidgetProvider::class.java
                                        ).setAction(NextAlarmWidgetProvider.ACTION_REFRESH)
                                    )
                                }
                            },
                            refreshKey = refreshTick.intValue
                        )
                    }
                }
            }
        }
    }

    // クイックタイマー作成関数
    val createQuickTimer: (LocalTime) -> Unit = { time ->
        scope.launch {
            val newId = withContext(Dispatchers.IO) { repo.createQuickTimer(time) }
            if (newId != null) {
                val items = withContext(Dispatchers.IO) { repo.list() }
                val sorted = items.sortedBy { it.time }
                list.clear(); list.addAll(sorted)
                // スケジューリング
                val newSpec = items.find { it.id == newId }
                if (newSpec != null) {
                    scheduler.scheduleNext(newSpec)
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_quick_timer_created),
                    Toast.LENGTH_SHORT
                ).show()
                // ウィジェットへ更新通知
                context.sendBroadcast(
                    Intent(
                        context,
                        NextAlarmWidgetProvider::class.java
                    ).setAction(NextAlarmWidgetProvider.ACTION_REFRESH)
                )
            }
        }
    }

    // クイックタイマーダイアログ
    if (showQuickTimerDialog.value) {
        QuickTimerDialog(
            onDismiss = { showQuickTimerDialog.value = false },
            onCreate = { time ->
                scope.launch {
                    val existing = withContext(Dispatchers.IO) { repo.list().find { it.isQuickTimer } }
                    if (existing != null) {
                        selectedTimeForConfirm.value = time
                        showConfirmDialog.value = true
                    } else {
                        createQuickTimer(time)
                    }
                }
                showQuickTimerDialog.value = false
            }
        )
    }

    // クイックタイマー上書き確認ダイアログ
    if (showConfirmDialog.value) {
        AlertDialog(
            onDismissRequest = {
                showConfirmDialog.value = false
                selectedTimeForConfirm.value = null
            },
            title = {
                Text(
                    text = stringResource(R.string.quick_timer_confirm_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.quick_timer_confirm_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTimeForConfirm.value?.let { createQuickTimer(it) }
                        showConfirmDialog.value = false
                        selectedTimeForConfirm.value = null
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog.value = false
                        selectedTimeForConfirm.value = null
                    }
                ) {
                    Text(stringResource(R.string.text_cancel))
                }
            }
        )
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
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
    /** 画面復帰や手動更新に同期して再計算するためのトリガ。*/
    refreshKey: Int = 0
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    // 1回スキップ状態（期限ミリ秒）
    val skipUntilLabel = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(spec.id, refreshKey) {
        val key =
            longPreferencesKey(io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys.KEY_SKIP_UNTIL_PREFIX + spec.id)
        val epoch =
            withContext(Dispatchers.IO) { context.appDataStore.data.map { it[key] ?: 0L }.first() }
        if (epoch > System.currentTimeMillis()) {
            val dt = java.time.Instant.ofEpochMilli(epoch).atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
            val fmt = DateTimeFormatter.ofPattern("M/d(E) H:mm")
            skipUntilLabel.value = dt.format(fmt)
        } else {
            skipUntilLabel.value = null
        }
    }
    // 無効時のカードスタイル調整
    val cardColors = if (spec.enabled) {
        CardDefaults.elevatedCardColors()
    } else {
        // 無効時は背景を少しグレー（SurfaceVariant）にし、コンテンツ色も薄くする
        CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }
    val cardElevation = if (spec.enabled) {
        CardDefaults.elevatedCardElevation()
    } else {
        // 無効時はフラットにする
        CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = cardColors,
        elevation = cardElevation,
        onClick = { if (!spec.isQuickTimer) onEdit() }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 無効時の表示色調整
            val disabledAlpha = 0.3f
            val primaryColor = if (spec.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
            val onSurfaceVariantColor = if (spec.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
            val mainTextColor = if (spec.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)

            // アラーム名は設定がある場合のみ、1行目に独立表示して見切れを防ぐ
            if (spec.name.isNotBlank()) {
                Text(
                    text = spec.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = onSurfaceVariantColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Alarm,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
                
                Text(
                    text = spec.time.formatHHMM(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = mainTextColor,
                    // 時刻はカードの主役とし、右側の操作アイコン群とのレイアウト競合を避けるため可変幅にする
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDuplicate, enabled = !spec.isQuickTimer, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.action_duplicate),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 1回スキップ状態の表示と解除
            if (skipUntilLabel.value != null) {
                RowAlignCenter {
                    Icon(
                        Icons.Outlined.Snooze,
                        contentDescription = null,
                        tint = if (spec.enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(
                            R.string.label_skip_until_format,
                            skipUntilLabel.value ?: ""
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariantColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = {
                        scope.launch {
                            val key =
                                longPreferencesKey(io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys.KEY_SKIP_UNTIL_PREFIX + spec.id)
                            withContext(Dispatchers.IO) {
                                context.appDataStore.edit { prefs ->
                                    prefs.remove(
                                        key
                                    )
                                }
                            }
                            skipUntilLabel.value = null
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_skip_cleared),
                                Toast.LENGTH_SHORT
                            ).show()
                            // ウィジェット更新
                            context.sendBroadcast(
                                Intent(
                                    context,
                                    NextAlarmWidgetProvider::class.java
                                ).setAction(NextAlarmWidgetProvider.ACTION_REFRESH)
                            )
                        }
                    }) { Text(stringResource(R.string.action_clear_skip), style = MaterialTheme.typography.labelSmall) }
                }
            }

            // 曜日・ポリシー・スイッチの行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // 曜日と祝日ポリシーの表示
                    if (!spec.isQuickTimer) {
                        val daysLabel = run {
                            val ordered = listOf(
                                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
                            )
                            val selected =
                                if (spec.daysOfWeek.isEmpty()) ordered else ordered.filter { it in spec.daysOfWeek }
                            selected.joinToString(" ") { it.getLabel(context) }
                        }
                        val policyLabel = when (spec.holidayPolicy) {
                            HolidayPolicy.SAME -> stringResource(R.string.policy_same)
                            HolidayPolicy.DELAY -> stringResource(R.string.policy_delay)
                            HolidayPolicy.SKIP -> stringResource(R.string.policy_skip)
                        }

                        Text(
                            text = stringResource(R.string.label_days) + ": " + daysLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariantColor
                        )
                        Text(
                            text = policyLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor
                        )
                    } else {
                         if (spec.repeatType == RepeatType.ONE_SHOT) {
                             Text(
                                 text = stringResource(R.string.label_one_shot_suffix),
                                 style = MaterialTheme.typography.bodySmall,
                                 color = onSurfaceVariantColor
                             )
                         }
                    }
                }

                // 有効/無効スイッチ
                val checked = remember(spec.id, refreshKey) { mutableStateOf(spec.enabled) }
                LaunchedEffect(spec.enabled, refreshKey) { checked.value = spec.enabled }
                Switch(
                    checked = checked.value,
                    onCheckedChange = { enabled ->
                        checked.value = enabled
                        onToggle(enabled)
                    },
                    enabled = !spec.isQuickTimer,
                    modifier = Modifier.padding(start = 8.dp)
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
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon?.invoke()
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
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
