package io.github.whitenoise0000.shukutenalarm.ui

// AutoMirrored版の音量アイコンを利用（RTL対応）。
// ネットワーククライアントのユーティリティ（Retrofit/kotlinx-serialization 用）
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.whitenoise0000.shukutenalarm.R
import io.github.whitenoise0000.shukutenalarm.settings.SettingsRepository
import io.github.whitenoise0000.shukutenalarm.ui.theme.ShukutenAlarmTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext


/**
 * メイン画面のアクティビティ。
 * - Compose Navigation により一覧/編集/設定を遷移表示する。
 * - エッジ・トゥ・エッジと Dynamic Color を有効化し、モダンな外観に整える。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // システムバーを透過し、コンテンツをエッジまで描画する
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // アプリ共通の Material3 テーマ
            ShukutenAlarmTheme {
                AppRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    val backstack = navController.currentBackStackEntryAsState()
    val route = backstack.value?.destination?.route ?: "home"
    // 編集画面の保存アクションをトップバーから呼び出すために登録/保持
    val editSaveAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    // スクロール時にトップバーの影や高さが自然に変化する挙動
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // アプリ起動時に不足権限のリクエストを行う（POST_NOTIFICATIONS / ACCESS_COARSE_LOCATION）。
    PermissionsAtLaunch()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            route.startsWith("edit") -> stringResource(R.string.title_add_alarm)
                            route == "settings" -> stringResource(R.string.title_settings)
                            else -> stringResource(R.string.title_alarm_list)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    // 編集/設定画面では戻るボタンを表示
                    if (route.startsWith("edit") || route == "settings") {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                },
                actions = {
                    if (route == "home") {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.label_settings)
                            )
                        }
                    } else if (route.startsWith("edit") || route == "settings") {
                        IconButton(onClick = { editSaveAction.value?.invoke() }) {
                            Icon(
                                Icons.Outlined.Save,
                                contentDescription = stringResource(R.string.text_save)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (route == "home") {
                FloatingActionButton(onClick = { navController.navigate("edit") }) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.label_add)
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(innerPadding)
        ) {
            AppNavHost(navController, editSaveAction)
        }
    }
}

/**
 * アプリ起動時に不足権限のリクエストを行うコンポーザブル。
 * - 通知権限: Android 13+（POST_NOTIFICATIONS）。
 * - 位置権限: 設定で「現在地を使用」が有効な場合のみ、ACCESS_COARSE_LOCATION を要求。
 */
@Composable
private fun PermissionsAtLaunch() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val asked = remember { mutableStateOf(false) }

    // ランチャーを先に用意
    val requestNotif =
        rememberLauncherForActivityResult(RequestPermission()) { /* 結果はとくに何もしない */ }
    val requestLocation = rememberLauncherForActivityResult(RequestPermission()) { /* 同上 */ }

    LaunchedEffect(Unit) {
        if (asked.value) return@LaunchedEffect
        asked.value = true

        // 通知権限（minSdk 33 以上のため常に対象）
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 位置権限（現在地使用時のみ）
        val useCurrent =
            withContext(Dispatchers.IO) { SettingsRepository(context).settingsFlow.first().useCurrentLocation }
        if (useCurrent) {
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!coarse) {
                requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    editSaveAction: androidx.compose.runtime.MutableState<(() -> Unit)?>
) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            AlarmListScreen(
                onEdit = { id -> navController.navigate("edit/$id") }
            )
        }
        composable("edit") {
            EditAlarmScreen(
                alarmId = null,
                onDone = { navController.popBackStack() },
                registerSave = { action -> editSaveAction.value = action })
        }
        composable("edit/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull()
            EditAlarmScreen(
                alarmId = id,
                onDone = { navController.popBackStack() },
                registerSave = { action -> editSaveAction.value = action })
        }
        composable("settings") {
            // 設定保存後はアラーム一覧へ戻るため、コールバックを渡す
            SettingsScreen(
                onSaved = { navController.popBackStack() },
                registerSave = { action -> editSaveAction.value = action }
            )
        }
    }
}
