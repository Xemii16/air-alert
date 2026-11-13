package ua.alert.phone.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.alert.phone.network.BackendClient
import ua.alert.phone.schedule.ScheduleManager
import ua.alert.phone.service.AirAlertFirebaseMessagingService
import ua.alert.phone.service.PhoneWearMessageSender
import ua.alert.shared.AlertEvent
import ua.alert.shared.DangerLevel
import ua.alert.shared.ThreatType

// M3 Expressive Connected Group Shapes
private val GroupTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
private val GroupMiddleShape = RoundedCornerShape(8.dp)
private val GroupBottomShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    messageSender: PhoneWearMessageSender,
    scheduleManager: ScheduleManager,
    backendClient: BackendClient
) {
    val coroutineScope = rememberCoroutineScope()

    val scheduleConfig by scheduleManager.scheduleState.collectAsState()
    val selectedRegion by backendClient.selectedRegion.collectAsState()
    val regionsTree by backendClient.regionsTree.collectAsState()
    val backendUrl by backendClient.backendUrl.collectAsState()
    val currentAlert by AirAlertFirebaseMessagingService.currentAlertState.collectAsState()

    var connectedNodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showRegionPicker by remember { mutableStateOf(false) }
    var showBackendDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val isAlertActive = currentAlert?.isActive == true
    val isYellowAlert = isAlertActive && currentAlert?.dangerLevel == DangerLevel.YELLOW

    // Live elapsed timer when an alert is active
    var elapsedSeconds by remember(currentAlert?.timestamp, isAlertActive) {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(currentAlert?.timestamp, isAlertActive) {
        if (isAlertActive) {
            val startTs = currentAlert?.timestamp ?: System.currentTimeMillis()
            while (true) {
                elapsedSeconds = maxOf(0L, (System.currentTimeMillis() - startTs) / 1000L)
                delay(1000)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    LaunchedEffect(Unit) {
        backendClient.fetchRegionsFromBackend()
        backendClient.registerDeviceWithBackend(selectedRegion)
        messageSender.syncSelectedRegion(selectedRegion)
        while (true) {
            val nodes = messageSender.getConnectedNodes()
            connectedNodes = nodes.map { it.displayName }
            delay(4000)
        }
    }

    // Auto-clear transient status banner after 5 seconds
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(5000)
            statusMessage = null
        }
    }

    if (showScheduleDialog) {
        ScheduleDialog(
            initialConfig = scheduleConfig,
            onDismiss = { showScheduleDialog = false },
            onConfirm = { updated ->
                scheduleManager.saveSchedule(updated)
                coroutineScope.launch {
                    messageSender.syncSchedule(updated)
                    statusMessage = "Графік синхронізовано з годинником"
                }
                showScheduleDialog = false
            }
        )
    }

    if (showRegionPicker) {
        RegionPickerDialog(
            states = regionsTree,
            onDismiss = { showRegionPicker = false },
            onRegionSelected = { newConfig ->
                showRegionPicker = false
                coroutineScope.launch {
                    backendClient.updateSelectedRegion(newConfig)
                    messageSender.syncSelectedRegion(newConfig)
                    statusMessage = "Регіон оновлено: ${newConfig.activeRegionName}"
                }
            }
        )
    }

    if (showBackendDialog) {
        var draftUrl by remember { mutableStateOf(backendUrl) }
        AlertDialog(
            onDismissRequest = { showBackendDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color(0xFF1A1E29),
            titleContentColor = Color.White,
            title = {
                Text("Хмарний Webhook Сервер", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Вкажіть публічну HTTPS-адресу вашого Spring Boot сервера:",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = draftUrl,
                        onValueChange = { draftUrl = it },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        backendClient.saveBackendUrl(draftUrl)
                        showBackendDialog = false
                        coroutineScope.launch {
                            backendClient.fetchRegionsFromBackend()
                            backendClient.registerDeviceWithBackend()
                            statusMessage = "Сервер оновлено: ${draftUrl.trim()}"
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5449), contentColor = Color(0xFF3B0002))
                ) {
                    Text("Зберегти", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackendDialog = false }) {
                    Text("Скасувати", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    val heroGradientStart by animateColorAsState(
        targetValue = when {
            isYellowAlert -> Color(0xFFB45309)
            isAlertActive -> Color(0xFFC81E1E)
            else -> Color(0xFF1E2433)
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "heroStart"
    )
    val heroGradientEnd by animateColorAsState(
        targetValue = when {
            isYellowAlert -> Color(0xFF451A03)
            isAlertActive -> Color(0xFF450A0A)
            else -> Color(0xFF131722)
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "heroEnd"
    )

    Scaffold(
        containerColor = Color(0xFF0E1015)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. EXPRESSIVE TOP HEADER & LIVE STATUS PILLS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ПОВІТРЯНА ТРИВОГА",
                        color = Color(0xFFFF5449),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = "Wear OS Companion",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Live Watch Connection Pill
                val watchConnected = connectedNodes.isNotEmpty()
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (watchConnected) Color(0xFF064E3B) else Color(0xFF3F1D24),
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = if (watchConnected) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF43F5E).copy(alpha = 0.4f),
                        shape = RoundedCornerShape(50)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (watchConnected) Color(0xFF34D399) else Color(0xFFFB7185),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (watchConnected) connectedNodes.first() else "Немає годинника",
                            color = if (watchConnected) Color(0xFFA7F3D0) else Color(0xFFFECDD3),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2. M3 EXPRESSIVE HERO STATUS CARD (Scalloped Soft-Burst Emblem + Stop Control)
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(heroGradientStart, heroGradientEnd)
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (isAlertActive) Color.White.copy(alpha = 0.24f) else Color(0xFF2E3646),
                        shape = RoundedCornerShape(32.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Region Pill inside Hero
                    Surface(
                        onClick = { showRegionPicker = true },
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditLocationAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedRegion.activeRegionName,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Expressive Animated Scalloped Polygon / Concentric Emblem
                    ExpressiveStatusEmblem(
                        isAlertActive = isAlertActive,
                        isYellowAlert = isYellowAlert
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isAlertActive) {
                            (currentAlert?.title ?: "ПОВІТРЯНА ТРИВОГА!").uppercase()
                        } else {
                            "НЕМАЄ ТРИВОГИ"
                        },
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.5.sp
                    )

                    if (isAlertActive) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val mins = elapsedSeconds / 60
                        val secs = elapsedSeconds % 60
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.28f)
                        ) {
                            Text(
                                text = String.format(Locale.ROOT, "Триває %02d:%02d", mins, secs),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isAlertActive) {
                            currentAlert?.text ?: "Негайно прямуйте до найближчого укриття!"
                        } else {
                            "Небо чисте • Миттєва вібрація на годиннику при загрозі"
                        },
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Expressive Pill Button to Stop Watch Vibration Immediately
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val ok = messageSender.sendStopAlert()
                                AirAlertFirebaseMessagingService.updateCurrentAlertState(null)
                                statusMessage = if (ok) {
                                    "Вібрацію на годиннику зупинено"
                                } else {
                                    "Стан скинуто (годинник не в мережі)"
                                }
                            }
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAlertActive) Color.White else Color(0xFF283042),
                            contentColor = if (isAlertActive) Color(0xFFB91C1C) else Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ЗУПИНИТИ ТРИВОГУ НА ГОДИННИКУ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            }

            // Status Toast Banner
            AnimatedVisibility(
                visible = statusMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF064E3B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = statusMessage.orEmpty(),
                            color = Color(0xFFD1FAE5),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 3. M3 EXPRESSIVE CONNECTED GROUP: REGION, SCHEDULE & SERVER
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "НАЛАШТУВАННЯ МОНІТОРИНГУ",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )

                // Top Grouped Card: Region Hierarchy
                Surface(
                    shape = GroupTopShape,
                    color = Color(0xFF191D28),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GroupTopShape)
                        .clickable { showRegionPicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Регіон сповіщень",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = selectedRegion.activeRegionName,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (selectedRegion.fullBreadcrumb != selectedRegion.activeRegionName) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = selectedRegion.fullBreadcrumb,
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        FilledTonalButton(
                            onClick = { showRegionPicker = true },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF282F40),
                                contentColor = Color(0xFFFF897D)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditLocationAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Змінити", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                // Middle Grouped Card: Working Hours Schedule
                Surface(
                    shape = GroupMiddleShape,
                    color = Color(0xFF191D28),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFF252B3B), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Color(0xFFFF897D),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Робочі години на годиннику",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = if (scheduleConfig.isEnabled) {
                                            "Активно з %02d:%02d до %02d:%02d".format(
                                                scheduleConfig.startHour,
                                                scheduleConfig.startMinute,
                                                scheduleConfig.endHour,
                                                scheduleConfig.endMinute
                                            )
                                        } else {
                                            "Цілодобово (24/7) без обмежень"
                                        },
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Switch(
                                checked = scheduleConfig.isEnabled,
                                onCheckedChange = { enabled ->
                                    val updated = scheduleConfig.copy(isEnabled = enabled)
                                    scheduleManager.saveSchedule(updated)
                                    coroutineScope.launch {
                                        messageSender.syncSchedule(updated)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF3B0002),
                                    checkedTrackColor = Color(0xFFFF5449)
                                )
                            )
                        }

                        // Expressive Quick Preset Chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExpressivePresetChip(
                                label = "24/7 Цілодобово",
                                selected = !scheduleConfig.isEnabled,
                                onClick = {
                                    val updated = scheduleConfig.copy(isEnabled = false)
                                    scheduleManager.saveSchedule(updated)
                                    coroutineScope.launch { messageSender.syncSchedule(updated) }
                                }
                            )
                            ExpressivePresetChip(
                                label = "Ніч 22:00–07:00",
                                selected = scheduleConfig.isEnabled && scheduleConfig.startHour == 22 && scheduleConfig.endHour == 7,
                                onClick = {
                                    val updated = scheduleConfig.copy(
                                        isEnabled = true,
                                        startHour = 22,
                                        startMinute = 0,
                                        endHour = 7,
                                        endMinute = 0
                                    )
                                    scheduleManager.saveSchedule(updated)
                                    coroutineScope.launch { messageSender.syncSchedule(updated) }
                                }
                            )
                            ExpressivePresetChip(
                                label = "Власний графік…",
                                selected = scheduleConfig.isEnabled && !(scheduleConfig.startHour == 22 && scheduleConfig.endHour == 7),
                                onClick = { showScheduleDialog = true }
                            )
                        }
                    }
                }

                // Bottom Grouped Card: Cloud Server & Haptic Mode Info
                Surface(
                    shape = GroupBottomShape,
                    color = Color(0xFF191D28),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GroupBottomShape)
                        .clickable { showBackendDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF252B3B), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Webhook Сервер • Беззвучна вібрація",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = backendUrl,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        TextButton(onClick = { showBackendDialog = true }) {
                            Text("URL", color = Color(0xFFFF897D), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // 4. M3 EXPRESSIVE BENTO SIMULATOR TILES
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ТЕСТУВАННЯ ГОДИННИКА",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpressiveSimTile(
                        title = "Тривога",
                        subtitle = "Червоний",
                        icon = Icons.Default.Campaign,
                        containerColor = Color(0xFF7F1D1D),
                        accentColor = Color(0xFFFCA5A5),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            coroutineScope.launch {
                                val event = AlertEvent(
                                    regionId = selectedRegion.activeRegionId,
                                    threatType = ThreatType.AIR_RAID,
                                    dangerLevel = DangerLevel.RED,
                                    region = selectedRegion.activeRegionName,
                                    title = ThreatType.AIR_RAID.ukrainianLabel,
                                    text = ThreatType.AIR_RAID.instructionText,
                                    isActive = true
                                )
                                AirAlertFirebaseMessagingService.updateCurrentAlertState(event)
                                messageSender.sendAlertTrigger(event)
                                statusMessage = "Тест: Повітряна тривога (${selectedRegion.activeRegionName})"
                            }
                        }
                    )

                    ExpressiveSimTile(
                        title = "Загроза",
                        subtitle = "Жовтий",
                        icon = Icons.Default.WarningAmber,
                        containerColor = Color(0xFF713F12),
                        accentColor = Color(0xFFFDE047),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            coroutineScope.launch {
                                val event = AlertEvent(
                                    regionId = selectedRegion.activeRegionId,
                                    threatType = ThreatType.CHEMICAL,
                                    dangerLevel = DangerLevel.YELLOW,
                                    region = selectedRegion.activeRegionName,
                                    title = ThreatType.CHEMICAL.ukrainianLabel,
                                    text = ThreatType.CHEMICAL.instructionText,
                                    isActive = true
                                )
                                AirAlertFirebaseMessagingService.updateCurrentAlertState(event)
                                messageSender.sendAlertTrigger(event)
                                statusMessage = "Тест: Жовта загроза (${selectedRegion.activeRegionName})"
                            }
                        }
                    )

                    ExpressiveSimTile(
                        title = "Відбій",
                        subtitle = "Зелений",
                        icon = Icons.Default.CheckCircle,
                        containerColor = Color(0xFF064E3B),
                        accentColor = Color(0xFF6EE7B7),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            coroutineScope.launch {
                                val event = AlertEvent(
                                    regionId = selectedRegion.activeRegionId,
                                    threatType = ThreatType.ALL_CLEAR,
                                    dangerLevel = DangerLevel.GREEN,
                                    region = selectedRegion.activeRegionName,
                                    title = ThreatType.ALL_CLEAR.ukrainianLabel,
                                    text = ThreatType.ALL_CLEAR.instructionText,
                                    isActive = false
                                )
                                AirAlertFirebaseMessagingService.updateCurrentAlertState(event)
                                messageSender.sendAlertTrigger(event)
                                statusMessage = "Тест: Відбій тривоги (${selectedRegion.activeRegionName})"
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ExpressiveStatusEmblem(
    isAlertActive: Boolean,
    isYellowAlert: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressiveEmblem")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isAlertActive) 14000 else 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isAlertActive) 900 else 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val badgeColor = when {
        isYellowAlert -> Color(0xFFFACC15)
        isAlertActive -> Color.White
        else -> Color(0xFF34D399)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(132.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * pulseScale

            // Outer concentric ring
            drawCircle(
                color = badgeColor.copy(alpha = 0.10f),
                radius = baseRadius,
                center = center
            )

            // Rotating M3 Expressive 12-lobe scalloped soft-burst polygon
            rotate(degrees = rotation, pivot = center) {
                val path = createScallopedPolygonPath(
                    centerX = center.x,
                    centerY = center.y,
                    outerRadius = size.minDimension * 0.38f,
                    innerRadius = size.minDimension * 0.31f,
                    lobes = 12
                )
                drawPath(
                    path = path,
                    color = badgeColor.copy(alpha = 0.20f)
                )
            }

            // Inner core circle
            drawCircle(
                color = badgeColor.copy(alpha = if (isAlertActive) 0.28f else 0.18f),
                radius = size.minDimension * 0.24f,
                center = center
            )
        }

        Icon(
            imageVector = when {
                isYellowAlert -> Icons.Default.WarningAmber
                isAlertActive -> Icons.Default.Campaign
                else -> Icons.Default.Shield
            },
            contentDescription = null,
            tint = if (isAlertActive) Color.White else Color(0xFF34D399),
            modifier = Modifier.size(44.dp)
        )
    }
}

private fun createScallopedPolygonPath(
    centerX: Float,
    centerY: Float,
    outerRadius: Float,
    innerRadius: Float,
    lobes: Int
): Path {
    val path = Path()
    val steps = lobes * 8
    val meanRadius = (outerRadius + innerRadius) / 2f
    val amp = (outerRadius - innerRadius) / 2f

    for (i in 0..steps) {
        val theta = (2.0 * PI * i) / steps
        val r = meanRadius + amp * cos(lobes * theta).toFloat()
        val x = centerX + r * cos(theta).toFloat()
        val y = centerY + r * sin(theta).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

@Composable
private fun ExpressivePresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = if (selected) RoundedCornerShape(14.dp) else RoundedCornerShape(50),
        color = if (selected) Color(0xFFFF5449) else Color(0xFF242A3A)
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF3B0002) else Color(0xFFCBD5E1),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ExpressiveSimTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.22f), CircleShape)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subtitle,
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
