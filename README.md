# 祝天アラーム（ShukutenAlarm）

祝日と天気を考慮する、Android向けの高機能アラームアプリです。

日本の祝日を自動で判定し、休みの日にうっかりアラームが鳴ってしまうのを防ぎます。また、アラーム鳴動時にはその日の天気予報も表示し、快適な目覚めをサポートします。

家族の要望を受けて、Codex CLIの性能確認目的も兼ねつつVive Codingで作成しています。Playストア公開予定はありません。

## ✨ 主な機能

*   **祝日対応:** 内閣府が提供する祝日情報APIから最新の祝日を取得し、アラームを自動でスキップします。
*   **天気予報:** Open-Meteo APIを利用し、指定した地域の天気予報をアラーム画面に表示します。
*   **柔軟なアラーム設定:** 複数のアラームを登録し、曜日ごとの繰り返し設定が可能です。
*   **サウンド選択:** デバイス内のサウンドを選択してアラーム音として設定できます。
*   **自動再設定:** 端末を再起動しても、設定済みのアラームは自動で再スケジュールされます。

## 🛠️ 使用技術

このアプリは、モダンなAndroid開発のベストプラクティスに沿って構築されています。

*   **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose)
*   **Architecture:** MVVM (Model-View-ViewModel)
*   **Dependency Injection:** [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
*   **Asynchronous:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html), [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
*   **Data Persistence:** [Jetpack DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
*   **Navigation:** [Navigation-Compose](https://developer.android.com/jetpack/compose/navigation)
*   **Networking:** [Retrofit2](https://square.github.io/retrofit/), [OkHttp3](https://square.github.io/okhttp/), [Kotlinx.Serialization](https://github.com/Kotlin/kotlinx.serialization)
*   **Media:** [Media3](https://developer.android.com/guide/topics/media/media3)

## 🚀 ビルド方法

1.  このリポジトリをクローンします。
    ```bash
    git clone https://github.com/WhiteNoise0000/ShukutenAlarm.git
    ```
2.  [Android Studio](https://developer.android.com/studio) (Hedgehog以降) でプロジェクトを開きます。
3.  Gradleの同期が完了したら、ビルドして実行します。
