@file:Suppress("FunctionName")

package com.taandava.manager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taandava.manager.data.DuplicateGroup
import com.taandava.manager.data.SameNameGroup
import com.taandava.manager.ui.*
import com.taandava.manager.ui.components.SmartMergeConfigDialog
import com.taandava.manager.util.NameDisambiguator
import com.taandava.manager.util.PhoneUtils

@Composable
fun DuplicatesScreen(
    vm: MainViewModel,
    onNavigateToContacts: () -> Unit = {}
) {
    val groups by vm.duplicateGroups.collectAsState()
    val sameNameGroups by vm.sameNameGroups.collectAsState()
    val currentTab by vm.duplicateTab.collectAsState()
    val isDeduplicating by vm.isDeduplicating.collectAsState()
    val isDisambiguating by vm.isDisambiguating.collectAsState()
    val deletingContactIds by vm.deletingContactIds.collectAsState()
    val mergingGroupPhones by vm.mergingGroupPhones.collectAsState()
    val disambiguatingKeys by vm.disambiguatingKeys.collectAsState()
    val smartMergeConfig by vm.smartMergeConfig.collectAsState()

    val selectedPhones by vm.selectedDuplicatePhones.collectAsState()
    val selectedSameNameKeys by vm.selectedSameNameKeys.collectAsState()
    val disambiguateSentenceCase by vm.disambiguateSentenceCase.collectAsState()

    var showMergeAllDialog by remember { mutableStateOf(false) }
    var groupToMerge by remember { mutableStateOf<DuplicateGroup?>(null) }
    var showMergeConfigDialog by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val screenWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }

    val gridColumns = when {
        screenWidth >= 1200.dp -> GridCells.Fixed(3)
        screenWidth >= 800.dp -> GridCells.Fixed(2)
        else -> GridCells.Fixed(1)
    }

    val isTablet = screenWidth >= 600.dp
    val totalRedundantEntries = groups.sumOf { (it.contacts.size - 1).coerceAtLeast(0) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Top Navigation Tabs
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (currentTab == 0) GlassCyan.copy(alpha = 0.5f) else GlassMint.copy(alpha = 0.5f)
        ) {
            PrimaryTabRow(
                selectedTabIndex = currentTab,
                containerColor = Color.Transparent,
                contentColor = TextPrimary,
                divider = { HorizontalDivider(color = Color(0x20FFFFFF), thickness = 0.5.dp) }
            ) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { vm.setDuplicateTab(0) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.CallMerge,
                                contentDescription = null,
                                tint = if (currentTab == 0) GlassCyan else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Exact Phone Duplicates",
                                fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (currentTab == 0) GlassCyan else TextSecondary,
                                fontSize = 13.sp
                            )
                            if (groups.isNotEmpty()) {
                                GlassBadge(
                                    text = "${groups.size}",
                                    color = GlassAmber,
                                    showDot = false
                                )
                            }
                        }
                    }
                )

                Tab(
                    selected = currentTab == 1,
                    onClick = { vm.setDuplicateTab(1) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Spellcheck,
                                contentDescription = null,
                                tint = if (currentTab == 1) GlassMint else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Same Name Disambiguation",
                                fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (currentTab == 1) GlassMint else TextSecondary,
                                fontSize = 13.sp
                            )
                            if (sameNameGroups.isNotEmpty()) {
                                GlassBadge(
                                    text = "${sameNameGroups.size}",
                                    color = GlassMint,
                                    showDot = false
                                )
                            }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (currentTab == 0) {
            // TAB 0: EXACT PHONE DUPLICATES
            PhoneDuplicatesTabContent(
                groups = groups,
                totalRedundantEntries = totalRedundantEntries,
                selectedPhones = selectedPhones,
                isDeduplicating = isDeduplicating,
                deletingContactIds = deletingContactIds,
                mergingGroupPhones = mergingGroupPhones,
                gridColumns = gridColumns,
                isTablet = isTablet,
                onToggleSelection = { vm.toggleDuplicatePhoneSelection(it) },
                onSelectAll = { vm.selectAllDuplicatePhones() },
                onClearSelection = { vm.clearDuplicatePhoneSelection() },
                onMergeSelected = { vm.smartMergeSelectedGroups() },
                onMergeAll = { showMergeAllDialog = true },
                onMergeGroup = { groupToMerge = it },
                onDeleteContact = { vm.deleteContact(it) },
                onCleanCopies = { vm.cleanAppDuplicates() },
                onPurgeInvalid = { vm.purgeInvalidContacts() },
                onCleanDevice = { vm.cleanDeviceDuplicates() },
                onOpenConfig = { showMergeConfigDialog = true },
                onNavigateToContacts = onNavigateToContacts
            )
        } else {
            // TAB 1: SAME NAME DISAMBIGUATION
            SameNameDisambiguationTabContent(
                sameNameGroups = sameNameGroups,
                selectedKeys = selectedSameNameKeys,
                isDisambiguating = isDisambiguating,
                disambiguatingKeys = disambiguatingKeys,
                sentenceCaseEnabled = disambiguateSentenceCase,
                gridColumns = gridColumns,
                isTablet = isTablet,
                onToggleSentenceCase = { vm.setDisambiguateSentenceCase(it) },
                onToggleSelection = { vm.toggleSameNameKeySelection(it) },
                onSelectAll = { vm.selectAllSameNameKeys() },
                onClearSelection = { vm.clearSameNameKeySelection() },
                onDisambiguateSelected = { vm.disambiguateSelectedSameNameGroups(disambiguateSentenceCase) },
                onDisambiguateAll = { vm.disambiguateAllSameNameGroups(disambiguateSentenceCase) },
                onDisambiguateGroup = { vm.disambiguateSameNameGroup(it, disambiguateSentenceCase) },
                onNavigateToContacts = onNavigateToContacts
            )
        }
    }

    if (showMergeAllDialog) {
        SmartMergeConfigDialog(
            targetPhone = null,
            groupCount = groups.size,
            initialConfig = smartMergeConfig,
            onDismiss = { showMergeAllDialog = false },
            onConfirm = { config ->
                showMergeAllDialog = false
                vm.updateSmartMergeConfig(config)
                vm.smartMergeAllGroups(config)
            }
        )
    }

    if (groupToMerge != null) {
        val target = groupToMerge!!
        SmartMergeConfigDialog(
            targetPhone = target.phone,
            groupCount = 1,
            initialConfig = smartMergeConfig,
            onDismiss = { groupToMerge = null },
            onConfirm = { config ->
                val phone = target.phone
                groupToMerge = null
                vm.updateSmartMergeConfig(config)
                vm.smartMergeGroup(phone, config)
            }
        )
    }

    if (showMergeConfigDialog) {
        SmartMergeConfigDialog(
            targetPhone = null,
            groupCount = groups.size,
            initialConfig = smartMergeConfig,
            onDismiss = { showMergeConfigDialog = false },
            onConfirm = { config ->
                showMergeConfigDialog = false
                vm.updateSmartMergeConfig(config)
            }
        )
    }
}

