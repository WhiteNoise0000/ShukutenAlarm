package io.github.whitenoise0000.shukutenalarm.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory

/**
 * アラーム設定のバックアップ/リストアに関するロジックを担うリポジトリ。
 * 主にインポート時のサウンドURIの有効性チェックと置換処理を行う。
 */
class BackupRepository {

    /**
     * 指定されたURIが現在の端末で有効なアラーム音として存在するかをチェックする。
     * @param context Context
     * @param uriString チェック対象のURI文字列
     * @return 有効な場合はtrue、無効な場合はfalse
     */
    fun isValidSoundUri(context: Context, uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            // RingtoneManager.getRingtone() は常にRingtoneオブジェクトを返すため、
            // 実際に鳴らせるか（タイトルが取得できるか）で実在性を判断する。
            // ただし、デフォルトサウンド（null）は常に有効とみなす。
            if (uriString.isBlank() || uriString == "null") {
                true
            } else {
                // タイトルが取得できれば実在するとみなす
                ringtone?.getTitle(context)?.isNotBlank() == true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * インポートされたアラーム設定から、現在の端末に存在しないサウンドURIを重複なく収集する。
     * @param context Context
     * @param alarms インポートされたアラーム設定のリスト
     * @return 存在しないサウンドURIのセット
     */
    fun findMissingSounds(context: Context, alarms: List<AlarmSpec>): Set<String> {
        val missingSounds = mutableSetOf<String>()

        for (alarm in alarms) {
            // デフォルトサウンド（空またはnull）はチェック対象外
            if (alarm.defaultSoundUri.isNotBlank() && !isValidSoundUri(context, alarm.defaultSoundUri)) {
                missingSounds.add(alarm.defaultSoundUri)
            }

            // 天気別サウンドをチェック
            for ((_, uri) in alarm.soundMapping) {
                if (uri.isNotBlank() && !isValidSoundUri(context, uri)) {
                    missingSounds.add(uri)
                }
            }
        }

        return missingSounds
    }

    /**
     * アラーム設定リスト内のサウンドURIを、指定された対応表に基づいて一括置換する。
     * @param alarms 置換対象のアラーム設定リスト
     * @param replacementMap 置換前URI（キー）と置換後URI（値）の対応表
     * @return 置換後の新しいアラーム設定リスト
     */
    fun replaceSounds(alarms: List<AlarmSpec>, replacementMap: Map<String, String>): List<AlarmSpec> {
        if (replacementMap.isEmpty()) return alarms

        return alarms.map { alarm ->
            var newAlarm = alarm

            // デフォルトサウンドの置換
            val newDefaultUri = replacementMap[alarm.defaultSoundUri]
            if (newDefaultUri != null) {
                newAlarm = newAlarm.copy(defaultSoundUri = newDefaultUri)
            }

            // 天気別サウンドの置換
            val newSoundMapping = newAlarm.soundMapping.mapValues { (_, uri) ->
                replacementMap[uri] ?: uri
            }

            if (newSoundMapping != newAlarm.soundMapping) {
                newAlarm = newAlarm.copy(soundMapping = newSoundMapping)
            }

            newAlarm
        }
    }
}
