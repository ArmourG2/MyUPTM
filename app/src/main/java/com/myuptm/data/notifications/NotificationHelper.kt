package com.myuptm.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.myuptm.MainActivity
import com.myuptm.R
import java.util.concurrent.atomic.AtomicInteger

// Sprint 7B: POC local notifications (no FCM). Fired when a new Firestore
// notification document arrives while the app is open, or when a class/clash
// warning is generated on-device. Silently no-ops if POST_NOTIFICATIONS is denied.
object NotificationHelper {

    private const val CHANNEL_ID = "myuptm_updates"
    private const val CHANNEL_NAME = "MyUPTM updates"
    private val nextId = AtomicInteger(1000)

    fun show(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )

        // Auto-granted below API 33; must be requested at runtime on 33+.
        // Denied -> silent no-op; the bell inbox still shows everything in-app.
        val hasPermission = (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            )
        if (!hasPermission) return

        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()

        manager.notify(nextId.incrementAndGet(), notification)
    }
}