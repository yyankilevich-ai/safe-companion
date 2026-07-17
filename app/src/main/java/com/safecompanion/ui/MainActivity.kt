package com.safecompanion.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safecompanion.settings.AppMode
import com.safecompanion.ui.supervised.SupervisedRoot
import com.safecompanion.ui.supervisor.SupervisorRoot
import com.safecompanion.ui.theme.SafeCompanionTheme

class MainActivity : ComponentActivity() {

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialEventId = intent.getStringExtra(EXTRA_EVENT_ID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            SafeCompanionTheme {
                val vm: AppViewModel = viewModel()
                val session by vm.session.collectAsState()
                val setup by vm.setupRoute.collectAsState()
                when (session.mode) {
                    AppMode.SUPERVISOR -> SupervisorRoot(vm, initialEventId)
                    AppMode.SUPERVISED -> SupervisedRoot(vm)
                    AppMode.UNSET -> when (setup) {
                        AppViewModel.SetupRoute.NONE -> ModePickerScreen(vm)
                        AppViewModel.SetupRoute.SUPERVISOR -> com.safecompanion.ui.supervisor.SupervisorAuthScreen(vm)
                        AppViewModel.SetupRoute.SUPERVISED -> com.safecompanion.ui.supervised.EnrollScreen(vm)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
    }
}
