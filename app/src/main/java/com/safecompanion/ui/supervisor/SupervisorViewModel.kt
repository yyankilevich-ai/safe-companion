package com.safecompanion.ui.supervisor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safecompanion.net.SupabaseApi
import com.safecompanion.settings.SecretStore
import com.safecompanion.settings.SessionStore
import com.safecompanion.work.ParentSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class Child(
    val id: String,
    val name: String,
    val ageBand: String,
    val language: String,
    val policyJson: String
)

data class Device(
    val id: String,
    val childId: String,
    val platform: String,
    val lastSeen: String,
    val notificationAccess: Boolean,
    val active: Boolean
)

data class Event(
    val id: String,
    val childId: String,
    val createdAt: String,
    val source: String,
    val category: String,
    val severity: Int,
    val confidence: Double,
    val conversationTitle: String,
    val excerpt: String,
    val summaryEn: String,
    val summaryHe: String,
    val recommendationEn: String,
    val recommendationHe: String,
    val aiStatus: String,
    val seen: Boolean
)

data class DashboardData(
    val children: List<Child> = emptyList(),
    val devices: List<Device> = emptyList(),
    val events: List<Event> = emptyList()
)

class SupervisorViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SessionStore(app)
    private val secrets = SecretStore(app)

    val data = MutableStateFlow(DashboardData())
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val newEnrollCode = MutableStateFlow<String?>(null)

    fun clearError() { error.value = null }
    fun clearCode() { newEnrollCode.value = null }

    private var token: String
        get() = secrets.accessToken
        set(v) { secrets.accessToken = v }

    /** Run a Supabase call, refreshing the session token once on 401. */
    private suspend fun <T> authed(block: (String) -> T): T = withContext(Dispatchers.IO) {
        try {
            block(token)
        } catch (e: SupabaseApi.ApiException) {
            if (e.code == 401 && secrets.refreshToken.isNotBlank()) {
                val s = SupabaseApi.refresh(secrets.refreshToken)
                token = s.accessToken
                secrets.refreshToken = s.refreshToken
                block(token)
            } else throw e
        }
    }

    fun refresh() {
        viewModelScope.launch {
            busy.value = true; error.value = null
            try {
                // Pull + AI-analyze + notify, then reload the tables.
                withContext(Dispatchers.IO) { runCatching { ParentSyncWorker.runSync(getApplication()) } }
                load()
            } catch (e: Exception) {
                error.value = e.message ?: "Could not refresh"
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun load() {
        val childrenArr = authed { t -> SupabaseApi.select("children", "select=*&order=created_at.asc", t) }
        val devicesArr = authed { t -> SupabaseApi.select("devices", "select=*", t) }
        val eventsArr = authed { t ->
            SupabaseApi.select("events", "select=*&order=created_at.desc&limit=200", t)
        }

        val children = (0 until childrenArr.length()).map { i ->
            val o = childrenArr.getJSONObject(i)
            Child(
                id = o.optString("id"),
                name = o.optString("display_name"),
                ageBand = o.optString("age_band", "AGE_11_13"),
                language = o.optString("language", "he"),
                policyJson = o.optJSONObject("policy")?.toString() ?: "{}"
            )
        }
        val devices = (0 until devicesArr.length()).map { i ->
            val o = devicesArr.getJSONObject(i)
            Device(
                id = o.optString("id"),
                childId = o.optString("child_id"),
                platform = o.optString("platform", "android"),
                lastSeen = o.optString("last_seen", ""),
                notificationAccess = o.optJSONObject("perm_status")
                    ?.optBoolean("notification_access", false) ?: false,
                active = o.optBoolean("active", true)
            )
        }
        val events = (0 until eventsArr.length()).map { i ->
            val o = eventsArr.getJSONObject(i)
            Event(
                id = o.optString("id"),
                childId = o.optString("child_id"),
                createdAt = o.optString("created_at"),
                source = o.optString("source", "auto"),
                category = o.optString("category"),
                severity = o.optInt("severity", 2),
                confidence = o.optDouble("confidence", 0.0),
                conversationTitle = o.optString("conversation_title"),
                excerpt = o.optString("excerpt"),
                summaryEn = o.optString("summary_en"),
                summaryHe = o.optString("summary_he"),
                recommendationEn = o.optString("recommendation_en"),
                recommendationHe = o.optString("recommendation_he"),
                aiStatus = o.optString("ai_status", "none"),
                seen = o.optBoolean("seen", false)
            )
        }
        data.value = DashboardData(children, devices, events)
    }

    fun addChild(name: String, ageBand: String, language: String) {
        viewModelScope.launch {
            busy.value = true; error.value = null
            try {
                val uid = store.current().userId
                val row = JSONObject()
                    .put("family_uid", uid)
                    .put("display_name", name.trim())
                    .put("age_band", ageBand)
                    .put("language", language)
                authed { t -> SupabaseApi.insert("children", row, t) }
                load()
            } catch (e: Exception) {
                error.value = e.message ?: "Could not add child"
            } finally { busy.value = false }
        }
    }

    fun generateEnrollCode(childId: String) {
        viewModelScope.launch {
            busy.value = true; error.value = null
            try {
                val uid = store.current().userId
                val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                val code = (1..6).map { chars.random() }.joinToString("")
                val row = JSONObject()
                    .put("code", code)
                    .put("family_uid", uid)
                    .put("child_id", childId)
                authed { t -> SupabaseApi.insert("enroll_codes", row, t) }
                authed { t ->
                    SupabaseApi.insert(
                        "audit",
                        JSONObject().put("family_uid", uid).put("actor", "supervisor")
                            .put("action", "created_enroll_code").put("target", "child:$childId")
                            .put("result", "ok"),
                        t
                    )
                }
                newEnrollCode.value = code
            } catch (e: Exception) {
                error.value = e.message ?: "Could not create code"
            } finally { busy.value = false }
        }
    }

    fun savePolicy(child: Child, policyJson: String) {
        viewModelScope.launch {
            busy.value = true; error.value = null
            try {
                authed { t ->
                    SupabaseApi.update(
                        "children", "id=eq.${child.id}",
                        JSONObject().put("policy", JSONObject(policyJson)), t
                    )
                }
                load()
            } catch (e: Exception) {
                error.value = e.message ?: "Could not save policy"
            } finally { busy.value = false }
        }
    }

    fun markSeen(eventId: String) {
        viewModelScope.launch {
            try {
                authed { t ->
                    SupabaseApi.update("events", "id=eq.$eventId",
                        JSONObject().put("seen", true), t)
                }
                data.value = data.value.copy(
                    events = data.value.events.map { if (it.id == eventId) it.copy(seen = true) else it }
                )
            } catch (_: Exception) { /* non-fatal */ }
        }
    }

    // AI + notification settings
    val aiKeyPresent: Boolean get() = secrets.anthropicKey.isNotBlank()
    fun setAiKey(key: String) { secrets.anthropicKey = key.trim() }
    fun setAiModel(model: String) { viewModelScope.launch { store.setAiModel(model) } }
    fun setMinNotify(sev: Int) { viewModelScope.launch { store.setMinNotifySeverity(sev.toLong()) } }
}
