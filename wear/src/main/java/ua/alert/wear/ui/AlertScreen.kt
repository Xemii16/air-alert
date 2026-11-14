package ua.alert.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import ua.alert.wear.ui.theme.AirAlertWearTheme
import ua.alert.wear.ui.theme.WearExpressivePolygonBadge
import ua.alert.wear.ui.theme.WearIcons
import ua.alert.wear.ui.theme.rememberWearAlertPalette

@Composable
fun AlertScreen(
    alert: AlertEvent,
    onDismiss: () -> Unit
) {
    val palette = rememberWearAlertPalette(alert.dangerLevel)
    val isLargeWatch = LocalConfiguration.current.screenWidthDp >= 225
    val scrollState = rememberScrollState()
    val scrollInfoProvider = remember(scrollState) { ScrollInfoProvider(scrollState) }

    AirAlertWearTheme {
        AppScaffold(timeText = { TimeText() }) {
            ScreenScaffold(
                scrollInfoProvider = scrollInfoProvider
            ) { contentPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(palette.centerGlow, Color.Black),
                                radius = 420f
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(
                                top = contentPadding.calculateTopPadding() + 4.dp,
                                bottom = contentPadding.calculateBottomPadding() + 10.dp,
                                start = if (isLargeWatch) 18.dp else 14.dp,
                                end = if (isLargeWatch) 18.dp else 14.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        WearExpressivePolygonBadge(
                            vertices = if (alert.dangerLevel == DangerLevel.RED) 10 else 8,
                            color = palette.badgeColor,
                            modifier = Modifier.size(if (isLargeWatch) 46.dp else 38.dp)
                        ) {
                            Icon(
                                imageVector = if (alert.dangerLevel == DangerLevel.YELLOW) {
                                    WearIcons.Warning
                                } else {
                                    WearIcons.AlertActive
                                },
                                contentDescription = null,
                                tint = palette.onBadge,
                                modifier = Modifier.size(if (isLargeWatch) 24.dp else 20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = alert.title,
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
                            text = alert.region,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        WearActiveReasonsList(alert = alert)

                        Spacer(modifier = Modifier.height(8.dp))

                        WearDismissButton(
                            palette = palette,
                            onClick = onDismiss,
                            isLargeWatch = isLargeWatch
                        )
                    }
                }
            }
        }
    }
}
