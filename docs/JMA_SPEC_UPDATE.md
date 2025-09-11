# JMA/GSI 連携・天気取得 仕様更新（ShukutenAlarm）

最終更新: 2025-09-11 / Asia/Tokyo

この文書は、Open‑Meteo から気象庁JMA予報＋GSI逆ジオコーディングへ切替えた内容の要約です。既存の `docs/SPEC.md`/`README.md` の補足として参照してください。

## 要点

- 現在地ON時のみ GSI 逆ジオ（LonLatToAddress）で muniCd（5桁）を取得。
- class20 候補 = `muniCd + "00"`。候補が area.json の `class20s` に存在しない場合、政令指定都市の区と判断し市単位へ丸め（例: `14103` → `1410000`）。
- `class20 → class10 → office` は `area.json`（`offices`/`class10s`/`class20s`）の親子から決定。
- 都市名検索は `area.json` の `offices.name` と `class20s.name` に対するローカル部分一致。
- 予報データは `https://www.jma.go.jp/bosai/forecast/data/forecast/{office}.json`。
- マスタデータは `https://www.jma.go.jp/bosai/common/const/area.json`。
- HTTP 取得に ETag/If-Modified-Since を付与し、`area.json`・`forecast/{office}.json` 双方で 304 Not Modified 時はローカルキャッシュから本文を復元。

## 代表例

- 渋谷区座標: muniCd=13113 → class20=1311300 → parent=130011 → class10=130010 → office=130000 → `forecast/130000.json`（code=130010）を参照。
- 横浜市の区: muniCd の `+"00"` が未存在 → 14103 → 丸め 1410000 → 上記と同様に parent を辿って予報取得。

## 実装（モジュール）

- API: `GsiApi`（逆ジオ）, `JmaConstApi`（area.json）, `JmaForecastApi`（forecast）
- Repository: `AreaRepository`（取得・メモリキャッシュ・親子解決・検索）, `ForecastRepository`（カテゴリ正規化＋保存）, `WeatherRepository`（上位調停）
- マッパー: `JmaMapper`（muniCd→class20→class10→office, 政令市丸め）
- キャッシュ: `EtagCacheInterceptor`＋`CacheStore`（ETag/If-Modified-Since, 304→本文復元）

## UI/設定

- 設定に `selectedOffice` と `selectedClass10` を追加。都市名検索で選択した値を保存。
- 設定「今すぐ取得」: 現在地ONなら GSI 経由、OFFなら `office/class10` で取得。

## マッピング規則（正規化）

- `weathers`（日本語）優先、なければ `weatherCodes`（数値文字列）
- テキスト: 雪 > 雨/雷 > 曇 > 晴
- コード（先頭桁）: 1=晴, 2=曇, 3=雨, 4=雪, 5=雨（雷雨）

## キャッシュ仕様

- 保存要素: URL, ETag, Last-Modified, 本文, 最終取得時刻
- 対象URL: `area.json`, `forecast/{office}.json`
- 304応答時: 200+本文差し替え（`X-Cache-Hit` をヘッダに付与）

以上
