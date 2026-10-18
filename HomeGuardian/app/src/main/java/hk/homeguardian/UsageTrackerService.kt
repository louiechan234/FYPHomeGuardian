package hk.homeguardian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

class UsageTrackerService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    private var lastTopApp: String? = null
    private var lastTickTime = System.currentTimeMillis()
    private val iconBase64Cache = mutableMapOf<String, String>()
    private var loopCounter = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_STICKY
        isRunning = true

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "usage_channel")
            .setContentTitle("HomeGuardian")
            .setContentText("正在背景監控使用時間與 App 限制...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()

        startForeground(1, notification)

        serviceScope.launch {
            while (isActive) {
                try {
                    // 1. 檢查黑名單
                    checkLockStatusAndLockApp()

                    // 2. 每 30 秒上傳
                    loopCounter++
                    if (loopCounter >= 10) {
                        fetchAndUploadUsageStats()
                        loopCounter = 0
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(3000)
            }
        }
        return START_STICKY
    }

    private fun checkLockStatusAndLockApp() {
        // 模擬器 IP 為 10.0.2.2，真機請改為 192.168.1.137
        val url = URL("http://10.0.2.2:3000/get_lock_status")//"http://10.0.2.2:3000/data" this is virual phone ip
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 2000
        conn.readTimeout = 2000

        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val lockedArray = json.optJSONArray("lockedApps")
            val lockedList = mutableListOf<String>()

            if (lockedArray != null) {
                for (i in 0 until lockedArray.length()) {
                    lockedList.add(lockedArray.getString(i))
                }
            }
            if (lockedList.isNotEmpty()) {
                val currentTopApp = getForegroundAppPackage()
                if (currentTopApp != null && lockedList.contains(currentTopApp) && currentTopApp != packageName) {
                    val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("LOCKED_PACKAGE", currentTopApp)
                    }
                    startActivity(lockIntent)
                }
            }
        }
        conn.disconnect()
    }

    private fun fetchAndUploadUsageStats() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = packageManager

        val currentTopApp = getForegroundAppPackage() ?: "Unknown"
        val now = System.currentTimeMillis()
        val diffSeconds = (now - lastTickTime) / 1000

        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis

        val aggregateStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        val dataList = mutableListOf<AppUsageInfo>()

        aggregateStats.values.filter { it.totalTimeInForeground > 0 }.forEach { stats ->
            val pkgName = stats.packageName
            var appName = pkgName
            var iconBase64 = ""
            try {
                val appInfo = pm.getApplicationInfo(pkgName, 0)
                appName = pm.getApplicationLabel(appInfo).toString()
                // ✅ 取得並轉換圖片
                iconBase64 = iconBase64Cache.getOrPut(pkgName) {
                    getIconAsBase64(pkgName)
                }
            } catch (e: PackageManager.NameNotFoundException) {}

            var totalSeconds = stats.totalTimeInForeground / 1000

            if (pkgName == currentTopApp && currentTopApp == lastTopApp && currentTopApp != "Unknown") {
                totalSeconds += diffSeconds
            }

            // ✅ 存入 iconBase64
            dataList.add(AppUsageInfo(pkgName, appName, totalSeconds, null, iconBase64))
        }

        if (dataList.none { it.packageName == currentTopApp } && currentTopApp != "Unknown") {
            dataList.add(AppUsageInfo(currentTopApp, currentTopApp, diffSeconds, null, null))
        }

        lastTopApp = currentTopApp
        lastTickTime = now

        val sortedList = dataList.sortedByDescending { it.totalSeconds }
        sendDataToServer(sortedList, currentTopApp)
    }

    private fun getForegroundAppPackage(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(currentTime - 10000, currentTime)
        val event = UsageEvents.Event()
        var topPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                topPackage = event.packageName
            }
        }
        return topPackage
    }

    private fun sendDataToServer(dataList: List<AppUsageInfo>, currentTopApp: String) {
        // 模擬器 IP 為 10.0.2.2，真機請改為 192.168.1.137
        val url = URL("http://10.0.2.2:3000/data")//"http://10.0.2.2:3000/data" this is virual phone ip
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.doOutput = true
        conn.connectTimeout = 5000

        val jsonBuilder = StringBuilder("[")
        dataList.forEachIndexed { index, app ->
            jsonBuilder.append("{")
            jsonBuilder.append("\"packageName\":\"${app.packageName}\",")
            jsonBuilder.append("\"appName\":\"${app.appName}\",")
            jsonBuilder.append("\"totalSeconds\":${app.totalSeconds},")
            jsonBuilder.append("\"iconBase64\":\"${app.iconBase64 ?: ""}\"") // ✅ 加上 Base64
            jsonBuilder.append("}")
            if (index < dataList.size - 1) jsonBuilder.append(",")
        }
        jsonBuilder.append("]")

        val finalJson = """
            {
                "currentActiveApp": "$currentTopApp",
                "rawData": $jsonBuilder
            }
        """.trimIndent()

        OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
            writer.write(finalJson)
            writer.flush()
        }

        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            println("背景服務：數據上傳成功！當前前台 App: $currentTopApp")
        }
        conn.disconnect()
    }

    // ✅ 新增：把 Drawable 轉成 Base64 的函數
    // ✅ 完美的 JPG 轉換函數（包含白色底色與縮放）
    private fun getIconAsBase64(pkgName: String): String {
        return try {
            val drawable = packageManager.getApplicationIcon(pkgName)

            // 1. 建立一個 64x64 的畫布
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 2. ✅ 關鍵：將畫布塗成純白色（防止 JPG 透明背景變黑）
            canvas.drawColor(android.graphics.Color.WHITE)

            // 3. 將 App 圖示畫在白色畫布上
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            // 4. 壓縮成 JPG (品質設為 80)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)

            // 5. 轉成 Base64，前綴記得改成 jpeg
            "data:image/jpeg;base64," + Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "usage_channel",
                "使用時間監控",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}