# ShukutenAlarm

Androidで祝日・天気に応じてサウンドを切り替えるアラームアプリです。

- 天気は気象庁（JMA）の予報API＋国土地理院（GSI）逆ジオコーディングを使用します。
  - 仕様詳細: `docs/SPEC.md`
  - 変更点サマリ: `docs/JMA_SPEC_UPDATE.md`

## 特徴

- 祝日ポリシー（SKIP/DELAY/SAME）に対応
- 現在地連動 または 都市名検索（ローカル）で予報エリアを決定
- ETag/If-Modified-Since によるHTTPキャッシュ（area.json/forecast/{office}.json）
- 次アラーム45分前（既定）に天気を先読みし、DataStoreへキャッシュ
- ウィジェットで次回アラームを表示

## 技術スタック

- UI: Jetpack Compose
- アーキテクチャ: MVVM
- DI: Hilt
- 非同期: Kotlin Coroutines, WorkManager
- データ永続化: Jetpack DataStore
- ネットワーク: Retrofit2, OkHttp3, Kotlinx.Serialization

## ビルド手順

1) クローン
```
git clone https://github.com/WhiteNoise0000/ShukutenAlarm.git
```
2) Android Studio で開く（Hedgehog 以降推奨）
3) ビルド
```
./gradlew assembleDebug
```

## 使い方（要点）

- 設定画面で「都市名検索」または「現在地連動」を選択
- 必要に応じて「今すぐ取得」で天気取得をテスト
- アラーム編集で曜日/祝日ポリシー/サウンドを設定
- 以降は次アラーム45分前に天気を自動先読みして鳴動時に反映

## 権限

- `ACCESS_COARSE_LOCATION`: 現在地連動で使用
- `POST_NOTIFICATIONS`: Android 13+ の通知許可
- `USE_FULL_SCREEN_INTENT`: 鳴動時のフルスクリーン表示
- `RECEIVE_BOOT_COMPLETED`: 端末再起動後の再スケジュール
- `INTERNET`: 天気/マスタデータの取得

## テスト

- 単体テスト: `./gradlew testDebugUnitTest`
- 計測テスト: `./gradlew connectedAndroidTest`

## ライセンス

本リポジトリの LICENSE を参照してください。
