package io.github.whitenoise0000.shukutenalarm.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.longPreferencesKey
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.whitenoise0000.shukutenalarm.R
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import io.github.whitenoise0000.shukutenalarm.holiday.HolidayRepository
import io.github.whitenoise0000.shukutenalarm.settings.SettingsRepository
import io.github.whitenoise0000.shukutenalarm.settings.UserSettings
import io.github.whitenoise0000.shukutenalarm.weather.GeoPlace
import io.github.whitenoise0000.shukutenalarm.weather.GeocodingRepository
import io.github.whitenoise0000.shukutenalarm.weather.OpenMeteoApi
import io.github.whitenoise0000.shukutenalarm.weather.WeatherRepository
import io.github.whitenoise0000.shukutenalarm.widget.NextAlarmWidgetProvider
import io.github.whitenoise0000.shukutenalarm.work.HolidaysRefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 設定画面。
 * - 都市名/現在地が「天気予報取得のため」に使われることを先頭で明示。
 * - 取得元はセグメント切替で直感的に選択。
 * - 祝日のDELAY分、祝日データ自動更新もカードで整理。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(onSaved: () -> Unit, registerSave: ((() -> Unit)?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val geocoding = remember { GeocodingRepository(context) }

    val lat = remember { mutableStateOf("35.0") }
    val lon = remember { mutableStateOf("135.0") }
    val useCurrent = remember { mutableStateOf(false) }
    val saving = remember { mutableStateOf(false) }
    val delayMinutes = remember { mutableStateOf("60") }
    val cityQuery = remember { mutableStateOf("") }
    val cityResults = remember { mutableStateListOf<GeoPlace>() }
    val selectedCityName = remember { mutableStateOf("") }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val searching = remember { mutableStateOf(false) }
    val holidayMonthly = remember { mutableStateOf(false) }
    val holidayWifiOnly = remember { mutableStateOf(true) }
    val holidayLastUpdatedText = remember { mutableStateOf<String?>(null) }
    val holidayRefreshing = remember { mutableStateOf(false) }
    val holidayRefreshMessage = remember { mutableStateOf<String?>(null) }
    // 天気の即時取得（テスト）用の状態
    val fetchingWeather = remember { mutableStateOf(false) }
    val weatherTestMessage = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val s = withContext(Dispatchers.IO) { repo.settingsFlow.first() }
        lat.value = s.latitude.toString()
        lon.value = s.longitude.toString()
        useCurrent.value = s.useCurrentLocation
        selectedCityName.value = s.cityName ?: ""
        delayMinutes.value = s.delayMinutes.toString()
        holidayMonthly.value = s.holidayRefreshMonthly
        holidayWifiOnly.value = s.holidayRefreshWifiOnly

        // 最終更新の読み込み
        val key =
            longPreferencesKey(io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys.KEY_HOLIDAYS_LAST_FETCH)
        val last =
            withContext(Dispatchers.IO) { context.appDataStore.data.map { it[key] ?: 0L }.first() }
        holidayLastUpdatedText.value = if (last > 0L) {
            val dt = java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
            context.getString(
                R.string.label_holiday_last_updated,
                dt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
            )
        } else context.getString(R.string.label_holiday_last_updated_never)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                scope.launch {
                    val loc = withContext(Dispatchers.IO) { getLastKnownCoarse(context) }
                    loc?.let {
                        lat.value = it.latitude.toString(); lon.value = it.longitude.toString()
                    }
                    useCurrent.value = true
                }
            }
        }

    LaunchedEffect(registerSave) {
        registerSave {
            saving.value = true
            val latD = lat.value.toDoubleOrNull() ?: 35.0
            val lonD = lon.value.toDoubleOrNull() ?: 135.0
            val delay = delayMinutes.value.toIntOrNull()?.coerceIn(0, 180) ?: 60
            scope.launch {
                withContext(Dispatchers.IO) {
                    repo.save(
                        UserSettings(
                            latD, lonD, useCurrent.value, delay,
                            holidayRefreshMonthly = holidayMonthly.value,
                            holidayRefreshWifiOnly = holidayWifiOnly.value,
                            cityName = if (useCurrent.value) null else selectedCityName.value.takeIf { it.isNotBlank() }
                        )
                    )
                }
                if (holidayMonthly.value) {
                    HolidaysRefreshScheduler.schedule(context, wifiOnly = holidayWifiOnly.value)
                } else {
                    HolidaysRefreshScheduler.cancel(context)
                }
                saving.value = false
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_settings_saved),
                    Toast.LENGTH_SHORT
                ).show()
                context.sendBroadcast(
                    Intent(
                        context,
                        NextAlarmWidgetProvider::class.java
                    ).setAction(NextAlarmWidgetProvider.ACTION_REFRESH)
                )
                onSaved()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 天気の取得元
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RowAlignCenter {
                    Icon(
                        Icons.Outlined.WbSunny,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_weather_header),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = stringResource(R.string.settings_weather_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val items = listOf(
                        stringResource(R.string.settings_source_city),
                        stringResource(R.string.settings_source_current)
                    )
                    items.forEachIndexed { index, label ->
                        val selected = (index == if (useCurrent.value) 1 else 0)
                        SegmentedButton(
                            modifier = Modifier.weight(1f),
                            selected = selected,
                            onClick = {
                                useCurrent.value = index == 1
                                if (useCurrent.value) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        scope.launch {
                                            val loc =
                                                withContext(Dispatchers.IO) { getLastKnownCoarse(context) }
                                            loc?.let {
                                                lat.value = it.latitude.toString(); lon.value =
                                                it.longitude.toString()
                                            }
                                        }
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = items.size
                            )
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                if (useCurrent.value) {
                    // 現在地は「アラーム直前の天気先読み」で取得します
                    Text(
                        text = stringResource(R.string.settings_weather_timing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 検索実行処理（ボタン/IME共通）
                fun performCitySearch() {
                    searching.value = true; errorMessage.value = null; cityResults.clear()
                    val query = cityQuery.value.trim()
                    scope.launch {
                        try {
                            if (query.isBlank()) {
                                errorMessage.value = context.getString(R.string.error_empty_query)
                            } else {
                                val results =
                                    withContext(Dispatchers.IO) { geocoding.searchCity(query) }
                                if (results.isEmpty()) errorMessage.value =
                                    context.getString(R.string.error_no_results) else cityResults.addAll(
                                    results
                                )
                            }
                        } catch (_: Exception) {
                            errorMessage.value = context.getString(R.string.error_network_generic)
                        } finally {
                            searching.value = false
                        }
                    }
                }

                if (!useCurrent.value) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = cityQuery.value,
                            onValueChange = { cityQuery.value = it },
                            label = { Text(stringResource(R.string.hint_city_search)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            maxLines = 1,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { performCitySearch() })
                        )
                        Button(
                            enabled = !searching.value && cityQuery.value.isNotBlank(),
                            onClick = { performCitySearch() }
                        ) {
                            Text(
                                if (searching.value) stringResource(R.string.action_searching) else stringResource(
                                    R.string.action_search
                                )
                            )
                        }
                    }
                    errorMessage.value?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (selectedCityName.value.isNotBlank()) {
                        Text(
                            text = stringResource(
                                R.string.label_selected_city,
                                selectedCityName.value
                            ), color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (cityResults.isNotEmpty()) {
                        LazyColumn(modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)) {
                            items(
                                cityResults,
                                key = {
                                    it.id ?: (it.name + it.latitude + it.longitude).hashCode()
                                }) { place ->
                                RowAlignCenter {
                                    val subtitle = listOfNotNull(
                                        place.admin1,
                                        place.country
                                    ).joinToString(" / ")
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(place.name)
                                        if (subtitle.isNotBlank()) Text(
                                            subtitle,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Button(onClick = {
                                        lat.value = place.latitude.toString(); lon.value =
                                        place.longitude.toString(); selectedCityName.value =
                                        place.name; cityResults.clear()
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_city_selected),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }) { Text(stringResource(R.string.action_select)) }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_weather_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 取得テストボタン：現在の選択（都市 or 現在地）に基づく座標で天気取得を即時実行し、キャッシュを温める
                RowAlignCenter {
                    Button(
                        enabled = !fetchingWeather.value,
                        onClick = {
                            fetchingWeather.value = true
                            weatherTestMessage.value = null
                            scope.launch {
                                try {
                                    val useCur = useCurrent.value
                                    val latD = lat.value.toDoubleOrNull() ?: 35.0
                                    val lonD = lon.value.toDoubleOrNull() ?: 135.0
                                    val (latUsed, lonUsed) = if (useCur) {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            val loc = withContext(Dispatchers.IO) {
                                                getLastKnownCoarse(context)
                                            }
                                            (loc?.latitude ?: latD) to (loc?.longitude ?: lonD)
                                        } else latD to lonD
                                    } else latD to lonD

                                    val json =
                                        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                    val client = okhttp3.OkHttpClient.Builder()
                                        .addInterceptor(
                                            okhttp3.logging.HttpLoggingInterceptor().apply {
                                                level =
                                                    okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                                            })
                                        .build()
                                    // Retrofit のコンバータは他箇所と同様に Json 拡張の asConverterFactory を用いる
                                    val contentType = "application/json".toMediaType()
                                    val retrofit = retrofit2.Retrofit.Builder()
                                        .baseUrl("https://api.open-meteo.com/")
                                        .client(client)
                                        .addConverterFactory(json.asConverterFactory(contentType))
                                        .build()
                                    val api = retrofit.create(OpenMeteoApi::class.java)
                                    val repo = WeatherRepository(context, api)
                                    val cat = withContext(Dispatchers.IO) {
                                        repo.prefetchToday(
                                            latUsed,
                                            lonUsed
                                        )
                                    }
                                    if (cat != null) {
                                        val label = cat.getLabel(context)
                                        val msg =
                                            context.getString(R.string.toast_weather_fetched, label)
                                        weatherTestMessage.value = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    } else {
                                        val msg =
                                            context.getString(R.string.error_weather_fetch_failed)
                                        weatherTestMessage.value = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {
                                    val msg = context.getString(R.string.error_weather_fetch_failed)
                                    weatherTestMessage.value = msg
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } finally {
                                    fetchingWeather.value = false
                                }
                            }
                        }
                    ) {
                        Text(
                            if (fetchingWeather.value) stringResource(R.string.action_fetching_weather) else stringResource(
                                R.string.action_fetch_weather_now
                            )
                        )
                    }
                }
                weatherTestMessage.value?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 緯度経度のプレビューは非表示（UI簡素化）
            }
        }

        // 祝日のDELAY分（スライダー＋クイックプリセット）
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 現在値の表示（分）
                val delayValue = delayMinutes.value.toIntOrNull()?.coerceIn(0, 180) ?: 60
                RowAlignCenter {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.label_delay_minutes_holiday) + "（現在: ${delayValue}分）",
                        modifier = Modifier.weight(1f)
                    )
                }
                // 5分刻みで調整できるスライダー（0..180）
                Slider(
                    value = delayValue.toFloat(),
                    onValueChange = { v ->
                        val rounded = (v / 5f).roundToInt() * 5
                        delayMinutes.value = rounded.coerceIn(0, 180).toString()
                    },
                    valueRange = 0f..180f,
                    steps = 35,
                    modifier = Modifier.fillMaxWidth()
                )
                // クイックプリセット
                val presets = listOf(0, 30, 60, 90, 120)
                RowAlignCenter {
                    presets.forEach { m ->
                        FilterChip(
                            selected = delayValue == m,
                            onClick = { delayMinutes.value = m.toString() },
                            label = { Text("${m}分") },
                            modifier = Modifier
                        )
                    }
                }
                // 効果の説明（現在値を反映）
                Text(
                    text = stringResource(R.string.hint_policy_delay_format, delayValue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 祝日データ 自動更新
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.title_holiday_refresh),
                    style = MaterialTheme.typography.titleMedium
                )
                RowAlignCenter {
                    Text(
                        stringResource(R.string.label_holiday_refresh_monthly),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = holidayMonthly.value,
                        onCheckedChange = { holidayMonthly.value = it })
                }
                if (holidayMonthly.value) {
                    RowAlignCenter {
                        Text(
                            stringResource(R.string.label_wifi_only),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = holidayWifiOnly.value,
                            onCheckedChange = { holidayWifiOnly.value = it })
                    }
                    Text(
                        text = stringResource(R.string.hint_holiday_refresh),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                holidayLastUpdatedText.value?.let { txt ->
                    Text(
                        text = txt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RowAlignCenter {
                    Button(
                        enabled = !holidayRefreshing.value,
                        onClick = {
                            holidayRefreshing.value = true
                            holidayRefreshMessage.value = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { HolidayRepository(context).forceRefresh() }
                                    val key =
                                        longPreferencesKey(io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys.KEY_HOLIDAYS_LAST_FETCH)
                                    val last = withContext(Dispatchers.IO) {
                                        context.appDataStore.data.map {
                                            it[key] ?: 0L
                                        }.first()
                                    }
                                    if (last > 0L) {
                                        val dt = java.time.Instant.ofEpochMilli(last)
                                            .atZone(java.time.ZoneId.systemDefault())
                                            .toLocalDateTime()
                                        holidayLastUpdatedText.value = context.getString(
                                            R.string.label_holiday_last_updated,
                                            dt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
                                        )
                                    }
                                    holidayRefreshMessage.value =
                                        context.getString(R.string.text_refreshed)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_holidays_refreshed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (_: Exception) {
                                    holidayRefreshMessage.value =
                                        context.getString(R.string.error_refresh_failed)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.error_refresh_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    holidayRefreshing.value = false
                                }
                            }
                        }
                    ) {
                        Text(
                            if (holidayRefreshing.value) stringResource(R.string.action_refreshing) else stringResource(
                                R.string.action_refresh_now
                            )
                        )
                    }
                }
                holidayRefreshMessage.value?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

    }
}

private fun getLastKnownCoarse(context: Context): Location? {
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
        LocationManager.GPS_PROVIDER
    )
    for (p in providers) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let { return it }
        }
    }
    return null
}

@Composable
private fun RowAlignCenter(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}
