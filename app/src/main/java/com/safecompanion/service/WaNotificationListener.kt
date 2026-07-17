package com.safecompanion.service

import android.app.Notification
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.safecompanion.data.AlertRepository
import com.safecompanion.settings.AppMode
import com.safecompanion.settings.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The transparent, officially supported capture channel: Android's
 * NotificationListenerService. The user grants "Notification access" manually
 * in system Settings and can revoke it anytime. Active only in SUPERVISED mode.
 */
class WaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val watchedPackages = setOf("com.whatsapp", "com.whatsapp.w4b")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in watchedPackages) return

        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.category == Notification.CATEGORY_CALL) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extractText(extras)?.trim().orEmpty()

        if (text.isBlank()) return
        if (isNoise(title, text)) return

        val conversation = title.ifBlank { "WhatsApp" }
        val ts = sbn.postTime

        scope.launch {
            val session = SessionStore(applicationContext).current()
            if (session.mode != AppMode.SUPERVISED) return@launch
            AlertRepository.get(applicationContext).processMessage(
                appPackage = sbn.packageName,
                conversationTitle = conversation,
                text = text,
                timestampMs = ts
            )
        }
    }

    private fun extractText(extras: android.os.Bundle): String? {
        extras.getCharSequence(Notification.EXTRA_TEXT)?.let { return it.toString() }
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!lines.isNullOrEmpty()) return lines.last().toString()
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { return it.toString() }
        return null
    }

    private fun isNoise(title: String, text: String): Boolean {
        val t = text.lowercase()
        val noisy = listOf(
            "new messages", "checking for new messages", "backup",
            "messages from", "you have", "swipe down"
        )
        if (title.equals("WhatsApp", ignoreCase = true) && noisy.any { t.contains(it) }) return true
        if (Regex("""^\d+\s+new messages?$""").containsMatchIn(t)) return true
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        fun isAccessGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            return flat.split(":").any { it.contains(context.packageName) }
        }
    }
}
