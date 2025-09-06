# Repository Guidelines

## プロジェクト構成
- ルート: Gradle Kotlin DSL（`build.gradle.kts`、`settings.gradle.kts`、`gradle/`）。
- アプリモジュール: `app/`。
  - ソース: `app/src/main/java|kotlin/io/github/whitenoise0000/shukutenalarm/`。
  - リソース: `app/src/main/res/`（drawable/mipmap/colors/strings/themes など）。
  - マニフェスト: `app/src/main/AndroidManifest.xml`。
  - テスト: ユニット `app/src/test/`、計測 `app/src/androidTest/`。

## ビルド・実行・テスト
- デバッグAPK: `./gradlew assembleDebug`（Windows: `gradlew.bat assembleDebug`）。
- ユニットテスト: `./gradlew testDebugUnitTest`。
- 計測テスト（端末/エミュレータ）: `./gradlew connectedAndroidTest`。
- Lint: `./gradlew lint`。
- 端末へインストール: `./gradlew installDebug`。
Tip: 日常開発は Windows 上の Android Studio を使用し、CI 互換は Gradle で確認。ターミナルは PowerShell を利用。

## コーディング規約・命名
- 言語: Kotlin。インデント4スペース。公開APIにはKDoc。
- 命名: クラス/オブジェクト=PascalCase、関数/変数=camelCase、定数=UPPER_SNAKE_CASE。
- パッケージ: `io.github.whitenoise0000.shukutenalarm` に整合させディレクトリを配置。
- リソース命名: スネークケース。レイアウト=`activity_*`/`fragment_*`、drawable=`ic_*`/`bg_*`。
- 文字列/色はリソース経由。マジックナンバーとハードコード回避。
- 生成するすべてのソースコードに対し、適切な日本語コメントを付与すること。
- 修正前のコードやコメント等で、不要なものは随時削除すること。デッドロジックを残さない。
- ファイル入出力はUTF-8(BOMなし)で行うこと。

## テスト指針
- フレームワーク: JUnit（`app/src/test`）、AndroidX Test/Espresso（`app/src/androidTest`）。
- テスト命名: `<ClassName>Test.kt`。メソッドは振る舞いを記述（例: `addsHoliday_onValidInput`）。
- 重点: 日付/時刻計算、アラーム動作、設定の永続化。高速かつ疎結合なテストを維持。

## コミット・PR ガイド
- コミット: 命令形・現在形（例: "Add alarm snooze"）。粒度を小さく、`#<issue>` を参照。
- PR: 目的/背景/変更点を明記。関連Issueをリンク。UI変更はスクリーンショット/録画を添付。テスト観点と動作確認端末/APIレベルを記載。
- CI: ビルド/テスト/Lint 合格後にレビュー依頼。

## セキュリティと設定
- 秘密情報や `local.properties` をコミットしない。ビルド時設定は `gradle.properties` と `BuildConfig` を活用。
- 難読化/最適化は `app/proguard-rules.pro` を管理し、公開ビルドで検証。

## その他
- 思考過程の表示およびプロンプト回答は日本語で行うこと。
