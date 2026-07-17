package com.safecompanion.ui.supervisor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safecompanion.engine.Category
import com.safecompanion.ui.AppViewModel
import com.safecompanion.ui.SeverityPill
import com.safecompanion.ui.catTitle
import com.safecompanion.ui.isHe
import com.safecompanion.ui.severityColor
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SupervisorAuthScreen(vm: AppViewModel) {
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(true) }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            if (isHe()) "חשבון הורה" else "Parent account",
            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (isHe()) "צרו חשבון מפקח או התחברו כדי לנהל את המשפחה."
            else "Create a supervisor account or sign in to manage your family.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text(if (isHe()) "אימייל" else "Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(if (isHe()) "סיסמה" else "Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (isSignUp) vm.signUp(email, password) {} else vm.signIn(email, password) {}
            },
            enabled = !busy && email.isNotBlank() && password.length >= 6,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
            else Text(
                if (isSignUp) (if (isHe()) "צור חשבון" else "Create account")
                else (if (isHe()) "התחבר" else "Sign in")
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { isSignUp = !isSignUp; vm.clearError() }) {
            Text(
                if (isSignUp) (if (isHe()) "כבר יש לי חשבון" else "I already have an account")
                else (if (isHe()) "אין לי חשבון עדיין" else "I don't have an account yet")
            )
        }
        TextButton(onClick = { vm.backToModePicker() }) {
            Text(if (isHe()) "‹ חזרה" else "‹ Back")
        }
    }
}

private enum class SvTab { FAMILY, ALERTS, SETTINGS, ABOUT }

@Composable
fun SupervisorRoot(appVm: AppViewModel, initialEventId: String?) {
    val vm: SupervisorViewModel = viewModel()
    val data by vm.data.collectAsState()
    var tab by remember { mutableStateOf(SvTab.ALERTS) }
    var openEventId by remember { mutableStateOf(initialEventId) }

    LaunchedEffect(Unit) { vm.refresh() }

    val open = openEventId
    if (open != null) {
        val ev = data.events.firstOrNull { it.id == open }
        EventDetailScreen(vm, ev, data.children) { openEventId = null }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == SvTab.FAMILY, { tab = SvTab.FAMILY },
                    icon = { Icon(Icons.Filled.People, null) },
                    label = { Text(if (isHe()) "משפחה" else "Family") })
                NavigationBarItem(tab == SvTab.ALERTS, { tab = SvTab.ALERTS },
                    icon = { Icon(Icons.Filled.Notifications, null) },
                    label = { Text(if (isHe()) "התראות" else "Alerts") })
                NavigationBarItem(tab == SvTab.SETTINGS, { tab = SvTab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text(if (isHe()) "הגדרות" else "Settings") })
                NavigationBarItem(tab == SvTab.ABOUT, { tab = SvTab.ABOUT },
                    icon = { Icon(Icons.Filled.Info, null) },
                    label = { Text(if (isHe()) "אודות" else "About") })
            }
        }
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            SvTab.FAMILY -> FamilyScreen(vm, data, m)
            SvTab.ALERTS -> AlertsScreen(vm, data, m) { openEventId = it }
            SvTab.SETTINGS -> SupervisorSettingsScreen(vm, appVm, data, m)
            SvTab.ABOUT -> SupervisorAboutScreen(m)
        }
    }
}

@Composable
private fun FamilyScreen(vm: SupervisorViewModel, data: DashboardData, modifier: Modifier) {
    val busy by vm.busy.collectAsState()
    val code by vm.newEnrollCode.collectAsState()
    val error by vm.error.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    if (showAdd) AddChildDialog(vm) { showAdd = false }
    if (code != null) EnrollCodeDialog(code!!) { vm.clearCode() }

    LazyColumn(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (isHe()) "המשפחה שלי" else "My family",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Button(onClick = { showAdd = true }, enabled = !busy) {
                    Text(if (isHe()) "+ ילד/ה" else "+ Child")
                }
            }
        }
        if (error != null) item {
            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        if (data.children.isEmpty()) item {
            Text(
                if (isHe()) "עדיין לא הוספתם ילדים. הוסיפו ילד/ה ואז צרו קוד רישום למכשיר שלו/ה."
                else "No children yet. Add a child, then generate an enrollment code for their device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(data.children, key = { it.id }) { child ->
            val device = data.devices.firstOrNull { it.childId == child.id }
            ChildCard(vm, child, device, busy)
        }
    }
}

