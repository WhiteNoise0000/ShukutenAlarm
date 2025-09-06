package io.github.whitenoise0000.shukutenalarm.data

import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec

/**
 * アラーム設定の永続化用リポジトリIF。
 * 実装は DataStore/Room 等を想定。
 */
interface AlarmRepository {
    /** 設定を保存（同一IDは上書き） */
    suspend fun save(spec: AlarmSpec)
    /** ID指定で読み出し（存在しない場合は null） */
    suspend fun load(id: Int): AlarmSpec?
    /** 設定を削除 */
    suspend fun delete(id: Int)
    /** 全件列挙（ID昇順など自然順を推奨） */
    suspend fun list(): List<AlarmSpec>
}
