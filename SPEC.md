# Androidアラーム×祝日×天気アプリ 仕様書（個人利用・非公開）v1.0

最終更新: 2025-09-06 / Asia/Tokyo

---

## 0. 背景と目的

- Pixel を想定した「個人利用向け」Android アプリ。
- 目覚まし（アラーム）を主機能とし、日本の祝日と当日の天気で鳴動ロジックやサウンドを切り替える。
- 配布は Play ストア想定外（開発・検証用、ADB サイドロード）。
- データは端末内完結（祝日データは同梱、天気は Open‑Meteo から取得・キー不要）。

---

## 1. スコープ

- 祝日・天気に応じてアラーム動作やサウンドを切り替え。
- 祝日判定はオフライン優先（同梱データ）。
- 天気は Open‑Meteo の `/v1/jma` を利用（キー不要）。
- 位置情報は大まかな位置（COARSE）で十分。GPS は不要。
- 正確な時刻で鳴ることを最優先（AlarmManager の exact 系 API とフルスクリーン通知）。

非スコープ（v1では未対応）

- ストア公開・課金、解析 SDK、クラウド保存。
- 複数端末同期、バックアップ/復元のUI（内部的に最小限のバックアップ設定は保持）。

---

## 2. ターゲット/互換

- 対象OS: Android 13+（minSdk=33）、target/compileSdk は最新に追随。
- 端末: Pixel 6 以降で検証（他端末はベストエフォート）。

---

## 3. ユースケース

1. 平日 7:00 にアラーム。祝日は 8:00 に遅らせる（DELAY）またはスキップ（SKIP）。
2. 雨予報なら起床前 45 分（設定可）に WorkManager で天気を先読みし、対応サウンドに切り替える。
3. 取得失敗時でも既定時刻にデフォルト音で確実に鳴動。

---

## 4. 非機能要件

- 信頼性: Doze/省電力下でも時刻通りに鳴動（`setAlarmClock`/FSI）。
- オフライン耐性: 祝日データは同梱（YAML）、天気はキャッシュ/フォールバックあり。
- セキュリティ/プライバシー: 端末内完結（位置・設定はローカル保存）。
- 性能/電池: 天気取得は 1 日 1 回相当、またはアラーム前の先読みのみ。

---

## 5. 技術スタック（無償）

- 言語/UI: Kotlin, Jetpack Compose, Material3
- DI: Hilt
- 永続化: DataStore (Preferences)
- バックグラウンド: AlarmManager（鳴動）/ WorkManager（天気先読み・祝日更新）
- ネットワーク: Retrofit + kotlinx.serialization + OkHttp Logging

---

## 6. アーキテクチャ

- MVVM + Repository 構成。シングルアクティビティ + Compose Navigation。
- レイヤ: `ui`（Compose）/ `data`（Repository, DataStore, Network）/ `platform`（Alarm, Work, Notification, Permissions）。

推奨モジュール構成（将来分割）

- `:app`  … UI/ナビゲーション/DI
- `:core:alarm` … AlarmManager ゲートウェイ、正確なアラーム権限UI
- `:core:work` … WorkManager ジョブ
- `:core:holiday` … 祝日ローダ/判定（assets/holidays.yml）
- `:core:weather` … Open‑Meteo クライアント & マッピング
- `:core:data` … DataStore/Repository 共通

---

## 7. 主要フロー

1. アラーム登録: UI→AlarmGateway→`setAlarmClock()` で exact 登録。
2. 事前天気取得: アラーム時刻の N 分前（既定 45）に `OneTimeWorkRequest` をスケジュール（ネットワーク接続時）。
3. 鳴動: BroadcastReceiver→フルスクリーン Activity 起動、Media3/Ringtone で再生（USAGE_ALARM）。
4. 音源選択: 祝日か否かは鳴動可否（SKIP/DELAY/SAME）にのみ影響。サウンドは WeatherCategory のマッピングを使用し、未設定時はデフォルトにフォールバック（祝日優先ロジックなし）。

---

## 8. 権限/ポリシー設計

- 正確なアラーム:
  - 個人配布前提のため `USE_EXACT_ALARM` を宣言。
  - 公開する場合は `SCHEDULE_EXACT_ALARM` と許可誘導UI（`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`）を検討。
- 通知: Android 13+ で `POST_NOTIFICATIONS`（初回に説明）。
- 位置情報: `ACCESS_COARSE_LOCATION` を使用（高精度は不要）。
- フルスクリーン通知: `USE_FULL_SCREEN_INTENT`（アラーム用途）。

