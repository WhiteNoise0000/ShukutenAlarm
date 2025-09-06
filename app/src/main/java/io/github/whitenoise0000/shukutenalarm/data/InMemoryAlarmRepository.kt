package io.github.whitenoise0000.shukutenalarm.data

import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import java.util.concurrent.ConcurrentHashMap

/**
 * メモリ内実装（テスト/試験用）。アプリ終了で内容は消える。
 */
class InMemoryAlarmRepository : AlarmRepository {
    private val store = ConcurrentHashMap<Int, AlarmSpec>()

    override suspend fun save(spec: AlarmSpec) {
        store[spec.id] = spec
    }

    override suspend fun load(id: Int): AlarmSpec? = store[id]

    override suspend fun delete(id: Int) {
        store.remove(id)
    }

    override suspend fun list(): List<AlarmSpec> = store.values.toList()
}
