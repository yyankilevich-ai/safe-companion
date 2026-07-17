package com.safecompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecompanion.engine.Category
import java.util.Locale

fun isHe(): Boolean = Locale.getDefault().language.let { it == "iw" || it == "he" }

fun catTitle(id: String): String = when (id) {
    "manual_report" -> if (isHe()) "דיווח ידני" else "Manual report"
    "help_request" -> if (isHe()) "בקשת עזרה" else "Help request"
    else -> Category.fromId(id)?.let { if (isHe()) it.titleHe else it.titleEn } ?: id
}

/** 5-level severity per spec §10. */
fun severityLabel(level: Int): String = if (isHe()) when (level) {
    5 -> "קריטית"; 4 -> "גבוהה"; 3 -> "בינונית"; 2 -> "נמוכה"; else -> "מידע"
} else when (level) {
    5 -> "Critical"; 4 -> "High"; 3 -> "Medium"; 2 -> "Low"; else -> "Info"
}

fun severityColor(level: Int): Color = when (level) {
    5 -> Color(0xFF7A1CAC)
    4 -> Color(0xFFB3261E)
    3 -> Color(0xFFB26A00)
    2 -> Color(0xFF3E7C59)
    else -> Color(0xFF5B6B63)
}

@Composable
fun SeverityPill(level: Int) {
    val c = severityColor(level)
    Box(
        Modifier.background(c.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(severityLabel(level), color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ModePickerScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (isHe()) "מלווה בטוח" else "Safe Companion",
            fontSize = 30.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (isHe())
                "מערכת התרעה מוקדמת לבטיחות ילדים. בחרו כיצד ישמש המכשיר הזה."
            else
                "An early-warning system for child safety. Choose how this device will be used.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        if (!vm.backendConfigured) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    if (isHe())
                        "האפליקציה עדיין לא מחוברת לשרת המשפחתי. יש להזין את פרטי Supabase בקובץ BackendConfig ולבנות מחדש."
                    else
                        "Not yet linked to a family backend. Add your Supabase details in BackendConfig and rebuild.",
                    Modifier.padding(14.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = { vm.goToSupervisorSetup() },
            enabled = vm.backendConfigured,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isHe()) "אני ההורה (מצב מפקח)" else "I'm the parent (Supervisor)")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { vm.goToSupervisedSetup() },
            enabled = vm.backendConfigured,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isHe()) "זהו מכשיר הילד/ה (מצב מפוקח)" else "This is the child's device (Supervised)")
        }
    }
}