---

## 9. 天気カテゴリ（WMO→アプリ）

- WMO `weathercode` をアプリ内カテゴリへマッピング。
  - Clear: `0`
  - Cloudy: `1..3`
  - Rain/Drizzle: `51..57, 61..65, 80..82`
  - Snow: `71..75, 85, 86`
  - Thunder: `95..99`

Kotlin 例：

```kotlin
fun Int.toCategory(): WeatherCategory = when (this) {
  0 -> WeatherCategory.CLEAR
  in 1..3 -> WeatherCategory.CLOUDY
  in 51..57, in 61..65, in 80..82 -> WeatherCategory.RAIN
  in 71..75, 85, 86 -> WeatherCategory.SNOW
  in 95..99 -> WeatherCategory.THUNDER
  else -> WeatherCategory.CLOUDY
}
```

---

## 10. 位置取得/キャッシュ

- 位置は手動設定を基本（緯度経度を設定画面に保存）。
- 任意で現在地取得（COARSE）。「現在地を使用」有効時は、天気先読み（WorkManager 実行時）に端末の現在地を動的取得してから天気を取得する（権限未許可/取得不可時は保存座標にフォールバック）。
- 天気の直近取得結果は DataStore に保存（当日限り想定）。

---

## 11. データモデル抜粋

```kotlin
enum class WeatherCategory { CLEAR, CLOUDY, RAIN, SNOW, THUNDER }

data class AlarmSpec(
  val id: Int,
  val time: LocalTime,
  val daysOfWeek: Set<DayOfWeek>,
  val holidayPolicy: HolidayPolicy, // SKIP, DELAY, SAME
  val prefetchMinutes: Int = 45,
  val soundMapping: Map<WeatherCategory, Uri>,
  // 祝日優先ロジックは廃止。holidaySound は天気別が未設定時のフォールバックとしてのみ使用。
  val holidaySound: Uri? = null,
  val enabled: Boolean = true
)

enum class HolidayPolicy { SKIP, DELAY, SAME }
```

DataStore（Preferences）キー例

- `lat`, `lon`, `useCurrentLocation`
- `alarm_<id>_json`
- `last_weather_json`
- `holidays_last_fetch_epoch`

---

## 12. 画面（Compose）

- ホーム: アラーム一覧（ON/OFF、時刻、曜日、祝日ポリシー、設定概要）。FABで追加。
- 編集: 時刻、曜日、祝日ポリシー、天気別サウンド（晴/曇/雨/雪）。
- 設定: 位置設定（手動/現在地）、通知と正確アラームの説明、祝日データ更新、デバッグ（直近天気ログ）。
- 鳴動: FSIで前面表示。スヌーズ/停止、現在の天気ラベル、祝日ラベル表示。

---

## 13. Manifest 抜粋（実装準拠）

```xml
<manifest>
  <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
  <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
  <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
  <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
  <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.VIBRATE" />
  <!-- 公開する場合は以下も検討
  <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
  -->
  <application>
    <receiver android:name=".alarm.AlarmReceiver" android:exported="false" />
    <receiver android:name=".alarm.BootReceiver" android:enabled="true" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
      </intent-filter>
    </receiver>
    <activity android:name=".ui.RingingActivity"
              android:showOnLockScreen="true"
              android:turnScreenOn="true"
              android:exported="false" />
    <activity android:name=".ui.MainActivity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
    </activity>
  </application>
</manifest>
```

---

## 14. アラーム実装（抜粋）

```kotlin
class AlarmGateway @Inject constructor(
  private val context: Context
) {
  fun scheduleExactAlarm(spec: AlarmSpec, date: LocalDate) {
    val trigger = ZonedDateTime.of(date, spec.time, ZoneId.systemDefault())
      .toInstant().toEpochMilli()

    val ringIntent = PendingIntent.getBroadcast(
      context, spec.id,
      Intent(context, AlarmReceiver::class.java).setAction("ALARM_FIRE").putExtra("id", spec.id),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val fsiIntent = PendingIntent.getActivity(
      context, spec.id,
      Intent(context, RingingActivity::class.java).putExtra("id", spec.id),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val info = AlarmManager.AlarmClockInfo(trigger, fsiIntent)
    context.getSystemService(AlarmManager::class.java)
      ?.setAlarmClock(info, ringIntent)
  }
}
```

---

## 15. WorkManager（天気先読み）

