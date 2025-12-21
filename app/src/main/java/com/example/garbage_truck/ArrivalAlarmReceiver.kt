package com.example.garbage_truck

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent

class ArrivalAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "garbage_truck_local_alert_v2"
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "ALARM_TEST"
        // ✅ 將這個 Key 設為公開，這樣 MainActivity 才能讀取它
        const val EXTRA_SHOW_DIALOG = "SHOW_DIALOG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "⏰ onReceive() 觸發了！鬧鐘已響起！")

        val pointName = intent.getStringExtra("pointName") ?: "最近清運點"
        Log.d(TAG, "取得清運點名稱: $pointName")

        // ✅ 建立一個指令，目標是 MainActivity
        val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // ✅ 夾帶一張「顯示 Dialog」的便條
            putExtra(EXTRA_SHOW_DIALOG, true)
        }

        val pendingFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT

        // ✅ 將指令的目標設為 MainActivity
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainActivityIntent,
            pendingFlags
        )

        createNotificationChannelIfNeeded(context)

        val vibrationPattern = longArrayOf(0, 500, 250, 500)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.garbage_truck)
            .setContentTitle("垃圾車即將抵達")
            .setContentText("距離 $pointName 約 5 分鐘，記得準備垃圾喔！")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(vibrationPattern)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_LIGHTS)

        val notificationManager = NotificationManagerCompat.from(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "❌ 權限錯誤: POST_NOTIFICATIONS 未被授權，通知無法顯示！")
            return
        }

        Log.d(TAG, "✅ 權限正常，準備顯示通知...")
        notificationManager.notify(NOTIFICATION_ID, builder.build())
        Log.d(TAG, "✅ notify() 方法已呼叫，通知應該已經發送！")
    }

    private fun createNotificationChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "垃圾車定時提醒"
            val descriptionText = "垃圾車抵達前 5 分鐘的提醒通知"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}