@Composable
private fun ChildCard(vm: SupervisorViewModel, child: Child, device: Device?, busy: Boolean) {
    var showPolicy by remember { mutableStateOf(false) }
    if (showPolicy) PolicyDialog(vm, child) { showPolicy = false }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(child.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(ageLabel(child.ageBand), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (device == null) {
                Text(if (isHe()) "מכשיר לא מחובר" else "No device connected",
                    color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            } else {
                val protectOk = device.active && device.notificationAccess
                Text(
                    if (protectOk) (if (isHe()) "מוגן · פעיל" else "Protected · active")
                    else (if (isHe()) "הגנה מוגבלת — בדקו הרשאות במכשיר" else "Protection limited — check permissions on the device"),
                    color = if (protectOk) severityColor(2) else severityColor(3),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    (if (isHe()) "נראה לאחרונה: " else "Last seen: ") + shortTime(device.lastSeen),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.generateEnrollCode(child.id) }, enabled = !busy) {
                    Text(if (isHe()) "קוד רישום" else "Enroll code")
                }
                OutlinedButton(onClick = { showPolicy = true }, enabled = !busy) {
                    Text(if (isHe()) "מדיניות" else "Policy")
                }
            }
        }
    }
}

@Composable
private fun AddChildDialog(vm: SupervisorViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("AGE_11_13") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isHe()) "הוספת ילד/ה" else "Add a child") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text(if (isHe()) "שם או כינוי" else "Name or nickname") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(if (isHe()) "גיל" else "Age band", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("UNDER_11", "AGE_11_13", "AGE_14_17").forEach { a ->
                        FilterChip(age == a, { age = a }, label = { Text(ageLabel(a)) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.addChild(name, age, if (isHe()) "he" else "en"); onClose() },
                enabled = name.isNotBlank()) {
                Text(if (isHe()) "הוסף" else "Add")
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(if (isHe()) "ביטול" else "Cancel") } }
    )
}

@Composable
private fun EnrollCodeDialog(code: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isHe()) "קוד רישום" else "Enrollment code") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (isHe()) "במכשיר הילד/ה: בחרו 'מכשיר הילד', והזינו את הקוד. תקף ל-15 דקות."
                    else "On the child's device: choose 'child's device' and enter this code. Valid for 15 minutes.",
                    textAlign = TextAlign.Center, fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(code, fontSize = 40.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { Button(onClick = onClose) { Text(if (isHe()) "סגור" else "Done") } }
    )
}

