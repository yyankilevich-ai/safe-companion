package com.safecompanion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safecompanion.BackendConfig
import com.safecompanion.net.SupabaseApi
import com.safecompanion.settings.AppMode
import com.safecompanion.settings.SecretStore
import com.safecompanion.settings.SessionState
import com.safecompanion.settings.SessionStore
import com.safecompanion.work.ChildSyncWorker
import com.safecompanion.work.ParentSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Shared top-level state: which mode we're in, plus busy/error signalling for
 * the auth/enrollment flows.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SessionStore(app)
    private val secrets = SecretStore(app)

    val session: StateFlow<SessionState> =
        store.state.stateIn(viewModelScope, SharingStarted.Eagerly, SessionState())

    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    /** Pre-login navigation while mode is still UNSET. */
    enum class SetupRoute { NONE, SUPERVISOR, SUPERVISED }
    val setupRoute = MutableStateFlow(SetupRoute.NONE)

    fun goToSupervisorSetup() { error.value = null; setupRoute.value = SetupRoute.SUPERVISOR }
    fun goToSupervisedSetup() { error.value = null; setupRoute.value = SetupRoute.SUPERVISED }
    fun backToModePicker() { error.value = null; setupRoute.value = SetupRoute.NONE }

    val backendConfigured: Boolean get() = BackendConfig.isConfigured

    fun clearError() { error.value = null }

    // ---------------- Supervisor auth ----------------

    fun signUp(email: String, password: String, onDone: () -> Unit) =
        authFlow(onDone) { SupabaseApi.signUp(email.trim(), password) }

    fun signIn(email: String, password: String, onDone: () -> Unit) =
        authFlow(onDone) { SupabaseApi.signIn(email.trim(), password) }

    private fun authFlow(onDone: () -> Unit, call: suspend () -> SupabaseApi.Session) {
        viewModelScope.launch {
            busy.value = true; error.value = null
            try {
                val s = withContext(Dispatchers.IO) { call() }
                secrets.accessToken = s.accessToken
                secrets.refreshToken = s.refreshToken
                store.setSupervisor(s.userId, s.email)
                store.setMode(AppMode.SUPERVISOR)
                ParentSyncWorker.schedulePeriodic(getApplication())
                onDone()
            } catch (e: Exception) {
                error.value = e.message ?: "Sign-in failed"
            } finally {
                busy.value = false
            }
        }
    }

    // ---------------- Supervised enrollment ----------------

    fun enrollWithCode(code: String, onDone: () -> Unit) {
        viewModelScope.launch {
            busy.value = true; error.value = null
            try {
                val resp = withContext(Dispatchers.IO) {
                    SupabaseApi.rpc(
                        "claim_enrollment",
                        JSONObject().put("p_code", code.trim().uppercase()).put("p_platform", "android")
                    )
                }
                if (!resp.optBoolean("ok", false)) {
                    error.value = when (resp.optString("error")) {
                        "invalid_or_expired_code" -> "That code is invalid or has expired."
                        else -> "Enrollment failed."
                    }
                    return@launch
                }
                secrets.deviceToken = resp.optString("device_token")
                store.setEnrollment(
                    deviceId = resp.optString("device_id"),
                    childId = resp.optString("child_id"),
                    childName = resp.optString("child_name"),
                    ageBand = resp.optString("age_band", "AGE_11_13"),
                    policyJson = resp.optJSONObject("policy")?.toString() ?: ""
                )
                store.setMode(AppMode.SUPERVISED)
                ChildSyncWorker.schedulePeriodic(getApplication())
                onDone()
            } catch (e: Exception) {
                error.value = e.message ?: "Enrollment failed"
            } finally {
                busy.value = false
            }
        }
    }

    fun signOutOrUnenroll() {
        viewModelScope.launch {
            store.clearAll()
            secrets.clear()
        }
    }
}
