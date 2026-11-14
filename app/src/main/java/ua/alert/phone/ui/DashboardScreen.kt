package ua.alert.phone.ui

import android.graphics.Matrix
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.alert.phone.network.BackendClient
import ua.alert.phone.network.RegionsSourceState
import ua.alert.phone.service.AirAlertFirebaseMessagingService
import ua.alert.phone.service.PhoneWearMessageSender
import ua.alert.phone.ui.theme.SemanticAlertPalette
import ua.alert.phone.ui.theme.rememberSemanticAlertPalette
import ua.alert.shared.ActiveAlertLevel
import ua.alert.shared.AlertEvent
import ua.alert.shared.DangerLevel
import ua.alert.shared.SelectedRegionConfig
import ua.alert.shared.ThreatType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val backendClient = remember { BackendClient.getInstance(context) }
    val wearSender = remember { PhoneWearMessageSender(context) }

    val isServerConfigured by backendClient.isServerConfigured.collectAsState()
    val selectedRegion by backendClient.selectedRegion.collectAsState()
    val backendUrl by backendClient.backendUrl.collectAsState()
    val apiToken by backendClient.apiToken.collectAsState()
    val regionsTree by backendClient.regionsTree.collectAsState()
    val regionsSource by backendClient.regionsSourceState.collectAsState()
    val currentAlert by AirAlertFirebaseMessagingService.currentAlertState.collectAsState()

    LaunchedEffect(isServerConfigured) {
        wearSender.syncSetupState(isServerConfigured)
    }

    if (!isServerConfigured) {
        ServerSetupScreen(
            initialUrl = backendUrl,
            initialToken = apiToken,
            onVerifyAndConnect = { rawUrl, token ->
                backendClient.verifyAndSaveServer(rawUrl, token)
            }
        )
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showRegionSheet by remember { mutableStateOf(false) }
    var isRefreshingStatus by remember { mutableStateOf(false) }
    var isVerifyingSettingsUrl by remember { mutableStateOf(false) }
    var settingsVerifyMessage by remember { mutableStateOf<String?>(null) }
    var settingsVerifySuccess by remember { mutableStateOf<Boolean?>(null) }
    var statusError by remember { mutableStateOf<String?>(null) }
    var lastStatusSyncMs by remember { mutableStateOf<Long?>(null) }
    var connectedNodesCount by remember { mutableIntStateOf(0) }
    var showServerUnavailableDialog by remember { mutableStateOf(false) }
    var hasDismissedUnavailableDialog by remember { mutableStateOf(false) }

    fun showFeedback(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    suspend fun refreshRegionStatus(
        showErrorToast: Boolean = false,
        forceDialog: Boolean = false
    ) {
        isRefreshingStatus = true
        val res = backendClient.fetchCurrentAlertStatus(selectedRegion)
        isRefreshingStatus = false
        res.fold(
            onSuccess = {
                statusError = null
                lastStatusSyncMs = System.currentTimeMillis()
                showServerUnavailableDialog = false
                hasDismissedUnavailableDialog = false
            },
            onFailure = {
                statusError = "Сервер недоступний"
                if (forceDialog || !hasDismissedUnavailableDialog) {
                    showServerUnavailableDialog = true
                }
            }
        )
    }

    LaunchedEffect(selectedRegion.activeRegionId, backendUrl, apiToken) {
        backendClient.ensureTopicSubscriptions(selectedRegion)
        backendClient.fetchRegionsFromBackend(forceRefresh = false)
        wearSender.syncSelectedRegion(selectedRegion)
        connectedNodesCount = wearSender.getConnectedNodes().size
        refreshRegionStatus(showErrorToast = false)
    }

    // Periodic live status polling + retry any pending FCM topic sync every 30 seconds
    LaunchedEffect(selectedRegion.activeRegionId, backendUrl) {
        while (true) {
            delay(30_000L)
            backendClient.syncPendingTopicSubscriptionsIfNeeded()
            refreshRegionStatus(showErrorToast = false)
        }
    }

    val effectiveDangerLevel = when {
        currentAlert != null && currentAlert?.isActive == true -> currentAlert?.dangerLevel
        currentAlert?.threatType == ThreatType.ALL_CLEAR -> DangerLevel.GREEN
        else -> DangerLevel.GREEN
    }
    val semanticPalette = rememberSemanticAlertPalette(dangerLevel = effectiveDangerLevel)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (selectedTab == 0) "Повітряна тривога" else "Налаштування",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    if (selectedTab == 0) {
                        FilledTonalIconButton(
                            onClick = { scope.launch { refreshRegionStatus(forceDialog = true) } },
                            enabled = !isRefreshingStatus
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Оновити статус"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Non-floating docked Material 3 NavigationBar (Requirement #1)
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = "Головна"
                        )
                    },
                    label = { Text("Головна") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Налаштування"
                        )
                    },
                    label = { Text("Налаштування") }
                )
            }
        }
    ) { innerPadding ->
        if (selectedTab == 0) {
            MainAlertStatusPage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                selectedRegion = selectedRegion,
                currentAlert = currentAlert,
                semanticPalette = semanticPalette,
                isRefreshing = isRefreshingStatus,
                statusError = statusError,
                lastSyncMs = lastStatusSyncMs,
                onChooseRegionClick = {
                    showRegionSheet = true
                    scope.launch { backendClient.fetchRegionsFromBackend(forceRefresh = true) }
                }
            )
        } else {
            SettingsPage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                savedUrl = backendUrl,
                savedApiToken = apiToken,
                regionsSource = regionsSource,
                connectedNodesCount = connectedNodesCount,
                isVerifyingServer = isVerifyingSettingsUrl,
                verifyMessage = settingsVerifyMessage,
                verifySuccess = settingsVerifySuccess,
                onSaveAndSync = { newUrl, newToken ->
                    scope.launch {
                        isVerifyingSettingsUrl = true
                        settingsVerifyMessage = null
                        val res = backendClient.verifyAndSaveServer(newUrl, newToken)
                        isVerifyingSettingsUrl = false
                        res.fold(
                            onSuccess = { pingInfo ->
                                settingsVerifySuccess = true
                                settingsVerifyMessage = "Підтверджено (${pingInfo.latencyMs} мс, ${pingInfo.regionsCount} регіонів)"
                                connectedNodesCount = wearSender.getConnectedNodes().size
                                refreshRegionStatus(showErrorToast = false)
                                showFeedback("Сервер підтверджено та збережено!")
                            },
                            onFailure = { err ->
                                settingsVerifySuccess = false
                                val msg = err.localizedMessage ?: "Перевірку сервера не пройдено"
                                settingsVerifyMessage = msg
                                showFeedback(msg)
                            }
                        )
                    }
                },
                onRefreshRegionsOnly = {
                    scope.launch {
                        val loaded = backendClient.fetchRegionsFromBackend(forceRefresh = true)
                        connectedNodesCount = wearSender.getConnectedNodes().size
                        val err = backendClient.regionsSourceState.value.errorMessage
                        showFeedback(err ?: "Список регіонів оновлено (${loaded.size} областей)")
                    }
                }
            )
        }
    }

    if (showServerUnavailableDialog) {
        AlertDialog(
            onDismissRequest = {
                showServerUnavailableDialog = false
                hasDismissedUnavailableDialog = true
            },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(text = "Сервер недоступний")
            },
            text = {
                Text(
                    text = "Не вдалося з'єднатися з сервером тривог. Оберіть інший сервер у налаштуванні."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showServerUnavailableDialog = false
                        hasDismissedUnavailableDialog = false
                        backendClient.resetServerSetup()
                    }
                ) {
                    Text("Обрати інший сервер")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showServerUnavailableDialog = false
                        hasDismissedUnavailableDialog = false
                        scope.launch { refreshRegionStatus(forceDialog = true) }
                    }
                ) {
                    Text("Повторити")
                }
            }
        )
    }

    if (showRegionSheet) {
        RegionPickerDialog(
            currentRegion = selectedRegion,
            regionsTree = regionsTree,
            isLoading = regionsSource.isLoading,
            onDismiss = { showRegionSheet = false },
            onRegionSelected = { newConfig ->
                showRegionSheet = false
                scope.launch {
                    isRefreshingStatus = true
                    backendClient.updateSelectedRegion(newConfig)
                    isRefreshingStatus = false
                    showFeedback("Обрано регіон: ${newConfig.activeRegionName}")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainAlertStatusPage(
    modifier: Modifier = Modifier,
    selectedRegion: SelectedRegionConfig,
    currentAlert: AlertEvent?,
    semanticPalette: SemanticAlertPalette,
    isRefreshing: Boolean,
    statusError: String?,
    lastSyncMs: Long?,
    onChooseRegionClick: () -> Unit
) {
    val isActive = currentAlert?.isActive == true
    val dangerLevel = if (isActive) currentAlert?.dangerLevel else DangerLevel.GREEN
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val animatedContainer by animateColorAsState(
        targetValue = semanticPalette.container,
        label = "HeroContainerColor"
    )
    val animatedOnContainer by animateColorAsState(
        targetValue = semanticPalette.onContainer,
        label = "HeroOnContainerColor"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Main focused area: Selected region + Current alert status (Requirement #2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(36.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = animatedContainer,
                    contentColor = animatedOnContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Selected Region Chip
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = animatedOnContainer.copy(alpha = 0.12f),
                        contentColor = animatedOnContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = selectedRegion.activeRegionName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (selectedRegion.activeRegionName != selectedRegion.fullBreadcrumb) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = selectedRegion.fullBreadcrumb,
                            style = MaterialTheme.typography.bodySmall,
                            color = animatedOnContainer.copy(alpha = 0.78f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // Expressive Semantic Danger Shape Emblem
                    Box(contentAlignment = Alignment.Center) {
                        ExpressiveShapeEmblem(
                            dangerLevel = dangerLevel,
                            badgeColor = semanticPalette.badgeContainer,
                            modifier = Modifier.size(120.dp)
                        ) {
                            Icon(
                                imageVector = when (dangerLevel) {
                                    DangerLevel.RED -> Icons.Rounded.NotificationsActive
                                    DangerLevel.YELLOW -> Icons.Rounded.Warning
                                    DangerLevel.GREEN, null -> Icons.Rounded.Shield
                                },
                                contentDescription = null,
                                tint = semanticPalette.onBadgeContainer,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Danger Level Pill (Yellow / Red Danger / Safe)
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = semanticPalette.badgeContainer,
                        contentColor = semanticPalette.onBadgeContainer
                    ) {
                        Text(
                            text = when (dangerLevel) {
                                DangerLevel.RED -> "ЧЕРВОНИЙ РІВЕНЬ НЕБЕЗПЕКИ"
                                DangerLevel.YELLOW -> "ЖОВТИЙ РІВЕНЬ НЕБЕЗПЕКИ"
                                DangerLevel.GREEN, null -> "БЕЗПЕЧНО"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Current Alarm Status Title
                    val activeAlert = currentAlert?.takeIf { it.isActive }
                    Text(
                        text = activeAlert?.title ?: "Тривоги немає",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = animatedOnContainer,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = activeAlert?.text ?: "У вашому регіоні наразі все спокійно",
                        style = MaterialTheme.typography.bodyLarge,
                        color = animatedOnContainer.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center
                    )

                    val activeLevels = activeAlert?.activeAlertLevels.orEmpty()
                    if (activeLevels.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        ActiveLevelsBreakdownCard(
                            levels = activeLevels,
                            contentColor = animatedOnContainer
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    if (isRefreshing) {
                        LoadingIndicator(
                            modifier = Modifier.size(28.dp),
                            color = animatedOnContainer
                        )
                    } else if (statusError != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = animatedOnContainer.copy(alpha = 0.72f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = statusError,
                                style = MaterialTheme.typography.labelMedium,
                                color = animatedOnContainer.copy(alpha = 0.72f),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (lastSyncMs != null) {
                        Text(
                            text = "Оновлено о ${timeFormatter.format(Date(lastSyncMs))}",
                            style = MaterialTheme.typography.labelMedium,
                            color = animatedOnContainer.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big M3 Expressive button right near the non-floating nav bar (Requirement #3)
        Button(
            onClick = onChooseRegionClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Обрати інший регіон",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsPage(
    modifier: Modifier = Modifier,
    savedUrl: String,
    savedApiToken: String,
    regionsSource: RegionsSourceState,
    connectedNodesCount: Int,
    isVerifyingServer: Boolean,
    verifyMessage: String?,
    verifySuccess: Boolean?,
    onSaveAndSync: (String, String) -> Unit,
    onRefreshRegionsOnly: () -> Unit
) {
    var urlDraft by remember(savedUrl) { mutableStateOf(savedUrl) }
    var tokenDraft by remember(savedApiToken) { mutableStateOf(savedApiToken) }

    val isOfficialApiSelected = urlDraft.contains("api.ukrainealarm.com", ignoreCase = true)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Сервер тривог і регіонів",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Регіони та статуси завантажуються з UkraineAlarm API (api.ukrainealarm.com) напряму або через ваш бекенд-сервер.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick M3 Expressive Connected ButtonGroup presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    FilledTonalToggleButton(
                        checked = !isOfficialApiSelected,
                        onCheckedChange = { if (it) urlDraft = "http://10.0.2.2:8080" },
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Бекенд",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FilledTonalToggleButton(
                        checked = isOfficialApiSelected,
                        onCheckedChange = { if (it) urlDraft = BackendClient.UKRAINE_ALARM_API_URL },
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "UkraineAlarm API",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    label = { Text("URL сервера тривог") },
                    placeholder = { Text("https://api.ukrainealarm.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tokenDraft,
                    onValueChange = { tokenDraft = it },
                    label = { Text("API-токен UkraineAlarm") },
                    placeholder = { Text("Потрібен лише для прямого api.ukrainealarm.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { onSaveAndSync(urlDraft, tokenDraft) },
                    enabled = !isVerifyingServer && urlDraft.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    if (isVerifyingServer) {
                        LoadingIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isVerifyingServer) "Перевірка /api/v1/ping..." else "Перевірити та зберегти",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (verifyMessage != null) {
                    val isOk = verifySuccess == true
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isOk) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        contentColor = if (isOk) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = verifyMessage,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Синхронізація",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (regionsSource.isLiveFromApi) {
                            Icons.Rounded.CloudDone
                        } else {
                            Icons.Rounded.CheckCircle
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (regionsSource.isLiveFromApi) {
                                "Довідник регіонів з API"
                            } else {
                                "Локальний довідник регіонів"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Областей у списку: ${regionsSource.stateCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (regionsSource.isLoading) {
                        LoadingIndicator(modifier = Modifier.size(28.dp))
                    }
                }

                if (regionsSource.errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = regionsSource.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (connectedNodesCount > 0) Icons.Rounded.Watch else Icons.Rounded.Close,
                        contentDescription = null,
                        tint = if (connectedNodesCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (connectedNodesCount > 0) {
                            "Годинник Wear OS підключено ($connectedNodesCount)"
                        } else {
                            "Годинник Wear OS не підключено"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onRefreshRegionsOnly,
                    enabled = !regionsSource.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Оновити регіони з API")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveShapeEmblem(
    dangerLevel: DangerLevel?,
    badgeColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val polygon: RoundedPolygon = remember(dangerLevel) {
        when (dangerLevel) {
            DangerLevel.RED -> MaterialShapes.SoftBurst
            DangerLevel.YELLOW -> MaterialShapes.Cookie9Sided
            DangerLevel.GREEN -> MaterialShapes.Sunny
            null -> MaterialShapes.Circle
        }
    }
    ExpressivePolygonBadge(
        polygon = polygon,
        badgeColor = badgeColor,
        modifier = modifier,
        content = content
    )
}

@Composable
private fun ActiveLevelsBreakdownCard(
    levels: List<ActiveAlertLevel>,
    contentColor: Color
) {
    val ordered = remember(levels) {
        levels.sortedWith(
            compareByDescending<ActiveAlertLevel> { it.alertLevel == DangerLevel.RED }
                .thenByDescending { it.createdAt }
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = contentColor.copy(alpha = 0.10f),
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (item in ordered) {
                val isRed = item.alertLevel == DangerLevel.RED
                val dotColor = if (isRed) Color(0xFFFF5252) else Color(0xFFFFD54F)
                val levelBadge = if (isRed) "Red" else "Yellow"
                val reasonLabel = item.reason.trim().ifEmpty {
                    ThreatType.fromApiType(item.alarmType).ukrainianLabel
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = dotColor,
                        modifier = Modifier.size(10.dp)
                    ) {}
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = reasonLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = dotColor.copy(alpha = 0.22f)
                    ) {
                        Text(
                            text = levelBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
