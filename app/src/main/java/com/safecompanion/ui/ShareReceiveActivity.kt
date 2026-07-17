package com.safecompanion.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.safecompanion.data.AlertRepository
import com.safecompanion.settings.AppMode
import com.safecompanion.settings.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives text shared from WhatsApp (or any app) via the system Share sheet →
 * "Report conversation". Works only in SUPERVISED mode.
 */
class ShareReceiveActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        CoroutineScope(Dispatchers.Main).launch {
            val session = withContext(Dispatchers.IO) {
                SessionStore(applicationContext).current()
            }
            if (session.mode != AppMode.SUPERVISED) {
                Toast.makeText(
                    this@ShareReceiveActivity,
                    if (isHe()) "דיווח זמין רק במצב מפוקח." else "Reporting is available only in supervised mode.",
                    Toast.LENGTH_LONG
                ).show()
                finish(); return@launch
            }
            if (shared.isNullOrBlank()) {
                Toast.makeText(this@ShareReceiveActivity, "—", Toast.LENGTH_SHORT).show()
                finish(); return@launch
            }
            withContext(Dispatchers.IO) {
                AlertRepository.get(applicationContext).submitManualReport(shared, note = null)
            }
            Toast.makeText(
                this@ShareReceiveActivity,
                if (isHe()) "הדיווח נשלח לבדיקה. תודה." else "Report sent for review. Thank you.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }
}