```kotlin
class PrefetchWeatherWorker(
  appContext: Context,
  params: WorkerParameters,
  private val repo: WeatherRepository
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = try {
    repo.prefetchToday()
    Result.success()
  } catch (e: Exception) {
    Result.retry()
  }
}

fun schedulePrefetch(context: Context, spec: AlarmSpec, date: LocalDate) {
  val whenMillis = ZonedDateTime.of(date, spec.time, ZoneId.systemDefault())
    .minusMinutes(spec.prefetchMinutes.toLong())
    .toInstant().toEpochMilli()

  val delay = whenMillis - System.currentTimeMillis()
  val work = OneTimeWorkRequestBuilder<PrefetchWeatherWorker>()
    .setInitialDelay(delay.coerceAtLeast(0), TimeUnit.MILLISECONDS)
    .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
    .addTag("prefetch-${spec.id}")
    .build()
  WorkManager.getInstance(context).enqueueUniqueWork("prefetch-${spec.id}", ExistingWorkPolicy.REPLACE, work)
}

// 実装注記: 「現在地を使用」時は Worker 内で COARSE 位置を取得してから API を叩く。
// 許可がない/取得不可の場合は保存された緯度経度を使用してフォールバックする。
```

---

## 16. Open‑Meteo API インタフェース（Retrofit）

```kotlin
interface OpenMeteoApi {
  @GET("/v1/jma")
  suspend fun jma(
    @Query("latitude") lat: Double,
    @Query("longitude") lon: Double,
    @Query("hourly") hourly: String = "weathercode,precipitation_probability",
    @Query("timezone") tz: String = "Asia/Tokyo"
  ): JmaResponse
}
```

---

## 17. 祝日データ（assets 同梱, YAML）

実装は `assets/holidays.yml` を同梱し、簡易パースで `YYYY-MM-DD: 祝日名` を読み込む。初回は assets、以後は `filesDir/holidays.yml` をキャッシュし、必要に応じオンライン更新（`holiday-jp` 公式YAMLのraw）を行う。

例（抜粋）

```kotlin
class HolidayRepository(private val context: Context) {
  private val cache = File(context.filesDir, "holidays.yml")
  @Volatile private var memory: Map<String, String>? = null

  fun isHoliday(date: LocalDate): Boolean = ensure().containsKey(date.toString())
  fun nameOrNull(date: LocalDate): String? = ensure()[date.toString()]

  private fun ensure(): Map<String, String> {
    memory?.let { return it }
    val text = if (cache.exists()) cache.readText() else context.assets.open("holidays.yml").bufferedReader().use { it.readText() }
    val parsed = parseYaml(text)
    memory = parsed
    return parsed
  }

  private fun parseYaml(yaml: String): Map<String, String> = buildMap {
    yaml.lineSequence().forEach { line ->
      val t = line.trim()
      if (t.isEmpty() || t.startsWith("#") || t.startsWith("---")) return@forEach
      val idx = t.indexOf(":")
      if (idx > 0) {
        val d = t.substring(0, idx).trim()
        val n = t.substring(idx + 1).trim()
        if (d.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) && n.isNotEmpty()) put(d, n)
      }
    }
  }
}
```

---

## 18. 通知/鳴動

- チャンネル: `ALARM`（IMPORTANCE_HIGH）。
- フルスクリーン通知（FSI）で `RingingActivity` を前面化。
- 音再生: `AudioAttributes.USAGE_ALARM` を使用。
- フォールバック: 再生失敗時は `RingtoneManager.getDefaultUri(TYPE_ALARM)` 等。

---

## 19. ビルド/配布

1. keystore を生成しリリース署名 APK を作成。
2. Pixel で開発者モード＋USBデバッグを有効化。
3. `adb install -r app-release.apk` で更新配布。

---

## 20. テスト計画

- Unit: WMO→カテゴリ、祝日判定、サウンド選択。
- Robolectric: Alarm/Work スケジュールのユニット検証。
- Instrumented/実機: Doze/ロック画面/音量・FSI 挙動、電池消費トレードオフ。

---

## 21. 受け入れ基準（例）

- 祝日サンプル: 成人の日に `HolidayPolicy=DELAY` で設定分だけ後ろ倒しになる。
- 雨予報の日に雨用サウンドへ切り替わる（先読み成功時）。
- 先読み失敗時でも既定時刻にデフォルト音で鳴る。
- ロック画面中に FSI で鳴動画面が前面化する。

---

## 22. 開発メモ/ブレークダウン

