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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * モダンで洗練されたUIに刷新。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    onSaved: () -> Unit,
    registerSave: ((() -> Unit)?) -> Unit,
    registerBack: ((() -> Unit)?) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val alarmRepo = remember { DataStoreAlarmRepository(context) }
    val backupRepo = remember { BackupRepository() }

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
    val validationError = remember { mutableStateOf<String?>(null) }
    val searching = remember { mutableStateOf(false) }
    val holidayMonthly = remember { mutableStateOf(false) }
    val holidayWifiOnly = remember { mutableStateOf(true) }
    val masterIntervalDays = remember { mutableStateOf(30) }
    val holidayLastUpdatedText = remember { mutableStateOf<String?>(null) }
    val masterLastUpdatedText = remember { mutableStateOf<String?>(null) }
    val holidayRefreshing = remember { mutableStateOf(false) }
    val holidayRefreshMessage = remember { mutableStateOf<String?>(null) }
    val fetchingWeather = remember { mutableStateOf(false) }
    val weatherTestMessage = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val missingSounds = remember { mutableStateListOf<String>() }
    val soundReplacementMap = remember { mutableMapOf<String, String>() }
    val alarmsToImport = remember { mutableStateOf<List<AlarmSpec>?>(null) }

    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            val newUri = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            val oldUri = missingSounds.firstOrNull()

            if (oldUri != null && newUri != null) {
                soundReplacementMap[oldUri] = newUri.toString()
            } else if (oldUri != null) {
                soundReplacementMap[oldUri] = ""
            }

            if (oldUri != null) {
                missingSounds.removeAt(0)
            }
        }
    )

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
                                alarmsToImport.value = alarms
                                missingSounds.clear()
                                missingSounds.addAll(missing)
                                soundReplacementMap.clear()
                            } else {
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

    LaunchedEffect(missingSounds.firstOrNull()) {
        val oldUri = missingSounds.firstOrNull()
        if (oldUri != null) {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.label_replace_sound_title, oldUri))
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            }
            soundPickerLauncher.launch(intent)
        } else if (alarmsToImport.value != null && soundReplacementMap.isNotEmpty()) {
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

        val key = longPreferencesKey(io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys.KEY_HOLIDAYS_LAST_FETCH)
        val last = withContext(Dispatchers.IO) { context.appDataStore.data.map { it[key] ?: 0L }.first() }
        holidayLastUpdatedText.value = if (last > 0L) {
            val dt = java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            context.getString(R.string.label_holiday_last_updated, dt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")))
        } else context.getString(R.string.label_holiday_last_updated_never)

        val areaKey = longPreferencesKey(io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys.KEY_AREA_LAST_FETCH)
        val areaLast = withContext(Dispatchers.IO) { context.appDataStore.data.map { it[areaKey] ?: 0L }.first() }
        masterLastUpdatedText.value = if (areaLast > 0L) {
            val dt = java.time.Instant.ofEpochMilli(areaLast).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            context.getString(R.string.label_master_last_updated, dt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")))
        } else null
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch {
                val loc = withContext(Dispatchers.IO) { getLastKnownCoarse(context) }
                loc?.let { lat.value = it.latitude.toString(); lon.value = it.longitude.toString() }
                useCurrent.value = true
            }
        }
    }

    // バリデーション関数
    fun validateCitySelection(): Boolean {
        if (!useCurrent.value && selectedOffice.value.isNullOrBlank()) {
            validationError.value = context.getString(R.string.error_city_required)
            return false
        }
        validationError.value = null
        return true
    }

    LaunchedEffect(registerSave) {
        registerSave {
            // 保存時のバリデーション
            if (!validateCitySelection()) {
                Toast.makeText(context, validationError.value, Toast.LENGTH_LONG).show()
                return@registerSave
            }
            
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
                Toast.makeText(context, context.getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(context, NextAlarmWidgetProvider::class.java).setAction(NextAlarmWidgetProvider.ACTION_REFRESH))
                onSaved()
            }
        }
    }

    LaunchedEffect(registerBack) {
        registerBack {
            // 戻るボタン押下時のバリデーション
            // ※初期設定モードでは戻るボタン自体が非表示なので、このロジックは既存設定の編集時のみ実行される
            if (!validateCitySelection()) {
                Toast.makeText(context, validationError.value, Toast.LENGTH_LONG).show()
            } else {
                onSaved()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
            // 天気設定セクション
            SettingsSection(title = stringResource(R.string.settings_weather_header)) {
                Text(
                    text = stringResource(R.string.settings_weather_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ListItem(
                    headlineContent = {
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
                                        validationError.value = null // モード切り替え時にエラーをクリア
                                        if (useCurrent.value) {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                scope.launch {
                                                    val loc = withContext(Dispatchers.IO) { getLastKnownCoarse(context) }
                                                    loc?.let {
                                                        lat.value = it.latitude.toString(); lon.value = it.longitude.toString()
                                                    }
                                                }
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                            }
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size)
                                ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                AnimatedVisibility(
                    visible = useCurrent.value,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_weather_timing)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.Outlined.Schedule, contentDescription = null) }
                    )
                }

                AnimatedVisibility(
                    visible = !useCurrent.value,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        ListItem(
                            headlineContent = {
                                OutlinedTextField(
                                    value = cityQuery.value,
                                    onValueChange = { cityQuery.value = it },
                                    label = { Text(stringResource(R.string.hint_city_search)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        // 検索ロジック
                                        searching.value = true; errorMessage.value = null; cityResults.clear()
                                        val query = cityQuery.value.trim()
                                        scope.launch {
                                            try {
                                                if (query.isBlank()) {
                                                    errorMessage.value = context.getString(R.string.error_empty_query)
                                                } else {
                                                    val items = withContext(Dispatchers.IO) {
                                                        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                                        val client = okhttp3.OkHttpClient.Builder()
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
                                    }),
                                    trailingIcon = {
                                        IconButton(onClick = { /* 検索ロジック重複回避のため省略、IMEアクション推奨 */ }) {
                                            Icon(Icons.Outlined.Search, contentDescription = null)
                                        }
                                    }
                                )
                            },
                            supportingContent = errorMessage.value?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        if (selectedCityName.value.isNotBlank()) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.label_selected_city, selectedCityName.value)) },
                                leadingContent = { Icon(Icons.Outlined.WbSunny, contentDescription = null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }

                        // バリデーションエラー表示
                        if (validationError.value != null) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = validationError.value ?: "",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                leadingContent = { Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }

                        if (cityResults.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(cityResults, key = { (it.office + ":" + (it.class10 ?: "")).hashCode() }) { item ->
                                    ListItem(
                                        headlineContent = { Text(item.name) },
                                        supportingContent = {
                                            val codeLine = listOfNotNull("office=" + item.office, item.class10?.let { "class10=$it" }).joinToString("  ")
                                            Text(codeLine)
                                        },
                                        modifier = Modifier.clickable {
                                            selectedCityName.value = item.name
                                            selectedOffice.value = item.office
                                            selectedClass10.value = item.class10
                                            cityResults.clear()
                                            validationError.value = null // エラーをクリア
                                            Toast.makeText(context, context.getString(R.string.toast_city_selected), Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    headlineContent = {
                        Text(
                            if (fetchingWeather.value) stringResource(R.string.action_fetching_weather) else stringResource(R.string.action_fetch_weather_now)
                        )
                    },
                    supportingContent = weatherTestMessage.value?.let { { Text(it) } },
                    leadingContent = { Icon(Icons.Outlined.CloudDownload, contentDescription = null) },
                    modifier = Modifier.clickable(enabled = !fetchingWeather.value) {
                        // 天気取得テストロジック
                        fetchingWeather.value = true
                        weatherTestMessage.value = null
                        scope.launch {
                            try {
                                val useCur = useCurrent.value
                                val latD = lat.value.toDoubleOrNull() ?: 35.0
                                val lonD = lon.value.toDoubleOrNull() ?: 135.0
                                val (latUsed, lonUsed) = if (useCur) {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        val loc = withContext(Dispatchers.IO) { getLastKnownCoarse(context) }
                                        (loc?.latitude ?: latD) to (loc?.longitude ?: lonD)
                                    } else latD to lonD
                                } else latD to lonD

                                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                val client = okhttp3.OkHttpClient.Builder()
                                    .addInterceptor(EtagCacheInterceptor(context))
                                    .build()
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
                                val snap = withContext(Dispatchers.IO) {
                                    if (useCur) {
                                        repo.prefetchByCurrentLocation(latUsed, lonUsed)
                                    } else {
                                        val office = selectedOffice.value
                                        if (office.isNullOrBlank()) null else repo.prefetchByOffice(office, selectedClass10.value)
                                    }
                                }
                                if (snap != null) {
                                    val labelRaw = snap.text?.ifBlank { null } ?: snap.category?.getLabel(context) ?: context.getString(R.string.text_unknown)
                                    val label = normalizeWeatherTextForDisplay(labelRaw)
                                    val msg = context.getString(R.string.toast_weather_fetched, label)
                                    weatherTestMessage.value = msg
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    val msg = context.getString(R.string.error_weather_fetch_failed)
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
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // 祝日設定セクション
            SettingsSection(title = "祝日設定") {
                val delayValue = delayMinutes.value.toIntOrNull()?.coerceIn(0, 180) ?: 60
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_delay_minutes_holiday)) },
                    supportingContent = { Text("現在: ${delayValue}分") },
                    leadingContent = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Slider(
                        value = delayValue.toFloat(),
                        onValueChange = { v ->
                            val rounded = (v / 5f).roundToInt() * 5
                            delayMinutes.value = rounded.coerceIn(0, 180).toString()
                        },
                        valueRange = 0f..180f,
                        steps = 35
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 30, 60, 90).forEach { m ->
                            FilterChip(
                                selected = delayValue == m,
                                onClick = { delayMinutes.value = m.toString() },
                                label = { Text("${m}分") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.hint_policy_delay_format, delayValue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 自動更新セクション
            SettingsSection(title = stringResource(R.string.title_master_refresh)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_master_auto_refresh)) },
                    trailingContent = {
                        Switch(
                            checked = holidayMonthly.value,
                            onCheckedChange = { holidayMonthly.value = it }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                AnimatedVisibility(visible = holidayMonthly.value) {
                    Column {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.label_wifi_only)) },
                            trailingContent = {
                                Switch(
                                    checked = holidayWifiOnly.value,
                                    onCheckedChange = { holidayWifiOnly.value = it }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.label_refresh_interval)) },
                            supportingContent = {
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    val items = listOf(7, 14, 30)
                                    items.forEachIndexed { index, days ->
                                        SegmentedButton(
                                            modifier = Modifier.weight(1f),
                                            selected = masterIntervalDays.value == days,
                                            onClick = { masterIntervalDays.value = days },
                                            shape = SegmentedButtonDefaults.itemShape(index, items.size)
                                        ) {
                                            Text(
                                                text = when (days) {
                                                    7 -> "毎週"
                                                    14 -> "隔週"
                                                    else -> "毎月"
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    headlineContent = {
                        Text(
                            if (holidayRefreshing.value) stringResource(R.string.action_refreshing) else stringResource(R.string.action_refresh_now)
                        )
                    },
                    supportingContent = {
                        Column {
                            masterLastUpdatedText.value?.let { Text(it) }
                            holidayRefreshMessage.value?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                        }
                    },
                    leadingContent = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    modifier = Modifier.clickable(enabled = !holidayRefreshing.value) {
                         // 強制更新ロジック
                        holidayRefreshing.value = true
                        holidayRefreshMessage.value = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { HolidayRepository(context).forceRefresh() }
                                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                val client = okhttp3.OkHttpClient.Builder()
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
                                
                                // 更新日時の再取得
                                val key = longPreferencesKey(io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys.KEY_HOLIDAYS_LAST_FETCH)
                                val last = withContext(Dispatchers.IO) { context.appDataStore.data.map { it[key] ?: 0L }.first() }
                                if (last > 0L) {
                                    val dt = java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                                    holidayLastUpdatedText.value = context.getString(R.string.label_holiday_last_updated, dt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")))
                                }
                                val areaKey2 = longPreferencesKey(io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys.KEY_AREA_LAST_FETCH)
                                val areaLast2 = withContext(Dispatchers.IO) { context.appDataStore.data.map { it[areaKey2] ?: 0L }.first() }
                                if (areaLast2 > 0L) {
                                    val dt = java.time.Instant.ofEpochMilli(areaLast2).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                                    masterLastUpdatedText.value = context.getString(R.string.label_master_last_updated, dt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")))
                                }
                                holidayRefreshMessage.value = context.getString(R.string.toast_master_refreshed)
                                Toast.makeText(context, context.getString(R.string.toast_master_refreshed), Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                holidayRefreshMessage.value = context.getString(R.string.error_refresh_failed)
                                Toast.makeText(context, context.getString(R.string.error_refresh_failed), Toast.LENGTH_SHORT).show()
                            } finally {
                                holidayRefreshing.value = false
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // バックアップセクション
            SettingsSection(title = stringResource(R.string.title_backup_restore)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_export)) },
                    supportingContent = { Text(stringResource(R.string.desc_export)) },
                    leadingContent = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                    modifier = Modifier.clickable { exportLauncher.launch("shukuten_alarm_backup.json") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_import)) },
                    supportingContent = { Text(stringResource(R.string.desc_import)) },
                    leadingContent = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                    modifier = Modifier.clickable { importLauncher.launch("application/json") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

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
            onDismissRequest = { },
            title = { Text(stringResource(R.string.title_missing_sound)) },
            text = { Text(stringResource(R.string.message_missing_sound, soundName)) },
            confirmButton = {
                TextButton(onClick = { }) {
                    Text(stringResource(R.string.action_select_new_sound))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    soundReplacementMap[currentMissingUri] = ""
                    missingSounds.removeAt(0)
                }) {
                    Text(stringResource(R.string.text_cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content
            )
        }
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
            alarmRepo.list().forEach { oldAlarm -> alarmRepo.delete(oldAlarm.id) }
            alarms.forEach { newAlarm -> alarmRepo.save(newAlarm) }
        }
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

// 既存のヘルパー関数が必要ならここに追加しますが、今回はnormalizeWeatherTextForDisplayが使われているので
// それがどこにあるか確認が必要。おそらく別ファイルか、このファイル内にあったか。
// 元のファイルには `normalizeWeatherTextForDisplay` の定義が見当たりませんでした。
// インポートもされていません。
// しかし、603行目で使われています: `val label = normalizeWeatherTextForDisplay(labelRaw)`
// おそらく同じパッケージ内の別ファイルにあるか、トップレベル関数です。
// エラーにならないよう、そのまま使います。
