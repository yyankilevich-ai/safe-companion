package com.safecompanion.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.safecompanion.R
import com.safecompanion.ui.MainActivity
import java.util.Locale

object ParentNotifier {

    const val CHANNEL_ALERTS = "alerts"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ALERTS) == null) {
            val channel = NotificationChannel(
                CHANNEL_ALERTS,
                "Safety alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifies a parent when a possible concern is detected." }
            mgr.createNotificationChannel(channel)
        }
    }

    /**
     * Lock-screen-safe notification (spec §11): names the child and urgency only.
     * Details are shown inside the app after the parent opens it.
     */
    fun notifyParent(context: Context, childName: String, severity: Int, eventId: String) {
        ensureChannel(context)
        val he = Locale.getDefault().language == "iw" || Locale.getDefault().language == "he"

        val title = if (he) "התראת בטיחות ילדים" else "Child-safety alert"
        val urgency = when {
            severity >= 5 -> if (he) "דחוף מאוד" else "Urgent"
            severity == 4 -> if (he) "חשוב" else "Important"
            else -> if (he) "לבדיקה" else "For review"
        }
        val body = if (he)
            "$urgency · זוהתה התראה עבור $childName. פתחו את האפליקציה לצפייה."
        else
            "$urgency · An alert was detected for $childName. Open the app to review."

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
        }
        val pending = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (severity >= 4) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_HIGH
            )
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(eventId.hashCode(), notification)
    }
}
