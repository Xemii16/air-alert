package ua.alert.wear.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import ua.alert.shared.AlertEvent
import ua.alert.shared.DangerLevel
import ua.alert.shared.SelectedRegionConfig
import ua.alert.wear.service.AlertAlarmService
import ua.alert.wear.ui.theme.AirAlertWearTheme
import ua.alert.wear.ui.theme.WearExpressivePolygonBadge
import ua.alert.wear.ui.theme.WearIcons
import ua.alert.wear.ui.theme.rememberWearAlertPalette

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AirAlertWearTheme {
                WearAlertStatusScreen()
            }
        }
    }
}

/**
 * Lightweight, zero-lag Wear OS M3 Expressive screen showing ONLY the current
 * alert status for the synced region (Requirements #8, #9, #11).
 */
@Composable
fun WearAlertStatusScreen() {
    val context = LocalContext.current
    val isRinging by AlertAlarmService.isRinging.collectAsState()
    val activeAlert by AlertAlarmService.activeAlert.collectAsState()
    val isLargeWatch = LocalConfiguration.current.screenWidthDp >= 225

    val prefs = remember(context) {
        context.getSharedPreferences("wear_schedule_prefs", Context.MODE_PRIVATE)
    }
    var isPhoneConfigured by remember {
        mutableStateOf(readIsPhoneConfigured(prefs))
    }
    var regionName by remember {
        mutableStateOf(readRegionName(prefs))
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            when (key) {
                "synced_region_json" -> {
                    regionName = readRegionName(sp)
                    isPhoneConfigured = readIsPhoneConfigured(sp)
                }
                "is_phone_configured" -> {
                    isPhoneConfigured = readIsPhoneConfigured(sp)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val isAlertActive = activeAlert?.isActive == true

    if (!isPhoneConfigured && !isRinging && !isAlertActive) {
        AppScaffold(timeText = { TimeText() }) {
            ScreenScaffold { contentPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    Color.Black
                                ),
                                radius = 380f
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = contentPadding.calculateTopPadding() + 6.dp,
                                bottom = contentPadding.calculateBottomPadding() + 6.dp,
                                start = if (isLargeWatch) 20.dp else 14.dp,
                                end = if (isLargeWatch) 20.dp else 14.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        WearExpressivePolygonBadge(
                            vertices = 8,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(if (isLargeWatch) 56.dp else 46.dp)
                        ) {
                            Icon(
                                imageVector = WearIcons.PhoneSetup,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(if (isLargeWatch) 26.dp else 22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Налаштуйте на телефоні",
                            style = if (isLargeWatch) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.titleSmall
                            },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Відкрийте застосунок на телефоні, щоб підключити сервер",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        return
    }

    val statusDanger = if (isAlertActive) {
        activeAlert?.dangerLevel ?: DangerLevel.RED
    } else {
        DangerLevel.GREEN
    }
    val palette = rememberWearAlertPalette(statusDanger)
    val scrollState = rememberScrollState()
    val scrollInfoProvider = remember(scrollState) { ScrollInfoProvider(scrollState) }

    val statusBody: @Composable (PaddingValues) -> Unit = { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(palette.centerGlow.copy(alpha = 0.72f), Color.Black),
                        radius = 380f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        top = contentPadding.calculateTopPadding() + 6.dp,
                        bottom = contentPadding.calculateBottomPadding() + 10.dp,
                        start = if (isLargeWatch) 20.dp else 14.dp,
                        end = if (isLargeWatch) 20.dp else 14.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                WearExpressivePolygonBadge(
                    vertices = when {
                        isAlertActive && statusDanger == DangerLevel.RED -> 10
                        isAlertActive && statusDanger == DangerLevel.YELLOW -> 8
                        else -> 0
                    },
                    color = palette.badgeColor,
                    modifier = Modifier.size(if (isLargeWatch) 48.dp else 40.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isAlertActive && statusDanger == DangerLevel.YELLOW -> WearIcons.Warning
                            isAlertActive -> WearIcons.AlertActive
                            else -> WearIcons.ShieldCheck
                        },
                        contentDescription = null,
                        tint = palette.onBadge,
                        modifier = Modifier.size(if (isLargeWatch) 24.dp else 20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = activeAlert?.takeIf { it.isActive }?.title ?: "Тривоги немає",
                    style = if (isLargeWatch) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    color = palette.titleColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = resolveDisplayRegion(activeAlert, regionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                val currentActive = activeAlert
                if (isAlertActive && currentActive != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    WearActiveReasonsList(alert = currentActive)
                }

                if (isRinging || isAlertActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    WearDismissButton(
                        palette = palette,
                        onClick = { AlertAlarmService.dismissNotificationFromWatch(context) },
                        isLargeWatch = isLargeWatch
                    )
                }
            }
        }
    }

    AppScaffold(timeText = { TimeText() }) {
        if (isRinging || isAlertActive) {
            ScreenScaffold(
                scrollInfoProvider = scrollInfoProvider
            ) { contentPadding ->
                statusBody(contentPadding)
            }
        } else {
            ScreenScaffold { contentPadding ->
                statusBody(contentPadding)
            }
        }
    }
}

private fun readIsPhoneConfigured(prefs: SharedPreferences): Boolean {
    return if (prefs.contains("is_phone_configured")) {
        prefs.getBoolean("is_phone_configured", false)
    } else {
        prefs.contains("synced_region_json")
    }
}

private fun readRegionName(prefs: SharedPreferences): String {
    val raw = prefs.getString("synced_region_json", null) ?: return SelectedRegionConfig().activeRegionName
    return SelectedRegionConfig.fromJson(raw).activeRegionName
}

private fun resolveDisplayRegion(activeAlert: AlertEvent?, fallbackRegion: String): String {
    val alertRegion = activeAlert?.region?.takeIf { it.isNotBlank() }
    return if (activeAlert?.isActive == true && alertRegion != null) {
        alertRegion
    } else {
        fallbackRegion
    }
}
