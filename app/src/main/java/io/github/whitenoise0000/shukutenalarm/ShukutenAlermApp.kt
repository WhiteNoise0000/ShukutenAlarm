package io.github.whitenoise0000.shukutenalarm

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * アプリケーションのエントリポイント。
 * - Hilt を有効化し、アプリ全体の DI コンテナを初期化する。
 */
@HiltAndroidApp
class ShukutenAlermApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 通知チャンネルを初期化
        io.github.whitenoise0000.shukutenalarm.platform.Notifications.ensureChannels(this)
        // 祝日データのオンライン更新をバックグラウンドで試行（30日以上古い場合）
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                io.github.whitenoise0000.shukutenalarm.holiday.HolidayRepository(this@ShukutenAlermApp)
                    .refreshIfStale(maxAgeDays = 30)
            }
        }
    }
}
