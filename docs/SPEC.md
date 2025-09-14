# ShukutenAlarm 仕様書（JMA/GSI対応版・全体）

最終更新: 2025-09-11 / Asia/Tokyo

---

## 0. 概要

- 日本の祝日と天気に応じて動作するアラームアプリ。
- 祝日: ローカルマスタ（YAML）と更新スケジュールを持ち、常に最新へ同期。
- 天気: 気象庁（JMA）の予報APIと国土地理院（GSI）の逆ジオコーディングを利用。
- ネットワークは ETag/If-Modified-Since に対応し、304時はローカル本文を復元。

---

## 1. プロジェクト構成（Gradle Kotlin DSL）

- ルート: `build.gradle.kts`, `settings.gradle.kts`, `gradle/`
- アプリモジュール: `app/`
  - ソース: `app/src/main/java|kotlin/io/github/whitenoise0000/shukutenalarm/`
  - リソース: `app/src/main/res/`
  - マニフェスト: `app/src/main/AndroidManifest.xml`
  - テスト: `app/src/test/`, `app/src/androidTest/`

---

## 2. 主要機能

- アラーム（曜日・祝日対応・サウンド切替・バイブ・音量モード）
- 繰り返し: 週ごと（WEEKLY）/ 1回のみ（ONE_SHOT）。ONE_SHOT は曜日設定に従い最短の1回だけ鳴動し、鳴動後は自動で無効化（一覧に残す）。
- 天気に応じたサウンドマッピング（晴/曇/雨/雪）
- 現在地連動 or 都市名検索（ローカル）
- 次アラーム45分前（既定）に天気を先読み（DataStoreキャッシュ）
- 祝日の更新（WorkManagerによる定期/手動、Wi‑Fiのみオプション）
- ウィジェット（次回アラーム表示）

---

## 3. 画面/UI 概要

- メイン（アラーム一覧）: 有効/無効切替、編集/追加、次回時刻表示
- 編集（アラーム設定）: 時刻、曜日、祝日ポリシー、ボリューム、サウンド、バイブ等
- 設定: 天気取得（都市/現在地）、緯度経度、都市名検索、先読み分、マスタデータ更新（月次/Wi‑Fiのみ）、テスト取得
- ウィジェット: 次回アラーム時刻・タイトル・祝日状況

---

## 4. データモデル

- `WeatherCategory`: `CLEAR`, `CLOUDY`, `RAIN`, `SNOW`
- `HolidayPolicy`: `SKIP`, `DELAY`, `SAME`
- `VolumeMode`: `SYSTEM`, `CUSTOM`
- `AlarmSpec`（`app/src/main/java/.../data/model/Models.kt`）
  - `id`, `name`, `time`, `daysOfWeek`, `holidayPolicy`, `volumeMode`, `volumePercent`, `vibrate`, `respectSilentMode`, `holidayOnly`, `prefetchMinutes`, `soundMapping`, `holidaySound`, `enabled`
- 設定（DataStore）: `latitude`, `longitude`, `useCurrentLocation`, `delayMinutes`, `holidayRefreshMonthly`, `holidayRefreshWifiOnly`, `cityName`, `selectedOffice`, `selectedClass10`

---

## 5. 永続化（DataStore）

- キー定義: `app/src/main/java/.../data/PreferencesKeys.kt`
- 天気キャッシュ: `KEY_LAST_WEATHER_JSON`
  - 旧: `{"timestamp":epoch, "category":"..."}`
  - 新: `{"timestamp":epoch, "category":"...", "text":"くもり時々雨"}`（JMA文言をそのまま保存。無い場合は省略）
- 祝日最終取得: `KEY_HOLIDAYS_LAST_FETCH`
- 都市選択: `KEY_SELECTED_OFFICE`, `KEY_SELECTED_CLASS10`

---

## 6. 天気取得仕様（JMA/GSI）

### 6.1 現在地連動（位置ON時）

1) GSI 逆ジオ（LonLatToAddress）で `muniCd`（5桁）を取得
2) `class20` 候補 = `muniCd + "00"`
3) 候補が `area.json` の `class20s` に存在しない場合、政令指定都市の区と判断して市単位へ丸め（例: `14103` → `1410000`）
4) `class20 → class10 → office` を `area.json` の親子から決定
5) `forecast/{office}.json` を取得し、`areas[].area.code == class10` の `weathers`/`weatherCodes` からカテゴリを決定

代表例（合格条件）
- 渋谷区: `13113` → `class20=1311300` → `parent=130011` → `class10=130010` → `office=130000` → `forecast/130000.json`
- 横浜市の区: `14103` → 丸め `1410000` → 親子解決 → 予報取得

### 6.2 都市名検索（位置OFF時）

- `area.json` の `offices.name` と `class20s.name` をローカル部分一致で検索
- 選択した `office` と `class10`（分かれば）を保存。`class10` 未設定時は `office` 配下の先頭を使用

### 6.3 データソース

- 予報: `https://www.jma.go.jp/bosai/forecast/data/forecast/{office}.json`
- マスタ: `https://www.jma.go.jp/bosai/common/const/area.json`
- 逆ジオ: `https://mreversegeocoder.gsi.go.jp/reverse-geocoder/LonLatToAddress`

### 6.4 HTTPキャッシュ

- OkHttp インターセプタ（`EtagCacheInterceptor`）で ETag/If-Modified-Since を付与
- 304 Not Modified 時は `CacheStore` に保存済み本文を差し込み 200 として返却
- 対象: `area.json`, `forecast/{office}.json`

