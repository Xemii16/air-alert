package ua.alert.phone.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.alert.shared.RegionNode
import ua.alert.shared.SelectedRegionConfig

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RegionPickerDialog(
    currentRegion: SelectedRegionConfig,
    regionsTree: List<RegionNode>,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onRegionSelected: (SelectedRegionConfig) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedState by remember(currentRegion, regionsTree) {
        mutableStateOf(regionsTree.find { it.regionId == currentRegion.stateId })
    }
    var selectedDistrict by remember(selectedState, currentRegion) {
        mutableStateOf(
            selectedState?.regionChildIds?.find { it.regionId == currentRegion.districtId }
        )
    }
    var selectedCommunity by remember(selectedDistrict, currentRegion) {
        mutableStateOf(
            selectedDistrict?.regionChildIds?.find { it.regionId == currentRegion.communityId }
        )
    }

    var currentStep by remember {
        mutableIntStateOf(
            when {
                selectedDistrict != null && selectedDistrict!!.regionChildIds.isNotEmpty() -> 2
                selectedState != null && selectedState!!.regionChildIds.isNotEmpty() -> 1
                else -> 0
            }
        )
    }
    var searchQuery by remember { mutableStateOf("") }

    val activeList: List<RegionNode> = remember(currentStep, selectedState, selectedDistrict, regionsTree, searchQuery) {
        val source = when (currentStep) {
            0 -> regionsTree
            1 -> selectedState?.regionChildIds.orEmpty()
            else -> selectedDistrict?.regionChildIds.orEmpty()
        }
        val q = searchQuery.trim()
        if (q.isEmpty()) source else source.filter { it.regionName.contains(q, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
        ) {
            // Sheet Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Регіон оповіщення",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildBreadcrumb(selectedState, selectedDistrict, selectedCommunity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Закрити")
                }
            }

            Spacer(Modifier.height(14.dp))

            // Connected ButtonGroup for 3-level hierarchy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                FilledTonalToggleButton(
                    checked = currentStep == 0,
                    onCheckedChange = {
                        currentStep = 0
                        searchQuery = ""
                    },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "1. Область",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val hasDistricts = selectedState?.regionChildIds?.isNotEmpty() == true
                FilledTonalToggleButton(
                    checked = currentStep == 1,
                    enabled = hasDistricts,
                    onCheckedChange = {
                        if (hasDistricts) {
                            currentStep = 1
                            searchQuery = ""
                        }
                    },
                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "2. Район",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val hasCommunities = selectedDistrict?.regionChildIds?.isNotEmpty() == true
                FilledTonalToggleButton(
                    checked = currentStep == 2,
                    enabled = hasCommunities,
                    onCheckedChange = {
                        if (hasCommunities) {
                            currentStep = 2
                            searchQuery = ""
                        }
                    },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "3. Громада",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Instant search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Пошук регіону…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Очистити")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Quick confirmation at Oblast or Raion level without descending further
            if (currentStep > 0 && selectedState != null) {
                Spacer(Modifier.height(10.dp))
                val targetLabel = if (currentStep == 1) {
                    "Обрати всю область: ${selectedState!!.regionName}"
                } else {
                    "Обрати весь район: ${selectedDistrict?.regionName ?: ""}"
                }
                FilledTonalButton(
                    onClick = {
                        val state = selectedState ?: return@FilledTonalButton
                        val district = if (currentStep == 2) selectedDistrict else null
                        onRegionSelected(
                            SelectedRegionConfig(
                                stateId = state.regionId,
                                stateName = state.regionName,
                                districtId = district?.regionId,
                                districtName = district?.regionName,
                                communityId = null,
                                communityName = null
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = targetLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // LoadingIndicator or Region List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isLoading && activeList.isEmpty() -> {
                        LoadingIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    activeList.isEmpty() -> {
                        Text(
                            text = "Нічого не знайдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        AnimatedContent(targetState = currentStep to activeList, label = "region_step") { (_, nodes) ->
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                itemsIndexed(nodes, key = { _, item -> item.regionId }) { index, node ->
                                    val isSelected = when (currentStep) {
                                        0 -> selectedState?.regionId == node.regionId
                                        1 -> selectedDistrict?.regionId == node.regionId
                                        else -> selectedCommunity?.regionId == node.regionId
                                    }
                                    val itemShape = groupedItemShape(index, nodes.size)
                                    val hasChildren = node.regionChildIds.isNotEmpty() && currentStep < 2

                                    Surface(
                                        shape = itemShape,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(itemShape)
                                            .clickable {
                                                when (currentStep) {
                                                    0 -> {
                                                        selectedState = node
                                                        selectedDistrict = null
                                                        selectedCommunity = null
                                                        searchQuery = ""
                                                        if (node.regionChildIds.isNotEmpty()) {
                                                            currentStep = 1
                                                        } else {
                                                            onRegionSelected(
                                                                SelectedRegionConfig(
                                                                    stateId = node.regionId,
                                                                    stateName = node.regionName
                                                                )
                                                            )
                                                        }
                                                    }
                                                    1 -> {
                                                        selectedDistrict = node
                                                        selectedCommunity = null
                                                        searchQuery = ""
                                                        if (node.regionChildIds.isNotEmpty()) {
                                                            currentStep = 2
                                                        } else {
                                                            val st = selectedState!!
                                                            onRegionSelected(
                                                                SelectedRegionConfig(
                                                                    stateId = st.regionId,
                                                                    stateName = st.regionName,
                                                                    districtId = node.regionId,
                                                                    districtName = node.regionName
                                                                )
                                                            )
                                                        }
                                                    }
                                                    else -> {
                                                        selectedCommunity = node
                                                        val st = selectedState!!
                                                        val dist = selectedDistrict!!
                                                        onRegionSelected(
                                                            SelectedRegionConfig(
                                                                stateId = st.regionId,
                                                                stateName = st.regionName,
                                                                districtId = dist.regionId,
                                                                districtName = dist.regionName,
                                                                communityId = node.regionId,
                                                                communityName = node.regionName
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                    ) {
                                        ListItem(
                                            headlineContent = {
                                                Text(
                                                    text = node.regionName,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            supportingContent = if (hasChildren) {
                                                {
                                                    Text("${node.regionChildIds.size} підпорядкованих")
                                                }
                                            } else null,
                                            leadingContent = {
                                                Icon(
                                                    imageVector = if (currentStep == 0) {
                                                        Icons.Rounded.Public
                                                    } else {
                                                        Icons.Rounded.LocationOn
                                                    },
                                                    contentDescription = null,
                                                    tint = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.primary
                                                    }
                                                )
                                            },
                                            trailingContent = {
                                                if (hasChildren) {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                        contentDescription = null
                                                    )
                                                } else if (isSelected) {
                                                    Icon(
                                                        Icons.Rounded.CheckCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            },
                                            colors = ListItemDefaults.colors(
                                                containerColor = androidx.compose.ui.graphics.Color.Transparent
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom confirm bar
            if (selectedState != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Скасувати")
                    }
                    Button(
                        onClick = {
                            val st = selectedState ?: return@Button
                            onRegionSelected(
                                SelectedRegionConfig(
                                    stateId = st.regionId,
                                    stateName = st.regionName,
                                    districtId = selectedDistrict?.regionId,
                                    districtName = selectedDistrict?.regionName,
                                    communityId = selectedCommunity?.regionId,
                                    communityName = selectedCommunity?.regionName
                                )
                            )
                        },
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Text("Підтвердити")
                    }
                }
            }
        }
    }
}

private fun groupedItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    return when {
        totalCount == 1 -> RoundedCornerShape(20.dp)
        index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        index == totalCount - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

private fun buildBreadcrumb(
    state: RegionNode?,
    district: RegionNode?,
    community: RegionNode?
): String {
    return listOfNotNull(state?.regionName, district?.regionName, community?.regionName)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" • ")
        ?: "Оберіть область, район або громаду"
}
