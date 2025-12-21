package com.example.garbage_truck

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.app.PendingIntent

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "fcm_default_channel"   // 要跟 Manifest meta-data 裡的一致
        private const val NOTIFICATION_ID = 1001
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // 取得標題、內容：優先用 notification 欄位，沒有就 fallback 到 data
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "垃圾車通知"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: "您有一則新通知"

        // data 裡如果有 show_animation=true，就點通知時帶旗標進去
        val showAnimation = message.data["show_animation"] == "true"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (showAnimation) {
                // ✅ 直接使用字串作為 Key，不再依賴 MainActivity
                putExtra("SHOW_ANIMATION", true)
            }
        }

        val pendingFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            pendingFlags
        )

        createNotificationChannelIfNeeded()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.garbage_truck)   // 可以改成你自己的通知 icon
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)   // 抬頭通知
            .setDefaults(NotificationCompat.DEFAULT_ALL)     // 聲音 / 震動 / 燈光

        val notificationManager = NotificationManagerCompat.from(this)

        // 🔒 Android 13+ 要檢查 POST_NOTIFICATIONS，避免 lint 警告
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // 沒拿到權限就安靜地不顯示
            return
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        android.util.Log.d("FCM_TOKEN", "🔥 Token：$token")

        //（可選）存起來，之後在 SettingsFragment 用得到
        getSharedPreferences("fcm", MODE_PRIVATE)
            .edit()
            .putString("token", token)
            .apply()
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "垃圾車通知"
            val descriptionText = "垃圾車相關提醒與推播訊息"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}