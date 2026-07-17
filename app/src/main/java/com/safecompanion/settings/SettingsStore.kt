package com.safecompanion.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.safecompanion.engine.AgeBand
import com.safecompanion.engine.Category
import com.safecompanion.engine.RuleEngine
import com.safecompanion.engine.Sensitivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "session")

enum class AppMode { UNSET, SUPERVISOR, SUPERVISED }

/** Non-secret session + configuration state. */
data class SessionState(
    val mode: AppMode = AppMode.UNSET,
    // Supervisor
    val userId: String = "",
    val userEmail: String = "",
    // Supervised
    val deviceId: String = "",
    val childId: String = "",
    val childName: String = "",
    val ageBand: String = "AGE_11_13",
    val policyJson: String = "",
    val analyzedCount: Long = 0,
    // Parent-side sync + AI config
    val lastEventCursor: String = "1970-01-01T00:00:00Z",
    val aiModel: String = "claude-haiku-4-5",
    val minNotifySeverity: Long = 3
)

class SessionStore(private val context: Context) {

    private object K {
        val MODE = stringPreferencesKey("mode")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val CHILD_ID = stringPreferencesKey("child_id")
        val CHILD_NAME = stringPreferencesKey("child_name")
        val AGE_BAND = stringPreferencesKey("age_band")
        val POLICY = stringPreferencesKey("policy_json")
        val ANALYZED = longPreferencesKey("analyzed_count")
        val CURSOR = stringPreferencesKey("event_cursor")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val MIN_NOTIFY = longPreferencesKey("min_notify_severity")
    }

    val state: Flow<SessionState> = context.dataStore.data.map { p ->
        SessionState(
            mode = runCatching { AppMode.valueOf(p[K.MODE] ?: "UNSET") }.getOrDefault(AppMode.UNSET),
            userId = p[K.USER_ID] ?: "",
            userEmail = p[K.USER_EMAIL] ?: "",
            deviceId = p[K.DEVICE_ID] ?: "",
            childId = p[K.CHILD_ID] ?: "",
            childName = p[K.CHILD_NAME] ?: "",
            ageBand = p[K.AGE_BAND] ?: "AGE_11_13",
            policyJson = p[K.POLICY] ?: "",
            analyzedCount = p[K.ANALYZED] ?: 0,
            lastEventCursor = p[K.CURSOR] ?: "1970-01-01T00:00:00Z",
            aiModel = p[K.AI_MODEL] ?: "claude-haiku-4-5",
            minNotifySeverity = p[K.MIN_NOTIFY] ?: 3
        )
    }

    suspend fun current(): SessionState = state.first()

    suspend fun setMode(mode: AppMode) = context.dataStore.edit { it[K.MODE] = mode.name }

    suspend fun setSupervisor(userId: String, email: String) = context.dataStore.edit {
        it[K.USER_ID] = userId; it[K.USER_EMAIL] = email
    }

    suspend fun setEnrollment(
        deviceId: String, childId: String, childName: String, ageBand: String, policyJson: String
    ) = context.dataStore.edit {
        it[K.DEVICE_ID] = deviceId; it[K.CHILD_ID] = childId
        it[K.CHILD_NAME] = childName; it[K.AGE_BAND] = ageBand; it[K.POLICY] = policyJson
    }

    suspend fun updatePolicy(childName: String, ageBand: String, policyJson: String) =
        context.dataStore.edit {
            it[K.CHILD_NAME] = childName; it[K.AGE_BAND] = ageBand; it[K.POLICY] = policyJson
        }

    suspend fun incrementAnalyzed() =
        context.dataStore.edit { it[K.ANALYZED] = (it[K.ANALYZED] ?: 0) + 1 }

    suspend fun setCursor(iso: String) = context.dataStore.edit { it[K.CURSOR] = iso }

    suspend fun setAiModel(model: String) = context.dataStore.edit { it[K.AI_MODEL] = model }

    suspend fun setMinNotifySeverity(v: Long) = context.dataStore.edit { it[K.MIN_NOTIFY] = v }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }

    // ---------- Policy → engine config ----------

    fun engineConfigFrom(stateNow: SessionState): RuleEngine.Config {
        val defaults = RuleEngine.Config(
            Sensitivity.MEDIUM,
            runCatching { AgeBand.valueOf(stateNow.ageBand) }.getOrDefault(AgeBand.AGE_11_13),
            Category.entries.map { it.id }.toSet()
        )
        if (stateNow.policyJson.isBlank()) return defaults
        return try {
            val p = JSONObject(stateNow.policyJson)
            val cats = p.optJSONArray("categories")?.let { arr: JSONArray ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } ?: defaults.enabledCategoryIds
            RuleEngine.Config(
                runCatching { Sensitivity.valueOf(p.optString("sensitivity", "MEDIUM")) }
                    .getOrDefault(Sensitivity.MEDIUM),
                defaults.ageBand,
                cats
            )
        } catch (_: Exception) { defaults }
    }

    fun cloudAiAllowed(stateNow: SessionState): Boolean = try {
        if (stateNow.policyJson.isBlank()) true
        else JSONObject(stateNow.policyJson).optBoolean("cloud_ai_allowed", true)
    } catch (_: Exception) { true }

    fun retentionDays(stateNow: SessionState): Int = try {
        if (stateNow.policyJson.isBlank()) 14
        else JSONObject(stateNow.policyJson).optInt("retention_days", 14)
    } catch (_: Exception) { 14 }
}

/**
 * Secrets live in EncryptedSharedPreferences (Android Keystore-backed), separate
 * from the plain session state: parent auth tokens, parent AI key, device token.
 */
class SecretStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secrets",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var accessToken: String
        get() = prefs.getString("access_token", "") ?: ""
        set(v) { prefs.edit().putString("access_token", v).apply() }

    var refreshToken: String
        get() = prefs.getString("refresh_token", "") ?: ""
        set(v) { prefs.edit().putString("refresh_token", v).apply() }

    var deviceToken: String
        get() = prefs.getString("device_token", "") ?: ""
        set(v) { prefs.edit().putString("device_token", v).apply() }

    var anthropicKey: String
        get() = prefs.getString("anthropic_key", "") ?: ""
        set(v) { prefs.edit().putString("anthropic_key", v).apply() }

    fun clear() = prefs.edit().clear().apply()
}
