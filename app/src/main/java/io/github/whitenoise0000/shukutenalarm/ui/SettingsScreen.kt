package io.github.whitenoise0000.shukutenalarm.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import io.github.whitenoise0000.shukutenalarm.data.BackupRepository
import io.github.whitenoise0000.shukutenalarm.data.DataStoreAlarmRepository
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.holiday.HolidayRepository
import io.github.whitenoise0000.shukutenalarm.network.EtagCacheInterceptor
import io.github.whitenoise0000.shukutenalarm.settings.SettingsRepository
import io.github.whitenoise0000.shukutenalarm.settings.UserSettings
import io.github.whitenoise0000.shukutenalarm.weather.WeatherRepository
import io.github.whitenoise0000.shukutenalarm.weather.jma.AreaRepository
import io.github.whitenoise0000.shukutenalarm.weather.jma.GsiApi
import io.github.whitenoise0000.shukutenalarm.weather.jma.JmaConstApi
import io.github.whitenoise0000.shukutenalarm.weather.jma.JmaForecastApi
import io.github.whitenoise0000.shukutenalarm.weather.jma.TelopsRepository
import io.github.whitenoise0000.shukutenalarm.widget.NextAlarmWidgetProvider
import io.github.whitenoise0000.shukutenalarm.work.MasterRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    val alarmRepo = remember { DataStoreAlarmRepository(context) }
    val backupRepo = remember { BackupRepository() } // BackupRepositoryのインスタンス化
    // 都市名検索は area.json ローカル検索へ切替えるためGeocoding依存は廃止

    val lat = remember { mutableStateOf("35.0") }
    val lon = remember { mutableStateOf("135.0") }
    val useCurrent = remember { mutableStateOf(false) }
    val saving = remember { mutableStateOf(false) }
    val delayMinutes = remember { mutableStateOf("60") }
    val cityQuery = remember { mutableStateOf("") }
    data class CityItem(val name: String, val office: String, val class10: String?)
    val cityResults = remember { mutableStateListOf<CityItem>() }
    val selectedCityName = remember { mutableStateOf("") }
    val selectedOffice = remember { mutableStateOf<String?>(null) }
    val selectedClass10 = remember { mutableStateOf<String?>(null) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val searching = remember { mutableStateOf(false) }
    val holidayMonthly = remember { mutableStateOf(false) }
    val holidayWifiOnly = remember { mutableStateOf(true) }
    val masterIntervalDays = remember { mutableStateOf(30) }
    val holidayLastUpdatedText = remember { mutableStateOf<String?>(null) }
    val masterLastUpdatedText = remember { mutableStateOf<String?>(null) }
    val holidayRefreshing = remember { mutableStateOf(false) }
    val holidayRefreshMessage = remember { mutableStateOf<String?>(null) }
    // 天気の即時取得（テスト）用の状態
    val fetchingWeather = remember { mutableStateOf(false) }
    val weatherTestMessage = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // サウンド再選択フローの状態
    val missingSounds = remember { mutableStateListOf<String>() } // 無効なURIのリスト
    val soundReplacementMap = remember { mutableMapOf<String, String>() } // 置換マップ (旧URI -> 新URI)
    val alarmsToImport = remember { mutableStateOf<List<AlarmSpec>?>(null) } // インポート待ちのアラームリスト

    // サウンドピッカー用のランチャー
    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            val newUri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val oldUri = missingSounds.firstOrNull() // 現在処理中の無効なURI

            if (oldUri != null && newUri != null) {
                // 選択された新しいURIを、現在処理中の無効なURIの置換値としてマップに登録
                soundReplacementMap[oldUri] = newUri.toString()
            } else if (oldUri != null) {
                // ユーザーがキャンセルした場合、デフォルトサウンド（空文字列）で置換
                soundReplacementMap[oldUri] = ""
            }

            // 処理済みのURIをリストから削除し、次の無効なURIの処理へ進む
            if (oldUri != null) {
                missingSounds.removeAt(0)
            }
        }
    )

    // ファイル書き出し（エクスポート）用のランチャー
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val alarms = alarmRepo.list()
                        val jsonString = Json.encodeToString(alarms)
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(it)?.use { stream ->
                                stream.write(jsonString.toByteArray())
                            }
                        }
                        Toast.makeText(context, R.string.toast_export_success, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, R.string.toast_export_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    // ファイル読み込み（インポート）用のランチャー
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val jsonString = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(it)?.use { stream ->
                                stream.bufferedReader().readText()
                            }
                        }
                        if (jsonString != null) {
                            val alarms = Json.decodeFromString<List<AlarmSpec>>(jsonString)
                            val missing = backupRepo.findMissingSounds(context, alarms)

                            if (missing.isNotEmpty()) {
                                // 無効なサウンドがある場合、再選択フローを開始
                                alarmsToImport.value = alarms
                                missingSounds.clear()
                                missingSounds.addAll(missing)
                                soundReplacementMap.clear()
                            } else {
                                // 無効なサウンドがない場合、即座にインポート
                                performImport(context, alarmRepo, alarms, onSaved)
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, R.string.toast_import_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    // 再選択フローの実行
    LaunchedEffect(missingSounds.firstOrNull()) {
        val oldUri = missingSounds.firstOrNull()
        if (oldUri != null) {
            // サウンドピッカーを起動
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.label_replace_sound_title, oldUri))
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            }
            soundPickerLauncher.launch(intent)
        } else if (alarmsToImport.value != null && soundReplacementMap.isNotEmpty()) {
            // すべての無効なサウンドの代替が選択されたら、インポートを完了
            val finalAlarms = backupRepo.replaceSounds(alarmsToImport.value!!, soundReplacementMap)
            performImport(context, alarmRepo, finalAlarms, onSaved)
            alarmsToImport.value = null
        }
    }

    LaunchedEffect(Unit) {
        val s = withContext(Dispatchers.IO) { repo.settingsFlow.first() }
        lat.value = s.latitude.toString()
        lon.value = s.longitude.toString()
        useCurrent.value = s.useCurrentLocation
        selectedCityName.value = s.cityName ?: ""
        selectedOffice.value = s.selectedOffice
        selectedClass10.value = s.selectedClass10
        delayMinutes.value = s.delayMinutes.toString()
        holidayMonthly.value = s.holidayRefreshMonthly
        holidayWifiOnly.value = s.holidayRefreshWifiOnly
        masterIntervalDays.value = s.masterRefreshIntervalDays

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

        // area.json の最終取得
        val areaKey =
            longPreferencesKey(io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys.KEY_AREA_LAST_FETCH)
        val areaLast =
            withContext(Dispatchers.IO) { context.appDataStore.data.map { it[areaKey] ?: 0L }.first() }
        masterLastUpdatedText.value = if (areaLast > 0L) {
            val dt = java.time.Instant.ofEpochMilli(areaLast).atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
            context.getString(
                R.string.label_master_last_updated,
                dt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
            )
        } else null
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
                            cityName = if (useCurrent.value) null else selectedCityName.value.takeIf { it.isNotBlank() },
                            selectedOffice = if (useCurrent.value) null else selectedOffice.value,
                            selectedClass10 = if (useCurrent.value) null else selectedClass10.value
                        )
                    )
                }
                if (holidayMonthly.value) {
                    MasterRefreshScheduler.schedule(context, wifiOnly = holidayWifiOnly.value, intervalDays = masterIntervalDays.value)
                } else {
                    MasterRefreshScheduler.cancel(context)
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
                                val items = withContext(Dispatchers.IO) {
                                    // JMA + ETagキャッシュ付きクライアントを都度生成
                                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                    val client = okhttp3.OkHttpClient.Builder()
                                        .addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply {
                                            level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                                        })
                                        .addInterceptor(EtagCacheInterceptor(context))
                                        .build()
                                    val retrofit = retrofit2.Retrofit.Builder()
                                        .baseUrl("https://www.jma.go.jp/")
                                        .client(client)
                                        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                                        .build()
                                    val constApi = retrofit.create(JmaConstApi::class.java)
                                    val areaRepo = AreaRepository(context, constApi)
                                    areaRepo.searchByName(query)
                                }
                                if (items.isEmpty()) {
                                    errorMessage.value = context.getString(R.string.error_no_results)
                                } else {
                                    val mapped = items.mapNotNull {
                                        when (it) {
                                            is AreaRepository.SearchResult.Office -> CityItem(it.name, it.code, null)
                                            is AreaRepository.SearchResult.Class20 -> CityItem(it.name, it.officeCode ?: return@mapNotNull null, it.class10Code)
                                        }
                                    }
                                    cityResults.addAll(mapped)
                                }
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
                                key = { (it.office + ":" + (it.class10 ?: "")).hashCode() }
                            ) { item ->
                                RowAlignCenter {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name)
                                        val codeLine = listOfNotNull("office=" + item.office, item.class10?.let { "class10=$it" }).joinToString("  ")
                                        Text(codeLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Button(onClick = {
                                        selectedCityName.value = item.name
                                        selectedOffice.value = item.office
                                        selectedClass10.value = item.class10
                                        cityResults.clear()
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
                                        .addInterceptor(EtagCacheInterceptor(context))
                                        .build()
                                    // Retrofit のコンバータは他箇所と同様に Json 拡張の asConverterFactory を用いる
                                    val contentType = "application/json".toMediaType()
                                    val jmaRetrofit = retrofit2.Retrofit.Builder()
                                        .baseUrl("https://www.jma.go.jp/")
                                        .client(client)
                                        .addConverterFactory(json.asConverterFactory(contentType))
                                        .build()
                                    val gsiRetrofit = retrofit2.Retrofit.Builder()
                                        .baseUrl("https://mreversegeocoder.gsi.go.jp/")
                                        .client(client)
                                        .addConverterFactory(json.asConverterFactory(contentType))
                                        .build()
                                    val forecastApi = jmaRetrofit.create(JmaForecastApi::class.java)
                                                                         val gsiApi = gsiRetrofit.create(GsiApi::class.java)
                                    val constApi = jmaRetrofit.create(JmaConstApi::class.java)
                                    val areaRepo = AreaRepository(context, constApi)
                                    val telopsRepo = TelopsRepository(context)
                                    val repo = WeatherRepository(context, forecastApi, gsiApi, areaRepo, telopsRepo)
                                    // 取得結果をスナップショット（カテゴリ＋JMA文言）として受け取り、文言優先で表示する
                                    val snap = withContext(Dispatchers.IO) {
                                        if (useCur) {
                                            repo.prefetchByCurrentLocation(latUsed, lonUsed)
                                        } else {
                                            val office = selectedOffice.value
                                            if (office.isNullOrBlank()) null else repo.prefetchByOffice(office, selectedClass10.value)
                                        }
                                    }
                                    if (snap != null) {
                                        val labelRaw = snap.text?.ifBlank { null }
                                            ?: snap.category?.getLabel(context)
                                            ?: context.getString(R.string.text_unknown)
                                        val label = normalizeWeatherTextForDisplay(labelRaw)
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
                // Issue #12 対応: Pixel 8a 実機で末尾の「120分」チップが見切れてしまう不具合があるため
                // プリセットから 120 を除外し、0/30/60/90 の4つに限定する。
                // スライダーは従来通り 0..180 を維持するため、必要な場合はスライダーから 120 分も選択可能。
                val presets = listOf(0, 30, 60, 90)
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
                    stringResource(R.string.title_master_refresh),
                    style = MaterialTheme.typography.titleMedium
                )
                RowAlignCenter {
                    Text(
                        stringResource(R.string.label_master_auto_refresh),
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
                    // 更新間隔（7/14/30日）
                    Text(
                        text = stringResource(R.string.label_refresh_interval),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val items = listOf(7, 14, 30)
                        items.forEachIndexed { index, days ->
                            val selected = masterIntervalDays.value == days
                            SegmentedButton(
                                modifier = Modifier.weight(1f),
                                selected = selected,
                                onClick = { masterIntervalDays.value = days },
                                shape = SegmentedButtonDefaults.itemShape(index, items.size)
                            ) {
                                Text(
                                    when (days) {
                                        7 -> stringResource(R.string.interval_weekly)
                                        14 -> stringResource(R.string.interval_biweekly)
                                        else -> stringResource(R.string.interval_monthly)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.hint_master_refresh),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                masterLastUpdatedText.value?.let { txt ->
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
                                    // 祝日（YAML）更新
                                    withContext(Dispatchers.IO) { HolidayRepository(context).forceRefresh() }
                                    // area.json（JMA）更新（ETag/IMS対応）
                                    val json =
                                        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                    val client = okhttp3.OkHttpClient.Builder()
                                        .addInterceptor(
                                            okhttp3.logging.HttpLoggingInterceptor().apply {
                                                level =
                                                    okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                                            })
                                        .addInterceptor(EtagCacheInterceptor(context))
                                        .build()
                                    val contentType = "application/json".toMediaType()
                                    val jmaRetrofit = retrofit2.Retrofit.Builder()
                                        .baseUrl("https://www.jma.go.jp/")
                                        .client(client)
                                        .addConverterFactory(json.asConverterFactory(contentType))
                                        .build()
                                    val constApi = jmaRetrofit.create(JmaConstApi::class.java)
                                    val areaRepo = AreaRepository(context, constApi)
                                    withContext(Dispatchers.IO) { areaRepo.refreshMaster() }
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
                                    val areaKey2 = longPreferencesKey(io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys.KEY_AREA_LAST_FETCH)
                                    val areaLast2 = withContext(Dispatchers.IO) {
                                        context.appDataStore.data.map { it[areaKey2] ?: 0L }.first()
                                    }
                                    if (areaLast2 > 0L) {
                                        val dt = java.time.Instant.ofEpochMilli(areaLast2)
                                            .atZone(java.time.ZoneId.systemDefault())
                                            .toLocalDateTime()
                                        masterLastUpdatedText.value = context.getString(
                                            R.string.label_master_last_updated,
                                            dt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
                                        )
                                    }
                                    holidayRefreshMessage.value =
                                        context.getString(R.string.toast_master_refreshed)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_master_refreshed),
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

        // データのバックアップと復元
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RowAlignCenter {
                    Icon(
                        Icons.Outlined.SaveAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.title_backup_restore),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider()
                // Export
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportLauncher.launch("shukuten_alarm_backup.json") }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null)
                    Column {
                        Text(stringResource(R.string.action_export))
                        Text(
                            stringResource(R.string.desc_export),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Import
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { importLauncher.launch("application/json") }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Column {
                        Text(stringResource(R.string.action_import))
                        Text(
                            stringResource(R.string.desc_import),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // サウンド再選択ダイアログ（実際はLaunchedEffectでピッカーを起動するため、ここでは不要）
    // LaunchedEffectでピッカーを起動しているため、ダイアログは不要。

    // インポート処理の共通関数
    if (missingSounds.isNotEmpty()) {
        val currentMissingUri = missingSounds.first()
        val ringtone = remember(currentMissingUri) {
            try {
                RingtoneManager.getRingtone(context, Uri.parse(currentMissingUri))
            } catch (e: Exception) {
                null
            }
        }
        val soundName = ringtone?.getTitle(context) ?: currentMissingUri
        
        AlertDialog(
            onDismissRequest = { /* 外部タップでのキャンセルは不可 */ },
            title = { Text(stringResource(R.string.title_missing_sound)) },
            text = { Text(stringResource(R.string.message_missing_sound, soundName)) },
            confirmButton = {
                TextButton(onClick = {
                    // LaunchedEffectが自動でピッカーを起動するため、ここでは何もしない
                }) {
                    Text(stringResource(R.string.action_select_new_sound))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    // キャンセルされた場合、デフォルトサウンド（空文字列）で置換
                    soundReplacementMap[currentMissingUri] = ""
                    missingSounds.removeAt(0)
                }) {
                    Text(stringResource(R.string.text_cancel))
                }
            }
        )
    }
}

private fun performImport(
    context: Context,
    alarmRepo: DataStoreAlarmRepository,
    alarms: List<AlarmSpec>,
    onSaved: () -> Unit
) {
    val scope = CoroutineScope(Dispatchers.Main)
    scope.launch {
        withContext(Dispatchers.IO) {
            // 既存の全アラームを削除
            alarmRepo.list().forEach { oldAlarm -> alarmRepo.delete(oldAlarm.id) }
            // インポートしたアラームを保存
            alarms.forEach { newAlarm -> alarmRepo.save(newAlarm) }
        }
        // 設定が変更されたことを通知し、アラームを再スケジュール
        onSaved()
        Toast.makeText(context, R.string.toast_import_success, Toast.LENGTH_SHORT).show()
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
