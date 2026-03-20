package io.github.whitenoise0000.shukutenalarm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whitenoise0000.shukutenalarm.R
import java.time.LocalTime

/**
 * クイックタイマー登録ダイアログ。
 * 時間選択リストからタイマーを選択し、コールバックでアラームを作成する。
 */
@Composable
fun QuickTimerDialog(
    onDismiss: () -> Unit,
    initialRespectSilentMode: Boolean,
    onCreate: (LocalTime, Boolean) -> Unit
) {
    val selectedTime = remember { mutableStateOf<LocalTime?>(null) }
    val selectedIndex = remember { mutableStateOf(-1) }
    val showDetails = remember { mutableStateOf(false) }
    val respectSilentState = remember(initialRespectSilentMode) {
        mutableStateOf(initialRespectSilentMode)
    }

    // 時間選択オプションのリスト
    val timeOptions = remember {
        val options = mutableListOf<Pair<String, LocalTime>>()

        // 10分まで1分刻み
        for (min in 1..10) {
            val time = LocalTime.now().plusMinutes(min.toLong())
            options.add("${min}分" to time)
        }

        // 30分まで5分刻み (15,20,25,30)
        for (min in 15..30 step 5) {
            val time = LocalTime.now().plusMinutes(min.toLong())
            options.add("${min}分" to time)
        }

        // 1時間まで10分刻み (40,50,60)
        for (min in 40..60 step 10) {
            val time = LocalTime.now().plusMinutes(min.toLong())
            options.add("${min}分" to time)
        }

        // 3時間まで1時間刻み (2h,3h)
        for (hour in 2..3) {
            val time = LocalTime.now().plusHours(hour.toLong())
            options.add("${hour}時間" to time)
        }

        options
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.quick_timer_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    itemsIndexed(timeOptions) { index, item ->
                        val (label, time) = item
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selectedIndex.value == index) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable {
                                    selectedIndex.value = index
                                    selectedTime.value = time
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                TextButton(
                    onClick = { showDetails.value = !showDetails.value },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        stringResource(
                            if (showDetails.value) R.string.action_hide_quick_timer_details
                            else R.string.action_show_quick_timer_details
                        )
                    )
                }

                AnimatedVisibility(visible = showDetails.value) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.label_quick_timer_details),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.label_respect_silent),
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Switch(
                                checked = respectSilentState.value,
                                onCheckedChange = { respectSilentState.value = it }
                            )
                        }
                        Text(
                            text = stringResource(R.string.hint_quick_timer_respect_silent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedTime.value?.let { onCreate(it, respectSilentState.value) }
                    onDismiss()
                },
                enabled = selectedTime.value != null
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.text_cancel))
            }
        }
    )
}
