package io.github.whitenoise0000.shukutenalarm.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * アラーム編集画面のプレースホルダー。
 * - 仕様に基づき、将来的に時刻/曜日/祝日ポリシー/サウンド割当を編集できるようにする。
 */
class EditAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EditAlarmPlaceholder() }
    }
}

@Composable
private fun EditAlarmPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Edit Alarm - 準備中")
    }
}