```
root
├─ app/                # Compose UI, Navigation, DI
├─ core/alarm/         # Alarm & FSI gateway
├─ core/work/          # WorkManager jobs
├─ core/holiday/       # holiday loader & utils (YAML)
├─ core/weather/       # Open-Meteo client & mapping
├─ core/data/          # DataStore/Repo
├─ assets/holidays.yml
└─ SPEC.md             # 本仕様書（ルート）
```

### 依存関係（Gradle, 抜粋）

```kotlin
implementation("androidx.activity:activity-compose:<latest>")
implementation("androidx.compose.material3:material3:<latest>")
implementation("androidx.navigation:navigation-compose:<latest>")
implementation("androidx.work:work-runtime-ktx:<latest>")
implementation("androidx.datastore:datastore-preferences:<latest>")
implementation("com.google.dagger:hilt-android:<latest>")
kapt("com.google.dagger:hilt-compiler:<latest>")
implementation("com.squareup.retrofit2:retrofit:<latest>")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<latest>")
implementation("com.squareup.okhttp3:logging-interceptor:<latest>")
implementation("com.google.android.exoplayer:exoplayer:<latest>")
```

### ビルド条件

- minSdk=33、target/compile は最新（現状 36）。
- `compileOptions`/`kotlinOptions` は Java/Kotlin 17 相当。

### 作業ブレークダウン

1. プロジェクト初期化（Compose, Hilt, WorkManager 依存追加）。
2. `assets/holidays.yml` 同梱と `HolidayRepository` 実装。
3. Open‑Meteo クライアントと `WeatherRepository` 実装（WMO→カテゴリ）。
4. `AlarmGateway` と FSI 起動アクティビティ実装（音再生）。
5. `WeatherFetchWorker` とスケジュール連携。
6. Compose 画面（一覧/編集/設定/鳴動）。
7. 受け入れテストシナリオの自動化（Robolectric/Instrumentation）。

---

## 23. 既知の論点/メモ

- `USE_EXACT_ALARM` は個人配布では便利だが、Play 公開時はポリシー要件あり。公開予定が出たら `SCHEDULE_EXACT_ALARM` に切替と許可誘導UIを要検討。
- フルスクリーン通知は Android 14+ の制限により「通話/アラーム」に限定。乱用不可。
- DND 中の挙動はベンダー差があるため、実機での検証を推奨。

---

## 24. ホームウィジェット（実装）

- 提供ウィジェット: 2x1「次回アラーム」ウィジェット（ホーム画面）。
- 表示仕様:
  - 時刻: 「H:mm」（大きめ/太字）。
  - 日付: 「M/d(E)」。祝日の場合は末尾に「 [祝日]」を付与。
  - タイトル: 「次回」。
- 操作仕様:
  - 本体タップ: アプリ起動（`MainActivity`）。
  - タイトルタップ: ウィジェット内容を手動更新。
  - 「スキップ」ボタン: 直近のアラームを1回だけスキップ（現在の登録をキャンセル → 設定に基づき次回以降を再スケジュール）。
- ロジック整合:
  - 次回候補計算は `NextAlarmCalculator` を使用（祝日ポリシー SKIP/DELAY/SAME、`holidayOnly`、`delayMinutes` に準拠）。
  - スキップ動作は `ScheduleManager.cancel(id)` 後に `ScheduleManager.scheduleNext(spec)` で再登録。
- デザイン:
  - 角丸カード＋ダーク系グラデーション背景、半透明の角丸ボタン（モダン・シンプル）。
  - RemoteViews ベース実装（Compose Glance 未採用）。
- 更新ポリシー:
  - `updatePeriodMillis=0`（自動更新なし）。ユーザー操作（スキップ/タイトルタップ）時に明示更新。
  - 将来拡張: アラーム設定変更時や `AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED` 受信での更新を検討。
- マニフェスト（抜粋）:
  ```xml
  <receiver
      android:name=".widget.NextAlarmWidgetProvider"
      android:exported="false">
    <intent-filter>
      <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
      android:name="android.appwidget.provider"
      android:resource="@xml/next_alarm_widget_info" />
  </receiver>
  ```
- メタデータ（`res/xml/next_alarm_widget_info.xml` 抜粋）:
  ```xml
  <appwidget-provider
      android:minWidth="132dp"
      android:minHeight="58dp"
      android:updatePeriodMillis="0"
      android:initialLayout="@layout/widget_next_alarm_2x1"
      android:resizeMode="horizontal|vertical"
      android:widgetCategory="home_screen" />
  ```
---

## 25. 今後拡張

- バックアップ/リストア（`DocumentFile` 経由でJSON書出し）。

以上。
