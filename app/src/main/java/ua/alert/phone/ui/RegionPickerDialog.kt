package ua.alert.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.alert.shared.RegionNode
import ua.alert.shared.SelectedRegionConfig

private data class FlatSearchMatch(
    val label: String,
    val subtitle: String,
    val config: SelectedRegionConfig
)

@Composable
fun RegionPickerDialog(
    states: List<RegionNode>,
    onDismiss: () -> Unit,
    onRegionSelected: (SelectedRegionConfig) -> Unit
) {
    var selectedState by remember { mutableStateOf<RegionNode?>(null) }
    var selectedDistrict by remember { mutableStateOf<RegionNode?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val stepNumber = when {
        selectedState == null -> 1
        selectedDistrict == null -> 2
        else -> 3
    }
    val stepTitle = when (stepNumber) {
        1 -> "Оберіть область"
        2 -> selectedState?.regionName ?: "Оберіть район"
        else -> selectedDistrict?.regionName ?: "Оберіть громаду"
    }

    // Build flattened search results when the user types in the search box
    val searchResults = remember(states, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            val matches = mutableListOf<FlatSearchMatch>()
            for (st in states) {
                if (st.regionName.contains(q, ignoreCase = true)) {
                    matches.add(
                        FlatSearchMatch(
                            label = st.regionName,
                            subtitle = "Область / Місто",
                            config = SelectedRegionConfig(stateId = st.regionId, stateName = st.regionName)
                        )
                    )
                }
                for (dist in st.regionChildIds) {
                    if (dist.regionName.contains(q, ignoreCase = true)) {
                        matches.add(
                            FlatSearchMatch(
                                label = dist.regionName,
                                subtitle = st.regionName,
                                config = SelectedRegionConfig(
                                    stateId = st.regionId,
                                    stateName = st.regionName,
                                    districtId = dist.regionId,
                                    districtName = dist.regionName
                                )
                            )
                        )
                    }
                    for (comm in dist.regionChildIds) {
                        if (comm.regionName.contains(q, ignoreCase = true)) {
                            matches.add(
                                FlatSearchMatch(
                                    label = comm.regionName,
                                    subtitle = "${st.regionName} • ${dist.regionName}",
                                    config = SelectedRegionConfig(
                                        stateId = st.regionId,
                                        stateName = st.regionName,
                                        districtId = dist.regionId,
                                        districtName = dist.regionName,
                                        communityId = comm.regionId,
                                        communityName = comm.regionName
                                    )
                                )
                            )
                        }
                    }
                }
            }
            matches
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFF161922),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE2E8F0),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Step indicator pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepBadge("1. Область", active = stepNumber == 1)
                    StepBadge("2. Район", active = stepNumber == 2)
                    StepBadge("3. Громада", active = stepNumber == 3)
                }
                Text(
                    text = stepTitle,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Швидкий пошук (Київ, Ірпінь, Львів...)", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    if (searchQuery.isNotBlank()) {
                        if (searchResults.isEmpty()) {
                            item {
                                Text(
                                    text = "Нічого не знайдено за запитом «$searchQuery»",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            items(searchResults) { match ->
                                RegionListRow(
                                    label = match.label,
                                    subtitle = match.subtitle,
                                    onClick = { onRegionSelected(match.config) }
                                )
                            }
                        }
                    } else {
                        when {
                            selectedState == null -> {
                                items(states) { state ->
                                    RegionListRow(
                                        label = state.regionName,
                                        subtitle = if (state.regionChildIds.isNotEmpty()) "${state.regionChildIds.size} районів" else null,
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
                                        subtitle = "Сповіщення для всієї області",
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
                                        subtitle = if (district.regionChildIds.isNotEmpty()) "${district.regionChildIds.size} громад" else null,
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
                                        subtitle = "Сповіщення для всього району",
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
                }
            }
        },
        confirmButton = {
            if (selectedState != null && searchQuery.isBlank()) {
                TextButton(onClick = {
                    if (selectedDistrict != null) {
                        selectedDistrict = null
                    } else {
                        selectedState = null
                    }
                }) {
                    Text("Назад", color = Color(0xFFFF5449), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
private fun StepBadge(text: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) Color(0xFFFF5449) else Color(0xFF232836)
    ) {
        Text(
            text = text,
            color = if (active) Color(0xFF3B0002) else Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RegionListRow(
    label: String,
    subtitle: String? = null,
    isHighlight: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isHighlight) Color(0xFF3B1219) else Color(0xFF202534),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isHighlight) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFFFF5449).copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFFF5449),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column {
                    Text(
                        text = label,
                        color = if (isHighlight) Color(0xFFFF897D) else Color.White,
                        fontWeight = if (isHighlight) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF64748B)
            )
        }
    }
}