@Composable
private fun PhoneDuplicatesTabContent(
    groups: List<DuplicateGroup>,
    totalRedundantEntries: Int,
    selectedPhones: Set<String>,
    isDeduplicating: Boolean,
    deletingContactIds: Set<String>,
    mergingGroupPhones: Set<String>,
    gridColumns: GridCells,
    isTablet: Boolean,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMergeSelected: () -> Unit,
    onMergeAll: () -> Unit,
    onMergeGroup: (DuplicateGroup) -> Unit,
    onDeleteContact: (com.taandava.manager.data.ContactEntity) -> Unit,
    onCleanCopies: () -> Unit,
    onPurgeInvalid: () -> Unit,
    onCleanDevice: () -> Unit,
    onOpenConfig: () -> Unit,
    onNavigateToContacts: () -> Unit
) {
    if (groups.isEmpty()) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(if (isTablet) 0.6f else 1f),
                shape = RoundedCornerShape(24.dp),
                borderColor = GlassMint.copy(alpha = 0.4f)
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(GlassMint.copy(alpha = 0.15f))
                            .border(BorderStroke(1.5.dp, GlassMint.copy(alpha = 0.5f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GlassMint,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Text(
                        "Zero Phone Number Duplicates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        "Every phone number in your database is uniquely consolidated with strict exact-number verification. No redundant copies found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCleanDevice,
                            enabled = !isDeduplicating,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, GlassCyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassCyan)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clean Device", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onNavigateToContacts,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GlassCyan, contentColor = Color(0xFF070B14))
                        ) {
                            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("View Contacts", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    } else {
        // Hero Status & Actions Bento Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = GlassAmber.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GlassAmber.copy(alpha = 0.15f))
                                .border(BorderStroke(1.dp, GlassAmber.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, tint = GlassAmber, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Exact Number Deduplication", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                            Text("Only identical 10-15 digit phone numbers are grouped and merged", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    IconButton(
                        onClick = onOpenConfig,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Merge Strategy", tint = GlassCyan, modifier = Modifier.size(20.dp))
                    }
                }

                // Metrics Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x25FFFFFF))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text("Duplicate Groups", fontSize = 10.sp, color = TextMuted)
                            Text("${groups.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlassAmber)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x25FFFFFF))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text("Redundant Copies", fontSize = 10.sp, color = TextMuted)
                            Text("$totalRedundantEntries", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlassCoral)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x25FFFFFF))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text("Selected", fontSize = 10.sp, color = TextMuted)
                            Text("${selectedPhones.size} / ${groups.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlassCyan)
                        }
                    }
                }

                // Checkbox Batch Selection Header
                val allSelected = groups.isNotEmpty() && selectedPhones.size == groups.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x18FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable {
                            if (allSelected) onClearSelection() else onSelectAll()
                        }
                    ) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { if (it) onSelectAll() else onClearSelection() },
                            colors = CheckboxDefaults.colors(checkedColor = GlassCyan)
                        )
                        Text(
                            if (allSelected) "Deselect All" else "Select All (${groups.size} groups)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }

                    if (selectedPhones.isNotEmpty()) {
                        TextButton(
                            onClick = onClearSelection,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Clear (${selectedPhones.size})", fontSize = 11.sp, color = GlassCoral)
                        }
                    }
                }

                // Primary Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Merge Selected Button
                    Button(
                        onClick = onMergeSelected,
                        enabled = !isDeduplicating && selectedPhones.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassCyan, contentColor = Color(0xFF070B14)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (selectedPhones.isEmpty()) "Merge Selected" else "Merge Selected (${selectedPhones.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Smart Merge All
                    Button(
                        onClick = onMergeAll,
                        enabled = !isDeduplicating && groups.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassAmber, contentColor = Color(0xFF070B14)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Merge All (${groups.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Secondary Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCleanCopies,
                        enabled = !isDeduplicating,
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GlassAmber.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassAmber),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Clean Copies", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onPurgeInvalid,
                        enabled = !isDeduplicating,
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GlassCoral.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassCoral),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Purge SMS", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onCleanDevice,
                        enabled = !isDeduplicating,
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GlassMint.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassMint),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Clean Device", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Duplicate Group Cards
        val distinctGroups = remember(groups) { groups.distinctBy { it.phone } }
        LazyVerticalGrid(
            columns = gridColumns,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(distinctGroups, key = { it.phone }) { group ->
                val isGroupMerging = mergingGroupPhones.contains(group.phone)
                val isGroupSelected = selectedPhones.contains(group.phone)

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (isGroupSelected) GlassCyan.copy(alpha = 0.7f) else Color(0x30FFFFFF)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Group Header with Checkbox
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = isGroupSelected,
                                    onCheckedChange = { onToggleSelection(group.phone) },
                                    colors = CheckboxDefaults.colors(checkedColor = GlassCyan)
                                )

                                Column {
                                    Text(
                                        PhoneUtils.formatIndianPhone(group.phone),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                    Text("${group.contacts.size} duplicate copies", fontSize = 10.sp, color = TextMuted)
                                }
                            }

                            OutlinedButton(
                                onClick = { onMergeGroup(group) },
                                enabled = !isGroupMerging && !isDeduplicating,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassMint),
                                border = BorderStroke(1.dp, if (!isGroupMerging && !isDeduplicating) GlassMint else GlassMint.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                if (isGroupMerging) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = GlassMint
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Merging...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = GlassMint, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Merge", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0x20FFFFFF), thickness = 0.5.dp)

                        // Contact Copies
                        group.contacts.forEach { c ->
                            val cleanDetails = c.details.trim()
                            val isDeviceDuplicate = cleanDetails.startsWith("Device:", ignoreCase = true) ||
                                                    cleanDetails.equals(c.deviceId, ignoreCase = true) ||
                                                    (c.deviceId.isNotBlank() && cleanDetails.contains(c.deviceId, ignoreCase = true))
                            val isDeleting = deletingContactIds.contains(c.id)

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x15FFFFFF))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (c.saved) GlassGold.copy(alpha = 0.2f) else Color(0x30131E33)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (c.name.firstOrNull()?.uppercase() ?: "C"),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (c.saved) GlassGold else GlassCyan
                                        )
                                    }

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                c.name.ifBlank { "Unnamed Contact" },
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp,
                                                color = TextPrimary
                                            )
                                            if (c.saved) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.Default.Star, contentDescription = "Starred", tint = GlassGold, modifier = Modifier.size(13.dp))
                                            }
                                        }
                                        if (cleanDetails.isNotBlank() && !isDeviceDuplicate) {
                                            Text(cleanDetails, fontSize = 11.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (c.deviceId.isNotBlank()) {
                                            Text("📱 ${c.deviceId}", fontSize = 10.sp, color = TextMuted)
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { onDeleteContact(c) },
                                    enabled = !isDeleting && !isDeduplicating,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    if (isDeleting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 1.5.dp,
                                            color = GlassCoral
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Copy",
                                            tint = if (!isDeduplicating) GlassCoral else GlassCoral.copy(alpha = 0.3f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SameNameDisambiguationTabContent(
    sameNameGroups: List<SameNameGroup>,
    selectedKeys: Set<String>,
    isDisambiguating: Boolean,
    disambiguatingKeys: Set<String>,
    sentenceCaseEnabled: Boolean,
    gridColumns: GridCells,
    isTablet: Boolean,
    onToggleSentenceCase: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDisambiguateSelected: () -> Unit,
    onDisambiguateAll: () -> Unit,
    onDisambiguateGroup: (String) -> Unit,
    onNavigateToContacts: () -> Unit
) {
    if (sameNameGroups.isEmpty()) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(if (isTablet) 0.6f else 1f),
                shape = RoundedCornerShape(24.dp),
                borderColor = GlassMint.copy(alpha = 0.4f)
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(GlassMint.copy(alpha = 0.15f))
                            .border(BorderStroke(1.5.dp, GlassMint.copy(alpha = 0.5f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Spellcheck,
                            contentDescription = null,
                            tint = GlassMint,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Text(
                        "Zero Same-Name Clashes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        "There are no multiple phone numbers sharing identical names. Every person has a distinct name or already disambiguated spelling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = onNavigateToContacts,
                        modifier = Modifier.fillMaxWidth(0.6f).height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassMint, contentColor = Color(0xFF070B14))
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View Contacts", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        // Hero Explanation & Strategy Bento Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = GlassMint.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GlassMint.copy(alpha = 0.15f))
                                .border(BorderStroke(1.dp, GlassMint.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Spellcheck, contentDescription = null, tint = GlassMint, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Same Name, Multi-Number Fix", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                            Text("Disambiguates duplicate names across distinct phone numbers", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    GlassBadge(text = "${sameNameGroups.size} Groups", color = GlassMint, showDot = true)
                }

                // Rule Explanatory Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x18FFFFFF))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Disambiguation Rule (Exact Specification):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlassMint
                        )
                        Text(
                            "• Repeats the last alphabet of the first word based on duplicate index (1st copy: Ramesh, 2nd: Rameshh, 3rd: Rameshhh)",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                        Text(
                            "• Preserves honorific prefixes (Dr., Mr., Shri, etc.) and suffixes (Jr., II, etc.) without altering them",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                        Text(
                            "• Applies clean sentence case to all normal name words",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Strategy Controls: Radio buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { onToggleSentenceCase(!sentenceCaseEnabled) }
                    ) {
                        RadioButton(
                            selected = sentenceCaseEnabled,
                            onClick = { onToggleSentenceCase(true) },
                            colors = RadioButtonDefaults.colors(selectedColor = GlassMint)
                        )
                        Text("Sentence Case", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { onToggleSentenceCase(false) }
                    ) {
                        RadioButton(
                            selected = !sentenceCaseEnabled,
                            onClick = { onToggleSentenceCase(false) },
                            colors = RadioButtonDefaults.colors(selectedColor = GlassMint)
                        )
                        Text("Keep Original Casing", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                }

                // Batch Selection Strip
                val allSelected = sameNameGroups.isNotEmpty() && selectedKeys.size == sameNameGroups.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x18FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable {
                            if (allSelected) onClearSelection() else onSelectAll()
                        }
                    ) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { if (it) onSelectAll() else onClearSelection() },
                            colors = CheckboxDefaults.colors(checkedColor = GlassMint)
                        )
                        Text(
                            if (allSelected) "Deselect All" else "Select All (${sameNameGroups.size} groups)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }

                    if (selectedKeys.isNotEmpty()) {
                        TextButton(
                            onClick = onClearSelection,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Clear (${selectedKeys.size})", fontSize = 11.sp, color = GlassCoral)
                        }
                    }
                }

                // Primary Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDisambiguateSelected,
                        enabled = !isDisambiguating && selectedKeys.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassMint, contentColor = Color(0xFF070B14)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Default.Spellcheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (selectedKeys.isEmpty()) "Fix Selected" else "Fix Selected (${selectedKeys.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = onDisambiguateAll,
                        enabled = !isDisambiguating && sameNameGroups.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassCyan, contentColor = Color(0xFF070B14)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Fix All (${sameNameGroups.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Same Name Group Cards
        LazyVerticalGrid(
            columns = gridColumns,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(sameNameGroups, key = { it.nameKey }) { group ->
                val isGroupSelected = selectedKeys.contains(group.nameKey)
                val isGroupProcessing = disambiguatingKeys.contains(group.nameKey)

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (isGroupSelected) GlassMint.copy(alpha = 0.7f) else Color(0x30FFFFFF)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Card Header with Checkbox
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = isGroupSelected,
                                    onCheckedChange = { onToggleSelection(group.nameKey) },
                                    colors = CheckboxDefaults.colors(checkedColor = GlassMint)
                                )

                                Column {
                                    Text(
                                        group.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                    Text("${group.contacts.size} distinct phone numbers", fontSize = 10.sp, color = TextMuted)
                                }
                            }

                            OutlinedButton(
                                onClick = { onDisambiguateGroup(group.nameKey) },
                                enabled = !isGroupProcessing && !isDisambiguating,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassMint),
                                border = BorderStroke(1.dp, if (!isGroupProcessing && !isDisambiguating) GlassMint else GlassMint.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                if (isGroupProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = GlassMint
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Fixing...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Spellcheck, contentDescription = null, tint = GlassMint, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Fix", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0x20FFFFFF), thickness = 0.5.dp)

                        // Disambiguation Live Previews
                        group.contacts.forEachIndexed { index, contact ->
                            val targetName = NameDisambiguator.disambiguateName(
                                rawName = contact.name,
                                extraRepeats = index,
                                applySentenceCase = sentenceCaseEnabled
                            )
                            val isUnchanged = targetName == contact.name

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x15FFFFFF))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            contact.name,
                                            fontSize = 12.sp,
                                            color = TextMuted
                                        )
                                        Text("➔", fontSize = 11.sp, color = GlassCyan)
                                        Text(
                                            targetName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isUnchanged) TextPrimary else GlassMint
                                        )
                                    }
                                    Text(
                                        PhoneUtils.formatIndianPhone(contact.phone),
                                        fontSize = 11.sp,
                                        color = GlassCyan
                                    )
                                }

                                GlassBadge(
                                    text = if (index == 0) "Base (0)" else "Copy +$index",
                                    color = if (index == 0) GlassCyan else GlassMint,
                                    showDot = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
