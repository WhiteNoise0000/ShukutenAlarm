package io.github.whitenoise0000.shukutenalarm.data

import android.net.Uri
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.HolidayPolicy
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime

object AlarmSpecJson {
    fun encode(spec: AlarmSpec): String {
        val obj = JSONObject()
            .put("id", spec.id)
            .put("name", spec.name)
            .put("time", spec.time.toString())
            .put("days", JSONArray(spec.daysOfWeek.map { it.value }))
            .put("holidayPolicy", spec.holidayPolicy.name)
            .put("volumeMode", spec.volumeMode.name)
            .put("volumePercent", spec.volumePercent)
            .put("vibrate", spec.vibrate)
            .put("respectSilentMode", spec.respectSilentMode)
            .put("holidayOnly", spec.holidayOnly)
            .put("prefetchMinutes", spec.prefetchMinutes)
            .put("enabled", spec.enabled)

        val mapping = JSONObject()
        spec.soundMapping.forEach { (k, v) -> mapping.put(k.name, v.toString()) }
        obj.put("soundMapping", mapping)
        obj.put("holidaySound", spec.holidaySound?.toString())
        return obj.toString()
    }

    fun decode(json: String): AlarmSpec {
        val obj = JSONObject(json)
        val id = obj.getInt("id")
        val name = obj.optString("name", "")
        val time = LocalTime.parse(obj.getString("time"))
        val daysArr = obj.getJSONArray("days")
        val days = mutableSetOf<DayOfWeek>()
        for (i in 0 until daysArr.length()) {
            days.add(DayOfWeek.of(daysArr.getInt(i)))
        }
        val holidayPolicy = HolidayPolicy.valueOf(obj.getString("holidayPolicy"))
        val volumeMode = runCatching { io.github.whitenoise0000.shukutenalarm.data.model.VolumeMode.valueOf(obj.optString("volumeMode","SYSTEM")) }.getOrDefault(io.github.whitenoise0000.shukutenalarm.data.model.VolumeMode.SYSTEM)
        val volumePercent = obj.optInt("volumePercent", 100)
        val vibrate = obj.optBoolean("vibrate", false)
        val respectSilentMode = obj.optBoolean("respectSilentMode", true)
        val holidayOnly = obj.optBoolean("holidayOnly", false)
        val prefetchMinutes = obj.optInt("prefetchMinutes", 45)
        val enabled = obj.optBoolean("enabled", true)
        val mappingObj = obj.optJSONObject("soundMapping") ?: JSONObject()
        val mapping = HashMap<WeatherCategory, Uri>()
        val names = mappingObj.keys()
        while (names.hasNext()) {
            val name = names.next()
            val uri = if (mappingObj.isNull(name)) null else mappingObj.optString(name)
            if (uri != null) mapping[WeatherCategory.valueOf(name)] = Uri.parse(uri)
        }
        val holidaySoundStr = if (obj.isNull("holidaySound")) null else obj.optString("holidaySound")
        val holidaySound = holidaySoundStr?.let { Uri.parse(it) }

        return AlarmSpec(
            id = id,
            name = name,
            time = time,
            daysOfWeek = days,
            holidayPolicy = holidayPolicy,
            volumeMode = volumeMode,
            volumePercent = volumePercent,
            vibrate = vibrate,
            respectSilentMode = respectSilentMode,
            prefetchMinutes = prefetchMinutes,
            holidayOnly = holidayOnly,
            soundMapping = mapping,
            holidaySound = holidaySound,
            enabled = enabled
        )
    }
}
