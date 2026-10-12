package com.tekinflow.demo

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tekinflow.demo.data.notification.SystemNotificationNotifier
import com.tekinflow.demo.data.session.SessionManager
import com.tekinflow.demo.data.sync.SyncManager
import com.tekinflow.demo.data.sync.SyncStateStore
import com.tekinflow.demo.domain.model.KullaniciOturumu
import com.tekinflow.demo.navigation.AppNavGraph
import com.tekinflow.demo.navigation.LocalSessionManager
import com.tekinflow.demo.navigation.LocalSyncUiState
import com.tekinflow.demo.presentation.theme.LocalThemeController
import com.tekinflow.demo.presentation.theme.SatinalmaTheme
import com.tekinflow.demo.presentation.theme.ThemeController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var userSession: KullaniciOturumu

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var syncStateStore: SyncStateStore

    @Inject
    lateinit var systemNotificationNotifier: SystemNotificationNotifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        systemNotificationNotifier.ensureChannel()
        requestNotificationPermissionIfNeeded()
        syncManager.start()
        setContent {
            val isDarkTheme = userSession.isDarkTheme.collectAsStateWithLifecycle().value
            val syncUiState = syncStateStore.state.collectAsStateWithLifecycle().value
            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !isDarkTheme
                    isAppearanceLightNavigationBars = !isDarkTheme
                }
            }
            CompositionLocalProvider(
                LocalThemeController provides ThemeController(
                    isDarkTheme = isDarkTheme,
                    onThemeChange = userSession::setDarkTheme
                ),
                LocalSessionManager provides sessionManager,
                LocalSyncUiState provides syncUiState
            ) {
                SatinalmaTheme(darkTheme = isDarkTheme) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppNavGraph(userSession = userSession)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        syncManager.isAppForeground = true
    }

    override fun onStop() {
        syncManager.isAppForeground = false
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (BuildConfig.DEMO_MODE) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST_CODE
        )
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 2001
    }
}