### 6.5 カテゴリ正規化

- 文言優先（`weathers` があれば使用、無ければ `weatherCodes`）
- 文言→`WeatherCategory`: 雪 > 雨/雷 > 曇 > 晴
- コード→`WeatherCategory`（先頭桁）: 1=晴, 2=曇, 3=雨, 4=雪, 5=雨（雷雨）

---

## 7. 祝日仕様

- マスタ: `app/src/main/assets/holidays.yml`
- `HolidayRepository` が読み込み/問い合わせ/更新管理
- 設定に「月次更新（Wi‑Fiのみ）」を用意。即時更新ボタンあり
- 祝日名称の参照・当日判定 API を提供

---

## 8. アラーム動作

- `AlarmGateway` がOSへスケジュール登録し、時刻到来で `AlarmReceiver` が起動
- 鳴動条件: `HolidayPolicy` と当日の祝日判定に基づく（`SoundSelector.shouldRing`）
- 天気取得: DataStoreキャッシュがあれば優先。なければ `WeatherFetchWorker` 相当の処理を 10 秒タイムアウトで実行
- 鳴動UI: `RingingActivity`（FSI優先。不可の場合は通常通知 + FSI設定への誘導）
- 次回分の再スケジュールとウィジェット更新を行う（ONE_SHOT は鳴動後に自動無効化し、再スケジュールしない）

---

## 9. スケジューリング/バックグラウンド

- `ScheduleManager` がアラーム（次回）を計算し OS へ登録
- `BootReceiver` が再起動時に再登録
- `WeatherFetchWorker` が設定に基づくタイミングで天気を先読みして DataStore に保存
- `HolidaysRefreshWorker` は「マスタデータ更新」として、祝日YAMLとJMAの area.json（エリアマスタ）を月次で更新（Wi‑Fiのみの制約可）。area.json は ETag/IMS により304時はローカル本文を利用

---

## 10. 通知/FSI

- `Notifications` ユーティリティでフルスクリーン通知の発行
- 通知チャンネル: アラーム専用チャンネルを作成（高優先度、サウンド/バイブ設定はアプリ側音量ポリシーに従う）
- Android 14+ の FSI 要件に準拠
  - FSI の権限/設定が未許可の場合、通常の高優先度通知＋設定画面への導線を提示
  - 鳴動直前にFSIが使えない場合でも、確実にユーザーへフォールバック通知が届くよう設計

---

## 11. 権限

- `ACCESS_COARSE_LOCATION`（現在地ON時に使用）
- `POST_NOTIFICATIONS`（Android 13+）
- `USE_FULL_SCREEN_INTENT`（フルスクリーン通知）
- `RECEIVE_BOOT_COMPLETED`（再起動時の再登録）
- `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`（必要に応じて案内）
- `INTERNET`（天気/マスタ取得）

---

## 12. QA/エッジケース

- 位置権限未許可の場合（現在地ON）
  - 先読み/手動取得ともに「直近の保存済み都市（office/class10）」または設定の緯度経度をフォールバックに使用
  - 設定UIで権限リクエストを再提示可能
- 検索ヒットしない場合
  - JMA area.json 準拠の正式名称（市/区）で再試行
  - 同名が複数の都道府県に存在する場合、候補一覧からoffice/class10を確認して選択
- 予報取得で `weathers` が空の場合
  - `weatherCodes` からカテゴリを正規化
  - それも無い場合はCLOUDYをデフォルトとし、ログへ記録

---

## 12. ネットワーク/DI

- Retrofit+OkHttp+kotlinx.serialization
- DI: `AppModule` にて JSON/OkHttp（ETagインターセプタ）/Retrofit(JMA/GSI)/API/Repository を提供

---

## 13. ビルド・実行・テスト

- デバッグAPK: `./gradlew assembleDebug`
- 単体テスト: `./gradlew testDebugUnitTest`
- 計測テスト: `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`
- 端末へインストール: `./gradlew installDebug`

---

## 14. コーディング規約

- 言語: Kotlin、インデント4スペース、公開APIにKDoc
- 命名: クラス/オブジェクト=PascalCase、関数/変数=camelCase、定数=UPPER_SNAKE_CASE
- リソース命名: スネークケース（layout: `activity_*`/`fragment_*`、drawable: `ic_*`/`bg_*`）
- 文字列/色はリソース経由。マジックナンバー回避
- ファイル入出力はUTF‑8(BOMなし)

---

## 15. セキュリティと設定

- `local.properties` 等の秘情報はコミットしない
- ビルド時設定は `gradle.properties` と `BuildConfig` を活用
- 公開ビルドでは `app/proguard-rules.pro` を管理

---

## 16. PR/CI ガイド

- コミットメッセージは命令形・現在形。粒度は小さく、Issue を関連付け
- PRには目的/背景/変更点、テスト観点、動作確認端末/APIレベル、UI変更のスクショ/録画を添付
- CIはビルド/テスト/Lint 合格後にレビュー依頼

---

## 17. 受け入れ条件（天気関連）

- 渋谷区座標 → `forecast/130000.json`（code=130010）でカテゴリ取得
- 横浜市“区”座標 → 丸めロジックで予報取得
- `area.json` と `forecast/{office}.json` の双方でETagキャッシュ動作（304時にローカル使用）

---

## 18. 既知事項/将来課題

- エッジケース（離島や境界付近）での class20 丸め精度の検証強化
- UI での `class10` 任意選択のUX改善（説明/候補提示）