@Composable
private fun PolicyDialog(vm: SupervisorViewModel, child: Child, onClose: () -> Unit) {
    val policy = remember { runCatching { JSONObject(child.policyJson) }.getOrDefault(JSONObject()) }
    var sensitivity by remember { mutableStateOf(policy.optString("sensitivity", "MEDIUM")) }
    var minSev by remember { mutableStateOf(policy.optInt("min_notify_severity", 3)) }
    var retention by remember { mutableStateOf(policy.optInt("retention_days", 14)) }
    var cloudAi by remember { mutableStateOf(policy.optBoolean("cloud_ai_allowed", true)) }
    val enabledCats = remember {
        val arr = policy.optJSONArray("categories") ?: JSONArray()
        val set = mutableStateOf((0 until arr.length()).map { arr.getString(it) }.toMutableSet())
        set
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text((if (isHe()) "מדיניות · " else "Policy · ") + child.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(if (isHe()) "רגישות" else "Sensitivity", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("LOW", "MEDIUM", "HIGH").forEach { s ->
                        FilterChip(sensitivity == s, { sensitivity = s }, label = { Text(s) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(if (isHe()) "התראה מחומרה" else "Notify from severity", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (2..5).forEach { s ->
                        FilterChip(minSev == s, { minSev = s }, label = { Text(s.toString()) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(if (isHe()) "שמירת נתונים (ימים)" else "Retention (days)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 7, 14, 30).forEach { d ->
                        FilterChip(retention == d, { retention = d }, label = { Text(d.toString()) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isHe()) "ניתוח AI בענן" else "Cloud AI analysis", Modifier.weight(1f), fontSize = 13.sp)
                    Switch(checked = cloudAi, onCheckedChange = { cloudAi = it })
                }
                Spacer(Modifier.height(6.dp))
                Text(if (isHe()) "קטגוריות" else "Categories", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Category.entries.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isHe()) c.titleHe else c.titleEn, Modifier.weight(1f), fontSize = 13.sp)
                        Switch(
                            checked = c.id in enabledCats.value,
                            onCheckedChange = {
                                val s = enabledCats.value.toMutableSet()
                                if (it) s.add(c.id) else s.remove(c.id)
                                enabledCats.value = s
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val out = JSONObject()
                    .put("sensitivity", sensitivity)
                    .put("min_notify_severity", minSev)
                    .put("retention_days", retention)
                    .put("cloud_ai_allowed", cloudAi)
                    .put("categories", JSONArray(enabledCats.value.toList()))
                vm.savePolicy(child, out.toString())
                onClose()
            }) { Text(if (isHe()) "שמור" else "Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(if (isHe()) "ביטול" else "Cancel") } }
    )
}

@Composable
private fun AlertsScreen(
    vm: SupervisorViewModel,
    data: DashboardData,
    modifier: Modifier,
    onOpen: (String) -> Unit
) {
    val busy by vm.busy.collectAsState()
    LazyColumn(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (isHe()) "התראות בטיחות" else "Safety alerts",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { vm.refresh() }, enabled = !busy) {
                    Text(if (busy) (if (isHe()) "מרענן…" else "Refreshing…") else (if (isHe()) "רענן" else "Refresh"))
                }
            }
        }
        if (data.events.isEmpty()) item {
            Text(
                if (isHe()) "אין התראות. כשמשהו שדורש תשומת לב יזוהה במכשיר של הילד/ה, הוא יופיע כאן."
                else "No alerts. When something worth attention is detected on a child's device, it appears here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(data.events, key = { it.id }) { ev ->
            val childName = data.children.firstOrNull { it.id == ev.childId }?.name ?: "—"
            EventRow(ev, childName) { onOpen(ev.id) }
        }
    }
}

@Composable
private fun EventRow(ev: Event, childName: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (isHe()) "ייתכן: " else "Possible: ") + catTitle(ev.category),
                    fontWeight = FontWeight.SemiBold
                )
                Text("$childName · ${shortTime(ev.createdAt)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!ev.seen) Text(if (isHe()) "חדש" else "New",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            SeverityPill(ev.severity)
        }
    }
}

@Composable
private fun EventDetailScreen(
    vm: SupervisorViewModel,
    ev: Event?,
    children: List<Child>,
    onBack: () -> Unit
) {
    LaunchedEffect(ev?.id) { ev?.let { if (!it.seen) vm.markSeen(it.id) } }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack) { Text(if (isHe()) "‹ חזרה" else "‹ Back") }
        if (ev == null) {
            Text(if (isHe()) "ההתראה לא נמצאה." else "Alert not found."); return@Column
        }
        val childName = children.firstOrNull { it.id == ev.childId }?.name ?: "—"
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (isHe()) "ייתכן: " else "Possible: ") + catTitle(ev.category),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            SeverityPill(ev.severity)
        }
        Text("$childName · ${shortTime(ev.createdAt)} · ${sourceLabel(ev.source)}",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ev.aiStatus == "done" && ev.confidence > 0) {
            Text((if (isHe()) "ביטחון: " else "Confidence: ") + "${(ev.confidence * 100).toInt()}%",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (ev.aiStatus == "failed") {
            Text(if (isHe()) "ניתוח AI לא זמין — הוצג דירוג הכללים." else "AI analysis unavailable — showing rule-based rating.",
                fontSize = 12.sp, color = severityColor(3))
        } else if (ev.aiStatus == "pending") {
            Text(if (isHe()) "ממתין לניתוח AI (רעננו)." else "Awaiting AI analysis (pull refresh).",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))
        DetailCard(if (isHe()) "סיכום" else "Summary",
            (if (isHe()) ev.summaryHe else ev.summaryEn).ifBlank { if (isHe()) ev.summaryEn else ev.summaryHe })
        Spacer(Modifier.height(10.dp))
        val rec = (if (isHe()) ev.recommendationHe else ev.recommendationEn)
        if (rec.isNotBlank()) {
            DetailCard(if (isHe()) "צעד מומלץ" else "Recommended step", rec); Spacer(Modifier.height(10.dp))
        }
        if (ev.conversationTitle.isNotBlank()) {
            DetailCard(if (isHe()) "שיחה" else "Conversation", ev.conversationTitle); Spacer(Modifier.height(10.dp))
        }
        DetailCard(
            if (isHe()) "קטע (מצונזר)" else "Excerpt (redacted)",
            ev.excerpt.ifBlank { "—" }
        )
    }
}

@Composable
private fun DetailCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(body, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SupervisorSettingsScreen(
    vm: SupervisorViewModel,
    appVm: AppViewModel,
    data: DashboardData,
    modifier: Modifier
) {
    val session by appVm.session.collectAsState()
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(session.aiModel) }
    var saved by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(if (isHe()) "הגדרות" else "Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(if (isHe()) "ניתוח AI (Claude)" else "AI analysis (Claude)",
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (vm.aiKeyPresent)
                            (if (isHe()) "מפתח API מוגדר ומאוחסן מוצפן במכשיר זה בלבד." else "API key is set and stored encrypted on this device only.")
                        else
                            (if (isHe()) "הזינו מפתח Anthropic API כדי להפעיל ניתוח חכם. המפתח לעולם לא מגיע למכשיר הילד/ה." else "Enter an Anthropic API key to enable smart analysis. The key never reaches the child's device."),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = key, onValueChange = { key = it },
                        label = { Text(if (isHe()) "Anthropic API key (sk-ant-…)" else "Anthropic API key (sk-ant-…)") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(if (isHe()) "מודל" else "Model", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val models = listOf(
                            "claude-haiku-4-5" to "Haiku (fast, cheap)",
                            "claude-sonnet-5" to "Sonnet (smarter)"
                        )
                        models.forEach { (id, label) ->
                            FilterChip(model == id, { model = id; vm.setAiModel(id) },
                                label = { Text(label) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (key.isNotBlank()) vm.setAiKey(key)
                        vm.setAiModel(model); saved = true; key = ""
                    }) { Text(if (isHe()) "שמור מפתח" else "Save key") }
                    if (saved) Text(if (isHe()) "נשמר." else "Saved.",
                        color = severityColor(2), fontSize = 12.sp)
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(if (isHe()) "חשבון" else "Account", fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    Text(session.userEmail, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { appVm.signOutOrUnenroll() }) {
                        Text(if (isHe()) "התנתק" else "Sign out")
                    }
                }
            }
        }
    }
}

@Composable
private fun SupervisorAboutScreen(modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(if (isHe()) "אודות" else "About", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        item {
            AboutCard(
                if (isHe()) "מטרה" else "Purpose",
                if (isHe())
                    "זו מערכת התרעה מוקדמת. מטרתה לזהות סיכון משמעותי ולסכם אותו — לא לתת גישה לכל הודעה. הדגש הוא על בטיחות, שקיפות ואמון בין הורה לילד."
                else
                    "This is an early-warning system. Its purpose is to detect meaningful risk and summarize it — not to give access to every message. The emphasis is on safety, transparency, and parent–child trust."
            )
        }
        item {
            AboutCard(
                if (isHe()) "פרטיות" else "Privacy",
                if (isHe())
                    "הניתוח הראשוני מתבצע במכשיר הילד/ה. רק ממצאי בטיחות מצונזרים נשלחים — לא שיחות מלאות. מזהים אישיים מוסתרים, והנתונים נמחקים אוטומטית לפי תקופת השמירה."
                else
                    "First-level analysis runs on the child's device. Only redacted safety findings are sent — never full conversations. Personal identifiers are masked, and data auto-deletes per the retention period."
            )
        }
        item {
            AboutCard(
                if (isHe()) "מגבלות גרסה זו" else "Limitations of this version",
                if (isHe())
                    "קליטה פסיבית מכסה הודעות נכנסות בלבד (דרך התראות אנדרואיד). התראות מגיעות בסנכרון (מיידי כשהאפליקציה פתוחה, אחרת עד ~15 דקות). מצב מפקח נתמך באנדרואיד בגרסה זו."
                else
                    "Passive capture covers incoming messages only (via Android notifications). Alerts arrive on sync (instant while the app is open, otherwise up to ~15 min). Supervisor mode is Android-only in this version."
            )
        }
    }
}

@Composable
private fun AboutCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(body, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

// ---------- small helpers ----------

private fun ageLabel(b: String) = if (isHe()) when (b) {
    "UNDER_11" -> "עד 11"; "AGE_14_17" -> "14–17"; else -> "11–13"
} else when (b) {
    "UNDER_11" -> "Under 11"; "AGE_14_17" -> "14–17"; else -> "11–13"
}

private fun sourceLabel(s: String) = if (isHe()) when (s) {
    "manual_report" -> "דיווח ידני"; "help_request" -> "בקשת עזרה"; else -> "זיהוי אוטומטי"
} else when (s) {
    "manual_report" -> "Manual report"; "help_request" -> "Help request"; else -> "Auto-detected"
}

/** Trim an ISO timestamp to "YYYY-MM-DD HH:MM". */
private fun shortTime(iso: String): String {
    if (iso.length < 16) return iso
    return iso.substring(0, 10) + " " + iso.substring(11, 16)
}
