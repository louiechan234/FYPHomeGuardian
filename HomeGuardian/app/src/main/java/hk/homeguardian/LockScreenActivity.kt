package hk.homeguardian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LockScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 取得是哪個 App 被鎖定
        val lockedPackage = intent.getStringExtra("LOCKED_PACKAGE") ?: ""

        setContent {
            BackHandler { moveTaskToBack(true) }

            LaunchedEffect(Unit) {
                while (true) {
                    try {
                        val response = withContext(Dispatchers.IO) {
                            val url = URL("http://10.0.2.2:3000/get_lock_status")
                            val conn = url.openConnection() as HttpURLConnection
                            conn.connectTimeout = 2000
                            conn.readTimeout = 2000
                            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                                conn.inputStream.bufferedReader().readText()
                            } else null
                        }

                        if (response != null && lockedPackage.isNotEmpty()) {
                            val json = JSONObject(response)
                            val lockedArray = json.optJSONArray("lockedApps")
                            var isStillLocked = false

                            if (lockedArray != null) {
                                for (i in 0 until lockedArray.length()) {
                                    if (lockedArray.getString(i) == lockedPackage) {
                                        isStillLocked = true
                                        break
                                    }
                                }
                            }

                            // ✅ 如果這個 App 不在黑名單裡了（被解鎖），就關閉畫面
                            if (!isStillLocked) {
                                finish()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(3000)
                }
            }

            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFFB71C1C)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🚫 應用程式已鎖定",
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "家長限制了您使用此應用程式\n請聯繫家長解鎖",
                        color = Color.White,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}