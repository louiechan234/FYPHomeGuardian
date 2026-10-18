package hk.homeguardian

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import hk.homeguardian.ui.theme.HomeGuardianTheme
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

// ✅ 修改資料類別：保留 icon (給UI)，新增 iconBase64 (給伺服器)
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalSeconds: Long,
    val icon: Drawable? = null,
    val iconBase64: String? = null
)

class MainActivity : ComponentActivity() {

    private var hasPermission by mutableStateOf(false)
    private var appUsageList by mutableStateOf<List<AppUsageInfo>>(emptyList())
    private var serverMessage by mutableStateOf("等待伺服器連線...")
    private var lastUpdateTime = System.currentTimeMillis()
    private val iconCache = mutableMapOf<String, Drawable?>()

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startTrackerService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            HomeGuardianTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        Text(
                            text = "伺服器狀態: $serverMessage",
                            modifier = Modifier.padding(16.dp)
                        )

                        UsageScreen(
                            hasPermission = hasPermission,
                            usageList = appUsageList,
                            onRequestPermission = {
                                try {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    this@MainActivity.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 前台每 30 秒更新一次畫面
            LaunchedEffect(Unit) {
                while (true) {
                    try {
                        val response = withContext(Dispatchers.IO) {
                            // 如果你是模擬器用 10.0.2.2，真機請改 192.168.1.137
                            val url = URL("http://10.0.2.2:3000/get_update")//192.168.1.137"http://10.0.2.2:3000/get_update" this is virual phone ip
                            val conn = url.openConnection() as HttpURLConnection
                            conn.requestMethod = "GET"
                            conn.connectTimeout = 3000
                            conn.readTimeout = 3000
                            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                                conn.inputStream.bufferedReader().readText()
                            } else null
                        }

                        if (response != null) {
                            val json = JSONObject(response)
                            serverMessage = "版本 ${json.optInt("version", 0)} - ${json.optString("message", "無訊息")}"
                        }

                        if (hasPermission) {
                            loadUsageStatsForUI()
                        }
                    } catch (e: Exception) {
                        serverMessage = "錯誤: ${e.message}"
                    }
                    delay(30000)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasPermission = checkUsageStatsPermission()
        if (hasPermission) {
            loadUsageStatsForUI()
            startTrackerService()
        }
    }

    private fun startTrackerService() {
        val intent = Intent(this, UsageTrackerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun checkUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.noteOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }

    private fun loadUsageStatsForUI() {
        CoroutineScope(Dispatchers.IO).launch {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm = packageManager
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val startTime = calendar.timeInMillis

            val aggregateStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
            val newList = mutableListOf<AppUsageInfo>()

            aggregateStats.values.filter { it.totalTimeInForeground > 0 }.forEach { stats ->
                val pkgName = stats.packageName
                var appName = pkgName
                var icon: Drawable? = null

                if (iconCache.containsKey(pkgName)) {
                    icon = iconCache[pkgName]
                    try {
                        val appInfo = pm.getApplicationInfo(pkgName, 0)
                        appName = pm.getApplicationLabel(appInfo).toString()
                    } catch (e: PackageManager.NameNotFoundException) {}
                } else {
                    try {
                        val appInfo = pm.getApplicationInfo(pkgName, 0)
                        appName = pm.getApplicationLabel(appInfo).toString()
                        icon = pm.getApplicationIcon(appInfo)
                        iconCache[pkgName] = icon
                    } catch (e: PackageManager.NameNotFoundException) {}
                }

                var totalSeconds = stats.totalTimeInForeground / 1000
                if (pkgName == packageName) {
                    totalSeconds += (System.currentTimeMillis() - lastUpdateTime) / 1000
                    lastUpdateTime = System.currentTimeMillis()
                }

                // UI 只需要 Drawable，不用 Base64
                newList.add(AppUsageInfo(pkgName, appName, totalSeconds, icon, null))
            }

            if (newList.none { it.packageName == packageName }) {
                newList.add(AppUsageInfo(packageName, "HomeGuardian", (System.currentTimeMillis() - lastUpdateTime) / 1000, null, null))
            }

            withContext(Dispatchers.Main) {
                appUsageList = newList.sortedByDescending { it.totalSeconds }
            }
        }
    }
}

@Composable
fun UsageScreen(
    hasPermission: Boolean,
    usageList: List<AppUsageInfo>,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        if (!hasPermission) {
            Text(text = "HomeGuardian 需要「使用量資料存取權」來統計螢幕時間。")
            Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 8.dp)) {
                Text("前往設定開啟權限")
            }
        } else {
            Text(text = "今日 App 使用時間排名（數據已自動上傳）：", modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn {
                items(usageList) { appInfo ->
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (appInfo.icon != null) {
                            Image(
                                painter = rememberDrawablePainter(drawable = appInfo.icon),
                                contentDescription = appInfo.appName,
                                modifier = Modifier.size(40.dp).padding(end = 12.dp)
                            )
                        }
                        Column {
                            Text(text = appInfo.appName)
                            val mins = appInfo.totalSeconds / 60
                            val secs = appInfo.totalSeconds % 60
                            Text(text = "${mins} 分鐘 ${secs} 秒")
                        }
                    }
                }
            }
        }
    }
}