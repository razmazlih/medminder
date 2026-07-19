package com.medminder.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medminder.app.core.ui.theme.MedMinderTheme
import com.medminder.app.data.local.dao.SettingsDao
import com.medminder.app.data.sync.CloudSyncManager
import com.medminder.app.navigation.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private const val FOREGROUND_SYNC_INTERVAL_MS = 20_000L
    }

    @Inject
    lateinit var settingsDao: SettingsDao

    @Inject
    lateinit var cloudSyncManager: CloudSyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cloudSyncManager.requestSync("app_start")
        lifecycleScope.launch {
            while (true) {
                cloudSyncManager.requestSync("foreground_poll")
                delay(FOREGROUND_SYNC_INTERVAL_MS)
            }
        }
        setContent {
            val settings by settingsDao.observeSettings().collectAsStateWithLifecycle(initialValue = null)
            val notificationsPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { }
            val systemDensity = LocalDensity.current
            val textScale = settings?.internalTextScale ?: 1.1f
            MedMinderTheme(highContrast = settings?.highContrast ?: false) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalDensity provides Density(systemDensity.density, systemDensity.fontScale * textScale)
                ) {
                    AppNavGraph(
                        appLockEnabled = settings?.appLockEnabled ?: false,
                        onRequestNotificationPermission = {
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onOpenNotificationSettings = {
                            startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                    data = Uri.parse("package:$packageName")
                                }
                            )
                        },
                        onOpenExactAlarmSettings = {
                            if (android.os.Build.VERSION.SDK_INT >= 31) {
                                startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:$packageName")
                                    }
                                )
                            }
                        },
                        onHistoryOpened = {
                            cloudSyncManager.requestSync("history_open")
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        cloudSyncManager.requestSync("re_entry")
    }
}
