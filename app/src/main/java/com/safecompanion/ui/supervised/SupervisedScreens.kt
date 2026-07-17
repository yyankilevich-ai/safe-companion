package com.safecompanion.ui.supervised

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safecompanion.data.AlertRepository
import com.safecompanion.service.WaNotificationListener
import com.safecompanion.ui.AppViewModel
import com.safecompanion.ui.isHe
import com.safecompanion.ui.severityColor
import kotlinx.coroutines.launch

class SupervisedViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AlertRepository.get(app)
    val pendingUploads = repo.pendingUploads
    val reportSent = kotlinx.coroutines.flow.MutableStateFlow(false)
    val helpSent = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun submitReport(text: String, note: String?) {
        viewModelScope.launch {
            repo.submitManualReport(text, note)
            reportSent.value = true
        }
    }

    fun askForHelp(message: String?) {
        viewModelScope.launch {
            repo.askForHelp(message)
            helpSent.value = true
        }
    }

    fun resetFlags() { reportSent.value = false; helpSent.value = false }
}

@Composable
fun EnrollScreen(appVm: AppViewModel) {
    val busy by appVm.busy.collectAsState()
    val error by appVm.error.collectAsState()
    var code by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(if (isHe()) "חיבור מכשיר הילד/ה" else "Connect the child's device",
            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(
            if (isHe())
                "בקשו מההורה ליצור 'קוד רישום' באפליקציה שלו, והזינו אותו כאן. הפיקוח יהיה גלוי במכשיר הזה."
            else
                "Ask the parent to generate an 'Enroll code' in their app, then enter it here. Supervision will be visible on this device.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code, onValueChange = { if (it.length <= 6) code = it.uppercase() },
            label = { Text(if (isHe()) "קוד רישום (6 תווים)" else "Enroll code (6 characters)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { appVm.enrollWithCode(code) {} },
            enabled = !busy && code.length == 6,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
            else Text(if (isHe()) "חבר מכשיר" else "Connect device")
        }
        TextButton(onClick = { appVm.backToModePicker() }) {
            Text(if (isHe()) "‹ חזרה" else "‹ Back")
        }
    }
}

@Composable
fun SupervisedRoot(appVm: AppViewModel) {
    val vm: SupervisedViewModel = viewModel()
    val context = LocalContext.current
    val session by appVm.session.collectAsState()
    val pending by vm.pendingUploads.collectAsState(initial = 0)
    val reportSent by vm.reportSent.collectAsState()
    val helpSent by vm.helpSent.collectAsState()

    val accessGranted = WaNotificationListener.isAccessGranted(context)
    var reportText by remember { mutableStateOf("") }
    var reportNote by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        Text(if (isHe()) "פיקוח בטיחות פעיל" else "Safety supervision is on",
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(
            (if (isHe()) "פרופיל במכשיר זה: " else "Profile on this device: ") +
                session.userEmailOrChild(),
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))
        // Permission status
        val color = if (accessGranted) severityColor(2) else severityColor(3)
        Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
            Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (accessGranted) (if (isHe()) "ההגנה פעילה" else "Protection active")
                        else (if (isHe()) "נדרשת גישה להתראות" else "Notification access needed"),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!accessGranted) Text(
                        if (isHe()) "בלי הרשאה זו לא ניתן לנתח הודעות וואטסאפ נכנסות."
                        else "Without it, incoming WhatsApp messages can't be analyzed.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!accessGranted) TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text(if (isHe()) "אפשר" else "Enable") }
            }
        }

        if (pending > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                (if (isHe()) "ממתין לשליחה: " else "Waiting to send: ") + pending,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
        // What is analyzed (transparency)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(if (isHe()) "מה נבדק" else "What is analyzed",
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isHe())
                        "רק טקסט של הודעות וואטסאפ נכנסות (דרך התראות המערכת) נבדק במכשיר הזה. אם נמצא חשש בטיחותי, נשלח סיכום מצונזר להורה — לא השיחה המלאה. סיסמאות, קודים ופרטי תשלום אינם נאספים."
                    else
                        "Only the text of incoming WhatsApp messages (via system notifications) is analyzed on this device. If a safety concern is found, a redacted summary is sent to the parent — not the full conversation. Passwords, codes, and payment details are never collected.",
                    fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        // Report conversation
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(if (isHe()) "דיווח על שיחה" else "Report a conversation",
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isHe()) "הדביקו או כתבו הודעה מטרידה, ותוכלו להוסיף הערה. היא תישלח לבדיקה."
                    else "Paste or type a message that bothered you, and add a note. It will be sent for review.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reportText, onValueChange = { reportText = it },
                    label = { Text(if (isHe()) "ההודעה" else "The message") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reportNote, onValueChange = { reportNote = it },
                    label = { Text(if (isHe()) "הערה (רשות)" else "Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        vm.submitReport(reportText, reportNote.ifBlank { null })
                        reportText = ""; reportNote = ""
                    },
                    enabled = reportText.isNotBlank()
                ) { Text(if (isHe()) "שלח דיווח" else "Send report") }
                if (reportSent) Text(if (isHe()) "נשלח. תודה." else "Sent. Thank you.",
                    color = severityColor(2), fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        // Ask for help
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(if (isHe()) "בקשת עזרה" else "Ask for help",
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isHe()) "שולח להורה סימן שאתם רוצים לדבר. אין צורך להסביר עכשיו."
                    else "Sends the parent a signal that you want to talk. No need to explain now.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.askForHelp(null) }) {
                    Text(if (isHe()) "אני צריך/ה עזרה" else "I need help")
                }
                if (helpSent) Text(if (isHe()) "ההורה קיבל/ה סימן." else "Your parent has been notified.",
                    color = severityColor(2), fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = { appVm.signOutOrUnenroll() }) {
            Text(if (isHe()) "הסר פיקוח מהמכשיר" else "Remove supervision from this device")
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun com.safecompanion.settings.SessionState.userEmailOrChild(): String =
    if (childName.isNotBlank()) childName else "—"
