package ua.alert.phone.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.alert.shared.RegionNode
import ua.alert.shared.SelectedRegionConfig

@Composable
fun RegionPickerDialog(
    states: List<RegionNode>,
    onDismiss: () -> Unit,
    onRegionSelected: (SelectedRegionConfig) -> Unit
) {
    var selectedState by remember { mutableStateOf<RegionNode?>(null) }
    var selectedDistrict by remember { mutableStateOf<RegionNode?>(null) }

    val stepTitle = when {
        selectedState == null -> "1. Оберіть область"
        selectedDistrict == null -> "2. Оберіть район (${selectedState?.regionName})"
        else -> "3. Оберіть громаду (${selectedDistrict?.regionName})"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1F2023),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE4E4E7),
        title = {
            Text(text = stepTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                when {
                    selectedState == null -> {
                        items(states) { state ->
                            RegionListRow(
                                label = state.regionName,
                                onClick = {
                                    if (state.regionChildIds.isEmpty()) {
                                        onRegionSelected(
                                            SelectedRegionConfig(
                                                stateId = state.regionId,
                                                stateName = state.regionName
                                            )
                                        )
                                    } else {
                                        selectedState = state
                                    }
                                }
                            )
                        }
                    }

                    selectedDistrict == null -> {
                        val state = selectedState!!
                        item {
                            RegionListRow(
                                label = "Уся ${state.regionName}",
                                isHighlight = true,
                                onClick = {
                                    onRegionSelected(
                                        SelectedRegionConfig(
                                            stateId = state.regionId,
                                            stateName = state.regionName
                                        )
                                    )
                                }
                            )
                        }
                        items(state.regionChildIds) { district ->
                            RegionListRow(
                                label = district.regionName,
                                onClick = {
                                    if (district.regionChildIds.isEmpty()) {
                                        onRegionSelected(
                                            SelectedRegionConfig(
                                                stateId = state.regionId,
                                                stateName = state.regionName,
                                                districtId = district.regionId,
                                                districtName = district.regionName
                                            )
                                        )
                                    } else {
                                        selectedDistrict = district
                                    }
                                }
                            )
                        }
                    }

                    else -> {
                        val state = selectedState!!
                        val district = selectedDistrict!!
                        item {
                            RegionListRow(
                                label = "Увесь ${district.regionName}",
                                isHighlight = true,
                                onClick = {
                                    onRegionSelected(
                                        SelectedRegionConfig(
                                            stateId = state.regionId,
                                            stateName = state.regionName,
                                            districtId = district.regionId,
                                            districtName = district.regionName
                                        )
                                    )
                                }
                            )
                        }
                        items(district.regionChildIds) { community ->
                            RegionListRow(
                                label = community.regionName,
                                onClick = {
                                    onRegionSelected(
                                        SelectedRegionConfig(
                                            stateId = state.regionId,
                                            stateName = state.regionName,
                                            districtId = district.regionId,
                                            districtName = district.regionName,
                                            communityId = community.regionId,
                                            communityName = community.regionName
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedState != null) {
                TextButton(onClick = {
                    if (selectedDistrict != null) {
                        selectedDistrict = null
                    } else {
                        selectedState = null
                    }
                }) {
                    Text("Назад", color = Color(0xFFEF4444))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати", color = Color(0xFFA1A1AA))
            }
        }
    )
}

@Composable
private fun RegionListRow(
    label: String,
    isHighlight: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (isHighlight) Color(0xFFEF4444) else Color.White,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF71717A)
            )
        }
        HorizontalDivider(color = Color(0xFF27272A))
    }
}
