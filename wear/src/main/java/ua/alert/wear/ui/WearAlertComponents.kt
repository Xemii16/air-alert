package ua.alert.wear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import ua.alert.shared.ActiveAlertLevel
import ua.alert.shared.AlertEvent
import ua.alert.shared.DangerLevel
import ua.alert.wear.ui.theme.WearIcons
import ua.alert.wear.ui.theme.WearSemanticPalette
import kotlin.math.abs

/**
 * Prominent pill button shown directly inside Wear alert/status screens to stop vibration
 * and discard the active notification without relying on edge scrolling.
 */
@Composable
fun WearDismissButton(
    palette: WearSemanticPalette,
    onClick: () -> Unit,
    isLargeWatch: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.buttonContainer,
            contentColor = palette.buttonContent
        ),
        contentPadding = PaddingValues(
            horizontal = if (isLargeWatch) 18.dp else 14.dp,
            vertical = if (isLargeWatch) 10.dp else 8.dp
        ),
        modifier = modifier
            .fillMaxWidth(0.84f)
            .heightIn(min = if (isLargeWatch) 44.dp else 40.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = WearIcons.DismissClose,
                contentDescription = "Скинути сповіщення",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Скинути",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Displays every active (alertLevel + reason) entry independently on Wear OS without collapsing
 * distinct threats, highlighting Red threats ahead of Yellow ones.
 */
@Composable
fun WearActiveReasonsList(
    alert: AlertEvent,
    modifier: Modifier = Modifier
) {
    val entries = remember(alert) {
        val snapshotItems = alert.activeAlertLevels
            .filter { it.reason.isNotBlank() }
            .sortedByDescending { it.alertLevel == DangerLevel.RED }
            .distinctBy { "${it.alertLevel.name}|${it.reason.trim()}" }
        if (snapshotItems.isNotEmpty()) {
            snapshotItems
        } else {
            alert.eventReason.trim().takeIf { it.isNotEmpty() }?.let { reason ->
                listOf(ActiveAlertLevel(alertLevel = alert.dangerLevel, reason = reason, alarmType = alert.alarmType))
            } ?: emptyList()
        }
    }
    if (entries.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (entry in entries) {
            val dotColor = if (entry.alertLevel == DangerLevel.RED) {
                Color(0xFFFF5252)
            } else {
                Color(0xFFFFD54F)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(dotColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = entry.reason,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Allows dismissing the full-screen alert by swiping either left or right anywhere on screen.
 */
@Composable
fun SwipeLeftOrRightToDismiss(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val commitThresholdPx = remember(density) { with(density) { 56.dp.toPx() } }
    val instantDismissPx = remember(density) { with(density) { 108.dp.toPx() } }
    val fadeRangePx = remember(density) { with(density) { 180.dp.toPx() } }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onDismiss) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        if (dismissed) return@detectHorizontalDragGestures
                        change.consume()
                        val updatedOffset = offsetX.value + dragAmount
                        if (abs(updatedOffset) >= instantDismissPx) {
                            dismissed = true
                            onDismiss()
                        } else {
                            scope.launch {
                                offsetX.snapTo(updatedOffset)
                            }
                        }
                    },
                    onDragEnd = {
                        if (dismissed) return@detectHorizontalDragGestures
                        if (abs(offsetX.value) >= commitThresholdPx) {
                            dismissed = true
                            onDismiss()
                        } else {
                            scope.launch {
                                offsetX.animateTo(0f, animationSpec = spring())
                            }
                        }
                    },
                    onDragCancel = {
                        if (dismissed) return@detectHorizontalDragGestures
                        scope.launch {
                            offsetX.animateTo(0f, animationSpec = spring())
                        }
                    }
                )
            }
            .graphicsLayer {
                translationX = offsetX.value
                alpha = (1f - (abs(offsetX.value) / fadeRangePx) * 0.5f).coerceIn(0.4f, 1f)
            }
    ) {
        content()
    }
}
