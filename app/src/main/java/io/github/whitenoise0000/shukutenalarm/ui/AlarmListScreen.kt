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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
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

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            LazyColumn {
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
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // アラーム名は設定がある場合のみ、1行目に独立表示して見切れを防ぐ
            if (spec.name.isNotBlank()) {
                Text(
                    text = spec.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            RowAlignCenter {
                Icon(
                    Icons.Outlined.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = spec.time.formatHHMM(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    // 時刻を可変幅にして、右側の操作アイコン群との競合を避ける
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDuplicate) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.action_duplicate)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.action_edit)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.action_delete)
                    )
                }
            }

            // 曜日と祝日ポリシーの表示をコンパクトにまとめる
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

            // 1回スキップ状態の表示と解除
            if (skipUntilLabel.value != null) {
                RowAlignCenter {
                    Icon(
                        Icons.Outlined.Snooze,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = stringResource(
                            R.string.label_skip_until_format,
                            skipUntilLabel.value ?: ""
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    }) { Text(stringResource(R.string.action_clear_skip)) }
                }
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
