package com.medminder.app.navigation

import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medminder.app.R
import com.medminder.app.feature.history.HistoryScreen
import com.medminder.app.feature.history.HistoryReportRequest
import com.medminder.app.feature.history.HistoryViewModel
import com.medminder.app.feature.hidden.HiddenIntakeLogScreen
import com.medminder.app.feature.hidden.HiddenIntakeLogViewModel
import com.medminder.app.feature.missed.MissedDoseScreen
import com.medminder.app.feature.applock.AppLockScreen
import com.medminder.app.feature.applock.AppLockViewModel
import com.medminder.app.feature.medications.MedicationEditorScreen
import com.medminder.app.feature.medications.MedicationListScreen
import com.medminder.app.feature.medications.MedicationViewModel
import com.medminder.app.feature.medications.AsNeededMedicationScreen
import com.medminder.app.feature.onboarding.OnboardingScreen
import com.medminder.app.feature.onboarding.OnboardingViewModel
import com.medminder.app.feature.settings.SettingsScreen
import com.medminder.app.feature.settings.SettingsViewModel
import com.medminder.app.feature.today.TodayScreen
import com.medminder.app.feature.today.TodayViewModel

@Composable
fun AppNavGraph(
    appLockEnabled: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onHistoryOpened: () -> Unit
) {
    val navController = rememberNavController()
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboardingState by onboardingViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(onboardingState.completed) {
        if (onboardingState.completed) {
            navController.navigate(Routes.Today) {
                popUpTo(Routes.Onboarding) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var shouldLockOnNextForeground by remember { mutableStateOf(appLockEnabled) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(appLockEnabled) {
        if (!appLockEnabled) {
            shouldLockOnNextForeground = false
        }
    }

    DisposableEffect(lifecycleOwner, appLockEnabled, shouldLockOnNextForeground, currentRoute) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (appLockEnabled) {
                        shouldLockOnNextForeground = true
                    }
                }
                Lifecycle.Event.ON_START -> {
                    val alreadyOnLockScreen = currentRoute?.startsWith(Routes.AppLock) == true
                    if (appLockEnabled && shouldLockOnNextForeground && !alreadyOnLockScreen) {
                        navController.navigate(Routes.appLockRoute(forceLock = true))
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val showBottomBar = currentRoute in setOf(
        Routes.Today,
        Routes.Medications,
        Routes.AsNeededMedications,
        Routes.History,
        Routes.Settings
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                val items = listOf(
                    BottomNavItem(Routes.Today, R.string.tab_today, Icons.Filled.Home),
                    BottomNavItem(Routes.Medications, R.string.tab_medications, Icons.Filled.LocalPharmacy),
                    BottomNavItem(Routes.AsNeededMedications, R.string.tab_as_needed_medications, Icons.Filled.AddCircle),
                    BottomNavItem(Routes.History, R.string.tab_history, Icons.Filled.History),
                    BottomNavItem(Routes.Settings, R.string.tab_settings, Icons.Filled.Settings)
                )
                GlassBottomBar(
                    items = items,
                    currentRoute = currentRoute,
                    onItemClick = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Onboarding,
            modifier = Modifier.padding(
                top = innerPadding.calculateTopPadding(),
                bottom = 0.dp
            )
        ) {
            composable(Routes.Onboarding) {
                OnboardingScreen(
                    state = onboardingState,
                    onNameChanged = onboardingViewModel::onNameChanged,
                    on24HourChanged = onboardingViewModel::on24HourChanged,
                    onSeniorModeChanged = onboardingViewModel::onSeniorModeChanged,
                    onSubmit = onboardingViewModel::submit
                )
            }

            composable(Routes.Today) {
                val viewModel: TodayViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    viewModel.refreshNotificationState()
                }
                TodayScreen(
                    state = state,
                    onAction = viewModel::onAction,
                    onMarkAsNeededTaken = viewModel::markAsNeededMedicationTaken,
                    onAddOneTimeMedicationIntake = viewModel::addOneTimeMedicationIntake,
                    onEncouragementClick = viewModel::showNextEncouragement,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenNotificationSettings = onOpenNotificationSettings
                )
            }

            navigation(startDestination = Routes.Medications, route = Routes.MedicationsRoot) {
                composable(Routes.Medications) {
                    val parentEntry = remember {
                        navController.getBackStackEntry(Routes.MedicationsRoot)
                    }
                    val viewModel: MedicationViewModel = hiltViewModel(parentEntry)
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    MedicationListScreen(
                        state = state,
                        onAddClicked = { navController.navigate(Routes.medicationEditorRoute()) },
                        onEditClicked = { navController.navigate(Routes.medicationEditorRoute(it.id)) },
                        onDisableClicked = viewModel::disableMedication,
                        onRestoreClicked = viewModel::restoreMedication,
                        onDeleteConfirmed = viewModel::deleteMedication,
                        onShowInactiveChanged = viewModel::setShowInactiveMedications,
                        onOpenExactAlarmSettings = onOpenExactAlarmSettings
                    )
                }

                composable(Routes.AsNeededMedications) {
                    val parentEntry = remember {
                        navController.getBackStackEntry(Routes.MedicationsRoot)
                    }
                    val viewModel: MedicationViewModel = hiltViewModel(parentEntry)
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    AsNeededMedicationScreen(
                        state = state,
                        onAddMedication = viewModel::addAsNeededMedication,
                        onRenameMedication = viewModel::renameAsNeededMedication,
                        onDeleteMedication = viewModel::deleteAsNeededMedication,
                        onDisableMedication = viewModel::disableAsNeededMedication,
                        onRestoreMedication = viewModel::restoreAsNeededMedication,
                        onShowInactiveChanged = viewModel::setShowInactiveMedications
                    )
                }

                composable(
                    route = "${Routes.MedicationEditor}?${Routes.MedicationIdArg}={${Routes.MedicationIdArg}}",
                    arguments = listOf(navArgument(Routes.MedicationIdArg) {
                        type = NavType.StringType
                        nullable = true
                    })
                ) { entry ->
                    val parentEntry = remember(entry) {
                        navController.getBackStackEntry(Routes.MedicationsRoot)
                    }
                    val viewModel: MedicationViewModel = hiltViewModel(parentEntry)
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val medId = entry.arguments?.getString(Routes.MedicationIdArg).orEmpty().ifBlank { null }
                    val existing = state.allMedications.find { it.id == medId }
                    MedicationEditorScreen(
                        existing = existing,
                        errorMessage = state.error,
                        onBack = { navController.popBackStack() },
                        onSave = {
                            val isValid = viewModel.saveMedication(it)
                            if (isValid) navController.popBackStack()
                        }
                    )
                }
            }

            composable(Routes.History) {
                val viewModel: HistoryViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    onHistoryOpened()
                }
                val context = LocalContext.current
                var pendingReportRequest by remember { mutableStateOf<HistoryReportRequest?>(null) }
                var isShareIntent by remember { mutableStateOf(false) }
                val pdfLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf")
                ) { uri ->
                    val request = pendingReportRequest
                    if (uri != null && request != null) {
                        viewModel.exportPdf(uri, context.contentResolver, request) { isSuccess ->
                            if (isSuccess) {
                                if (isShareIntent) {
                                    sharePdfReport(context, uri)
                                } else {
                                    openPdfReport(context, uri)
                                }
                            }
                        }
                    }
                    pendingReportRequest = null
                    isShareIntent = false
                }
                HistoryScreen(
                    state = state,
                    onReportDownload = { request ->
                        pendingReportRequest = request
                        isShareIntent = false
                        pdfLauncher.launch("דוח-היסטוריית-תרופות.pdf")
                    },
                    onReportShare = { request ->
                        val reportFile = File(context.cacheDir, "reports/history_report.pdf")
                        reportFile.parentFile?.mkdirs()
                        val fileUri = Uri.fromFile(reportFile)
                        viewModel.exportPdf(fileUri, context.contentResolver, request) { isSuccess ->
                            if (isSuccess) {
                                val shareUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    reportFile
                                )
                                sharePdfReport(context, shareUri)
                            }
                        }
                    },
                    onMedicationFilterChanged = viewModel::setMedicationFilter,
                    onMedicationSearchQueryChanged = viewModel::setMedicationSearchQuery,
                    onDateRangeFilterChanged = viewModel::setDateRangeFilter,
                    onUpdateEventTime = viewModel::updateEventTime,
                    onDeleteEvent = viewModel::deleteEvent
                )
            }

            composable(Routes.Settings) {
                val viewModel: SettingsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    onTextScaleChanged = viewModel::setTextScale,
                    onHighContrastChanged = viewModel::setHighContrast,
                    onEscalatedReminderChanged = viewModel::setEscalatedReminderEnabled,
                    onCloudBackupChanged = viewModel::setCloudBackupEnabled,
                    onGenerateNewSyncCode = viewModel::generateNewSyncCode,
                    onSyncNow = viewModel::syncNow,
                    onExportAllLocalDataToCloud = viewModel::exportAllLocalDataToCloud,
                    onDisplayNameChanged = viewModel::setDisplayName,
                    onSaveDisplayName = viewModel::saveDisplayName,
                    onDismissSyncMessage = viewModel::clearSyncMessage,
                    onOpenHiddenLog = { navController.navigate(Routes.HiddenLog) },
                    onAppLockChanged = { enabled ->
                        viewModel.setAppLock(enabled)
                        if (enabled) {
                            navController.navigate(Routes.appLockRoute(forceLock = false))
                        }
                    }
                )
            }

            composable(Routes.HiddenLog) {
                val viewModel: HiddenIntakeLogViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HiddenIntakeLogScreen(
                    state = state,
                    onQueryChange = viewModel::setQuery,
                    onOperationFilterChange = viewModel::setOperationFilter,
                    onSourceFilterChange = viewModel::setSourceFilter,
                    onTimeRangeChange = viewModel::setTimeRange,
                    onSuspiciousOnlyChange = viewModel::setSuspiciousOnly,
                    onClearFilters = viewModel::clearFilters
                )
            }

            composable(Routes.Missed) {
                MissedDoseScreen(
                    onMarkTakenNow = { navController.popBackStack() },
                    onSkip = { navController.popBackStack() },
                    onDetails = { navController.navigate(Routes.Medications) }
                )
            }

            composable(
                route = "${Routes.AppLock}?${Routes.ForceLockArg}={${Routes.ForceLockArg}}",
                arguments = listOf(
                    navArgument(Routes.ForceLockArg) {
                        type = NavType.BoolType
                        defaultValue = true
                    }
                )
            ) { entry ->
                val forceLock = entry.arguments?.getBoolean(Routes.ForceLockArg) ?: true
                val viewModel: AppLockViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                AppLockScreen(
                    state = state,
                    forceLock = forceLock,
                    onSubmit = { pin ->
                        viewModel.submit(pin) {
                            shouldLockOnNextForeground = false
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val labelResId: Int,
    val icon: ImageVector
)

@Composable
private fun GlassBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onItemClick: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val containerShape = RoundedCornerShape(28.dp)
    val containerColor = if (isDark) Color(0xE6191A1C) else Color(0xE6F6F6F8)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.10f)
    val activeColor = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val inactiveColor = Color(0xFF8E8E93)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp)
            .shadow(18.dp, shape = containerShape, clip = false)
            .clip(containerShape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = containerShape)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(dividerColor)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val label = stringResource(item.labelResId)
                val selected = currentRoute == item.route
                val tint by animateColorAsState(
                    targetValue = if (selected) activeColor else inactiveColor,
                    label = "ios_tab_tint"
                )
                val yOffset by animateDpAsState(
                    targetValue = if (selected) (-1).dp else 0.dp,
                    label = "ios_tab_offset"
                )
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onItemClick(item.route) }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .offset(y = yOffset)
                            .wrapContentHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier.size(23.dp)
                        )
                        Text(
                            text = label,
                            color = tint,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun sharePdfReport(context: android.content.Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    val chooserIntent = Intent.createChooser(shareIntent, null).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    context.startActivity(chooserIntent)
}

private fun openPdfReport(context: android.content.Context, uri: Uri) {
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    runCatching {
        context.startActivity(viewIntent)
    }.recoverCatching {
        if (it is ActivityNotFoundException) {
            val chooserIntent = Intent.createChooser(viewIntent, null).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooserIntent)
        } else {
            throw it
        }
    }
}
