package io.github.whitenoise0000.shukutenalarm.ui

/**
 * 天気文言の表示用正規化ユーティリティ。
 * - 仕様: 気象庁の weathers 文言に含まれる「全角スペース（U+3000）」を除去してから表示する。
 * - 半角スペースは保持する（JMA由来の句読・接続で半角が含まれても壊さない）。
 * - 前後の空白（改行やタブ等）は取り除く。
 */
fun normalizeWeatherTextForDisplay(raw: String): String {
    // 全角スペースを除去し、前後の空白はトリム
    return raw
        .replace("\u3000", "") // 全角スペース除去
        .trim()
}
