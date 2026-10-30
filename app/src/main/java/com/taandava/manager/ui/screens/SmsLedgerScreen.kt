package com.taandava.manager.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.taandava.manager.data.SmsRecordEntity
import com.taandava.manager.ui.*
import com.taandava.manager.ui.components.SmsInspectorDialog
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SmsLedgerScreen(
    vm: MainViewModel,
    onWhatsAppClick: (String) -> Unit,
    onSetDefaultSms: () -> Unit = {}
) {
    val context = LocalContext.current
    val smsRecords by vm.smsRecords.collectAsState()
    val query by vm.smsQuery.collectAsState()
    val selectedFilter by vm.smsFilter.collectAsState()
    val selectedIds by vm.selectedSmsIds.collectAsState()
    val isScanningSms by vm.isScanningSms.collectAsState()
    val isDefaultSms by vm.isDefaultSmsRole.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteMode by remember { mutableStateOf("SELECTED") }
    var inspectingSms by remember { mutableStateOf<SmsRecordEntity?>(null) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    val density = LocalDensity.current
    val screenWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val isWideScreen = screenWidth >= 600.dp

    val gridColumns = when {
        screenWidth >= 1200.dp -> GridCells.Fixed(4)
        screenWidth >= 840.dp -> GridCells.Fixed(3)
        screenWidth >= 560.dp -> GridCells.Fixed(2)
        else -> GridCells.Fixed(1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Search SMS
        GlassTextField(
            value = query,
            onValueChange = { vm.updateSmsQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Search SMS body, sender, phone, OTP...",
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GlassCyan) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.updateSmsQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextPrimary)
                    }
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        // Default SMS App Banner (Enables direct deletion from Android telephony storage)
        if (!isDefaultSms) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                backgroundColor = Color(0x280284C7),
                borderColor = GlassCyan.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Permanent Device Deletion", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                        Text(
                            "Set Manager as default SMS app to delete messages directly from your device storage.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onSetDefaultSms,
                        colors = ButtonDefaults.buttonColors(containerColor = GlassCyan, contentColor = Color(0xFF070B14)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Set Default", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Horizontal Filter Chips Row
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                "ALL" to "All",
                "INBOX" to "Inbox 📥",
                "SENT" to "Sent 📤",
                "SAVED" to "Saved Contacts",
                "OTP" to "OTP 🟣",
                "TRANSACTION" to "Transactions 🟢",
                "NOTIFICATION" to "Notifications 🔵",
                "MOBILE" to "Mobile Numbers",
                "ALPHA" to "Shortcodes / Banks",
                "UNSYNCED" to "Unsynced"
            ).forEach { (key, label) ->
                val isSelected = selectedFilter == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) GlassCyan.copy(alpha = 0.25f) else Color(0x35131E33))
                        .border(
                            BorderStroke(1.dp, if (isSelected) GlassCyan else Color(0x25FFFFFF)),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { vm.updateSmsFilter(key) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) GlassCyan else TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Responsive Action Toolbar
        if (isWideScreen) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.selectAllSms() }) {
                        Text("Select All", fontSize = 11.sp, color = GlassCyan)
                    }
                    TextButton(onClick = { vm.deselectAllSms() }) {
                        Text("Deselect", fontSize = 11.sp, color = TextMuted)
                    }
                    if (selectedIds.isNotEmpty()) {
                        Text("(${selectedIds.size} selected)", fontSize = 11.sp, color = GlassCyan, fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selectedIds.isNotEmpty()) {
                        Button(
                            onClick = { deleteMode = "SELECTED"; showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GlassCoral),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Delete (${selectedIds.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    GlassSecondaryButton(
                        text = "Del Filtered",
                        onClick = { deleteMode = "FILTERED"; showDeleteConfirm = true },
                        modifier = Modifier.height(34.dp)
                    )
                    GlassPrimaryButton(
                        text = if (isScanningSms) "Scanning..." else "Scan SMS",
                        onClick = { vm.scanDeviceSms() },
                        modifier = Modifier.height(34.dp),
                        enabled = !isScanningSms
                    )
                    GlassSecondaryButton(
                        text = "Clean Dupes",
                        onClick = { vm.cleanSmsDupes() },
                        modifier = Modifier.height(34.dp)
                    )
                }
            }
        } else {
            // Mobile: 2-Tier Responsive Layout (Controls on top, scrollable action pills below)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { vm.selectAllSms() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Select All", fontSize = 11.sp, color = GlassCyan)
                        }
                        TextButton(
                            onClick = { vm.deselectAllSms() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Deselect", fontSize = 11.sp, color = TextMuted)
                        }
                        if (selectedIds.isNotEmpty()) {
                            Text("(${selectedIds.size})", fontSize = 11.sp, color = GlassCyan, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        "${smsRecords.size} found",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassPrimaryButton(
                        text = if (isScanningSms) "Scanning..." else "Scan SMS",
                        onClick = { vm.scanDeviceSms() },
                        modifier = Modifier.height(34.dp),
                        enabled = !isScanningSms
                    )
                    if (selectedIds.isNotEmpty()) {
                        Button(
                            onClick = { deleteMode = "SELECTED"; showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GlassCoral),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Text("Delete (${selectedIds.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    GlassSecondaryButton(
                        text = "Del Filtered",
                        onClick = { deleteMode = "FILTERED"; showDeleteConfirm = true },
                        modifier = Modifier.height(34.dp)
                    )
                    GlassSecondaryButton(
                        text = "Clean Dupes",
                        onClick = { vm.cleanSmsDupes() },
                        modifier = Modifier.height(34.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (isScanningSms) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassCard(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = GlassCyan, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                        Text("Scanning Device SMS...", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                        Text(
                            "Reading SMS inbox and sent messages, extracting OTPs and financial transactions into the local ledger. Please wait...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else if (smsRecords.isEmpty()) {
            val hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassCard(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = if (hasSmsPermission) GlassCyan else GlassAmber,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            if (hasSmsPermission) "No SMS Records in Ledger" else "SMS Permission Required",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 16.sp
                        )
                        Text(
                            if (hasSmsPermission)
                                "Tap 'Scan SMS from Device' below to import your device's SMS message history with instant OTP & transaction ledger extraction."
                            else
                                "Grant SMS permission to read and catalog your device SMS messages into the ledger.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        if (hasSmsPermission) {
                            GlassPrimaryButton(
                                text = "Scan SMS from Device",
                                onClick = { vm.scanDeviceSms() }
                            )
                        } else {
                            GlassPrimaryButton(
                                text = "Open App Settings for SMS Permission",
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = gridColumns,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(smsRecords, key = { it.id }) { sms ->
                    val isChecked = selectedIds.contains(sms.id)
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { inspectingSms = sms }
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { vm.toggleSmsSelection(sms.id) },
                                    colors = CheckboxDefaults.colors(checkedColor = GlassCyan)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (sms.type == 1) "📥 ${sms.address}" else "📤 ${sms.address}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val badgeColor = when (sms.category) {
                                    "OTP" -> GlassPurple
                                    "TRANSACTION" -> GlassMint
                                    "NOTIFICATION" -> GlassCyan
                                    else -> GlassIndigo
                                }
                                GlassBadge(text = sms.category, color = badgeColor, showDot = false)
                            }

                            Text(
                                text = sms.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Intelligent Extracted Highlights (OTP & Amount)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!sms.extractedOtp.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(GlassPurple.copy(alpha = 0.25f))
                                            .border(BorderStroke(1.dp, GlassPurple), RoundedCornerShape(8.dp))
                                            .clickable {
                                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cb.setPrimaryClip(ClipData.newPlainText("OTP", sms.extractedOtp))
                                                Toast.makeText(context, "Copied OTP: ${sms.extractedOtp}", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GlassPurple, modifier = Modifier.size(12.dp))
                                            Text("Copy OTP: ${sms.extractedOtp}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlassPurple)
                                        }
                                    }
                                }

                                if (!sms.extractedAmount.isNullOrBlank()) {
                                    val isDeb = sms.isDebit == true
                                    val amtColor = if (isDeb) GlassCoral else GlassMint
                                    GlassBadge(
                                        text = "${if (isDeb) "-" else "+"}${sms.extractedAmount}",
                                        color = amtColor,
                                        showDot = false
                                    )
                                }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(sms.senderType, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(sdf.format(Date(sms.date)), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Inspector Dialog
    if (inspectingSms != null) {
        SmsInspectorDialog(
            sms = inspectingSms!!,
            onDismiss = { inspectingSms = null },
            onWhatsApp = { phone ->
                onWhatsAppClick(phone)
                inspectingSms = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xF20F172A),
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    if (deleteMode == "SELECTED") "Delete Selected SMS" else "Delete Filtered SMS",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    if (deleteMode == "SELECTED") "Permanently delete ${selectedIds.size} selected SMS records from the ledger?"
                    else "Permanently delete all SMS records matching current filter ('$selectedFilter')?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                GlassDangerButton(
                    text = "Delete",
                    onClick = {
                        if (deleteMode == "SELECTED") vm.deleteSelectedSms() else vm.deleteFilteredSms()
                        showDeleteConfirm = false
                    }
                )
            },
            dismissButton = {
                GlassSecondaryButton(text = "Cancel", onClick = { showDeleteConfirm = false })
            }
        )
    }
}
