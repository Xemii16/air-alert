package ua.alert.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.alert.phone.network.BackendClient
import ua.alert.phone.schedule.ScheduleManager
import ua.alert.phone.service.AirAlertFirebaseMessagingService
import ua.alert.phone.service.PhoneWearMessageSender
import ua.alert.shared.AlertEvent
import ua.alert.shared.DangerLevel
import ua.alert.shared.ThreatType

@OptIn(ExperimentalMaterial3Api::class)
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

    if (showScheduleDialog) {
        ScheduleDialog(
            initialConfig = scheduleConfig,
            onDismiss = { showScheduleDialog = false },
            onConfirm = { updated ->
                scheduleManager.saveSchedule(updated)
                coroutineScope.launch {
                    messageSender.syncSchedule(updated)
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
            containerColor = Color(0xFF1F2023),
            titleContentColor = Color.White,
            title = { Text("Адреса Spring Boot сервера") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Вкажіть URL вашого Spring Boot Webhook сервера:",
                        color = Color(0xFFA1A1AA),
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = draftUrl,
                        onValueChange = { draftUrl = it },
                        singleLine = true,
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
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD92D20))
                ) {
                    Text("Зберегти")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackendDialog = false }) {
                    Text("Скасувати", color = Color(0xFFA1A1AA))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Повітряна тривога • Wear OS",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isAlertActive) Color(0xFFB91C1C) else Color(0xFF18191B)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF18191B))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. REGION SELECTOR HEADER (Official com.ukrainealarm layout)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF232428)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRegionPicker = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ОБРАНИЙ РЕГІОН",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = selectedRegion.activeRegionName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (selectedRegion.fullBreadcrumb != selectedRegion.activeRegionName) {
                            Text(
                                text = selectedRegion.fullBreadcrumb,
                                color = Color(0xFF9CA3AF),
                                fontSize = 12.sp
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showRegionPicker = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditLocationAlt,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Змінити", color = Color.White)
                    }
                }
            }

            // 2. CENTRAL STATUS EMBLEM (Matching com.ukrainealarm concentric circle UI)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isAlertActive) Color(0xFFC81E1E) else Color(0xFF232428)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(136.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val ringColor = if (isAlertActive) Color.White else Color(0xFF3F3F46)
                            drawCircle(color = ringColor.copy(alpha = 0.14f), radius = size.minDimension / 2f)
                            drawCircle(color = ringColor.copy(alpha = 0.25f), radius = size.minDimension / 2.6f)
                            drawCircle(color = ringColor.copy(alpha = 0.40f), radius = size.minDimension / 3.6f)
                        }
                        Icon(
                            imageVector = if (isAlertActive) Icons.Default.Campaign else Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(46.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isAlertActive) {
                            (currentAlert?.title ?: "ПОВІТРЯНА ТРИВОГА!").uppercase()
                        } else {
                            "НЕМАЄ ТРИВОГИ"
                        },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isAlertActive) {
                            currentAlert?.text ?: "Негайно прямуйте до найближчого укриття!"
                        } else {
                            "Сповіщення надходять через Webhook API на ваш годинник"
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 3. STOP WATCH VIBRATION BUTTON
            Button(
                onClick = {
                    coroutineScope.launch {
                        val ok = messageSender.sendStopAlert()
                        AirAlertFirebaseMessagingService.updateCurrentAlertState(null)
                        statusMessage = if (ok) {
                            "Сигнал зупинки вібрації надіслано на годинник"
                        } else {
                            "Стан скинуто (годинник не підключено)"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD92D20),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.StopCircle,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "ЗУПИНИТИ ТРИВОГУ НА ГОДИННИКУ",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = Color(0xFF4ADE80),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // 4. ACTIVE WORKING HOURS SCHEDULE CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF232428)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Робочі години на годиннику",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Switch(
                            checked = scheduleConfig.isEnabled,
                            onCheckedChange = { enabled ->
                                val updated = scheduleConfig.copy(isEnabled = enabled)
                                scheduleManager.saveSchedule(updated)
                                coroutineScope.launch {
                                    messageSender.syncSchedule(updated)
                                }
                            }
                        )
                    }

                    Text(
                        text = if (scheduleConfig.isEnabled) {
                            "Годинник вібруватиме лише з %02d:%02d до %02d:%02d".format(
                                scheduleConfig.startHour,
                                scheduleConfig.startMinute,
                                scheduleConfig.endHour,
                                scheduleConfig.endMinute
                            )
                        } else {
                            "Цілодобово (24/7). Сильна вібрація на годиннику при кожній тривозі."
                        },
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp
                    )

                    if (scheduleConfig.isEnabled) {
                        FilledTonalButton(
                            onClick = { showScheduleDialog = true },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Налаштувати час")
                        }
                    }
                }
            }

            // 5. WATCH & BACKEND CONNECTION CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF232428)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (connectedNodes.isNotEmpty()) Color(0xFF22C55E) else Color(0xFFEF4444),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (connectedNodes.isNotEmpty()) {
                                    "Годинник підключено: ${connectedNodes.joinToString(", ")}"
                                } else {
                                    "Годинник Wear OS не підключено"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Режим: тільки сильна вібрація на годиннику (без звуку)",
                                color = Color(0xFF9CA3AF),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Spring Boot Webhook Сервер",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = backendUrl,
                                color = Color(0xFF9CA3AF),
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = { showBackendDialog = true }) {
                            Text("Змінити URL", color = Color(0xFFEF4444))
                        }
                    }
                }
            }

            // 6. SIMULATOR CARD (Test com.ukrainealarm screens on Watch)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF232428)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Тестування тривог на годиннику",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Button(
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
                                statusMessage = "Надіслано: Повітряна тривога (${selectedRegion.activeRegionName})"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC81E1E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Тест: ПОВІТРЯНА ТРИВОГА (Червона)")
                    }

                    Button(
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
                                statusMessage = "Надіслано: Загроза / Хімічна небезпека (Жовта)"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Тест: ПОПЕРЕДЖЕННЯ / ЗАГРОЗА (Жовта)", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Button(
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
                                statusMessage = "Надіслано: Відбій тривоги (${selectedRegion.activeRegionName})"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Тест: ВІДБІЙ ТРИВОГИ (Зелена)")
                    }
                }
            }
        }
    }
}
