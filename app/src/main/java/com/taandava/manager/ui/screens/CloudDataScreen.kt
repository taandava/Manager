package com.taandava.manager.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.taandava.manager.data.CloudContactItem
import com.taandava.manager.data.CloudSmsItem
import com.taandava.manager.ui.*
import com.taandava.manager.util.PhoneUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@Suppress("FunctionName") // Standard Jetpack Compose PascalCase UI component convention
fun CloudDataViewerDialog(
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onWhatsAppClick: (String) -> Unit
) {
    val context = LocalContext.current
    val currentTab by vm.cloudViewerTab.collectAsState()
    val searchQuery by vm.cloudSearchQuery.collectAsState()
    val contacts by vm.cloudContacts.collectAsState()
    val totalContacts by vm.cloudTotalContacts.collectAsState()
    val smsList by vm.cloudSms.collectAsState()
    val totalSms by vm.cloudTotalSms.collectAsState()
    val overview by vm.cloudOverview.collectAsState()
    val isLoading by vm.isCloudLoading.collectAsState()
    val isLoadingMore by vm.isCloudLoadingMore.collectAsState()
    val errorMessage by vm.cloudErrorMessage.collectAsState()
    val cloudUrl by vm.cloudUrl.collectAsState()

    val contactsListState = rememberLazyListState()
    val smsListState = rememberLazyListState()

    val shouldLoadMoreContacts by remember {
        derivedStateOf {
            val total = contactsListState.layoutInfo.totalItemsCount
            val lastVisible = contactsListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4 && contacts.size < totalContacts && !isLoading && !isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMoreContacts) {
        if (shouldLoadMoreContacts) {
            vm.loadMoreCloudContacts()
        }
    }

    val shouldLoadMoreSms by remember {
        derivedStateOf {
            val total = smsListState.layoutInfo.totalItemsCount
            val lastVisible = smsListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4 && smsList.size < totalSms && !isLoading && !isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMoreSms) {
        if (shouldLoadMoreSms) {
            vm.loadMoreCloudSms()
        }
    }

    val hostName = remember(cloudUrl) {
        try {
            java.net.URI(cloudUrl).host ?: "Hostinger Cloud"
        } catch (e: Exception) {
            android.util.Log.w("CloudDataScreen", "Invalid cloud URI: ${e.message}")
            "Hostinger Cloud"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(BorderStroke(1.5.dp, GlassCyan.copy(alpha = 0.35f)), RoundedCornerShape(24.dp)),
            color = Color(0xF8070B14)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF070B14), Color(0xFF0E172B), Color(0xFF070B14))
                        )
                    )
            ) {
                // Top App Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(GlassCyan, Color(0xFF0284C7)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text(
                                "Cloud Data Explorer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(GlassMint)
                                )
                                Text(
                                    hostName,
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { vm.refreshCloudData(); vm.loadCloudOverview() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = GlassCyan)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                        }
                    }
                }

                // Horizontal Accent Line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0x10FFFFFF), GlassCyan.copy(alpha = 0.5f), Color(0x10FFFFFF))
                            )
                        )
                )

                // Search Input Shell
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    GlassTextField(
                        value = searchQuery,
                        onValueChange = { vm.updateCloudSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = if (currentTab == 0) "Search cloud contacts, numbers, notes..." else "Search cloud SMS, numbers, text...",
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = GlassCyan)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { vm.updateCloudSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextPrimary)
                                }
                            }
                        }
                    )
                }

                // Overview Quick Stats Strip
                if (overview != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CloudStatPill(
                            label = "Contacts",
                            value = "${overview!!.totalContacts}",
                            color = GlassCyan,
                            modifier = Modifier.weight(1f)
                        )
                        CloudStatPill(
                            label = "SMS Logs",
                            value = "${overview!!.totalSms}",
                            color = GlassPurple,
                            modifier = Modifier.weight(1f)
                        )
                        CloudStatPill(
                            label = "Devices",
                            value = "${overview!!.devices.size}",
                            color = GlassMint,
                            modifier = Modifier.weight(1f)
                        )
                        CloudStatPill(
                            label = "Size",
                            value = "${overview!!.dbSizeBytes / 1024} KB",
                            color = GlassAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Tab Pills Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val contactsLabel = if (totalContacts > 0) "📇 Contacts ($totalContacts)" else "📇 Contacts"
                    val smsLabel = if (totalSms > 0) "💬 SMS Ledger ($totalSms)" else "💬 SMS Ledger"

                    listOf(0 to contactsLabel, 1 to smsLabel).forEach { (tabIndex, label) ->
                        val isSelected = currentTab == tabIndex
                        val bgModifier = if (isSelected) {
                            Modifier.background(Brush.horizontalGradient(listOf(Color(0x4038BDF8), Color(0x300284C7))))
                        } else {
                            Modifier.background(Color(0x25131E33))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .then(bgModifier)
                                .border(
                                    BorderStroke(1.dp, if (isSelected) GlassCyan else Color(0x25FFFFFF)),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { vm.setCloudViewerTab(tabIndex) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) GlassCyan else TextMuted
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Error Banner
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassCoral.copy(alpha = 0.15f))
                            .border(BorderStroke(1.dp, GlassCoral.copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(errorMessage!!, color = GlassCoral, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.refreshCloudData() }) {
                                Text("Retry", color = GlassCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Loading Indicator
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = GlassCyan
                            )
                            Text("Fetching data from Hostinger...", fontSize = 11.5.sp, color = TextMuted)
                        }
                    }
                }

                // Content Lists
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                ) {
                    if (currentTab == 0) {
                        // Cloud Contacts List
                        if (contacts.isEmpty() && !isLoading) {
                            EmptyCloudState(
                                icon = Icons.Default.PeopleOutline,
                                title = if (searchQuery.isNotBlank()) "No contacts matching '$searchQuery'" else "No contacts found in cloud storage",
                                subtitle = "Sync your device contacts or verify server URL in Settings."
                            )
                        } else {
                            LazyColumn(
                                state = contactsListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(contacts, key = { it.id.ifBlank { it.phone } }) { contact ->
                                    CloudContactCard(
                                        contact = contact,
                                        onCall = {
                                            if (contact.phone.isNotBlank()) {
                                                try {
                                                    val dial = Intent(Intent.ACTION_DIAL, "tel:${contact.phone}".toUri())
                                                    context.startActivity(dial)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Cannot open dialer: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onSms = {
                                            if (contact.phone.isNotBlank()) {
                                                try {
                                                    val sms = Intent(Intent.ACTION_SENDTO, "smsto:${contact.phone}".toUri())
                                                    context.startActivity(sms)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Cannot open SMS: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onWhatsApp = {
                                            if (contact.phone.isNotBlank()) {
                                                onWhatsAppClick(contact.phone)
                                            }
                                        },
                                        onImport = {
                                            vm.importCloudContact(contact)
                                            Toast.makeText(context, "Saved '${contact.name}' into local contacts", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }

                                item {
                                    if (isLoadingMore) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = GlassCyan
                                                )
                                                Text(
                                                    "Loading more contacts (${contacts.size} of $totalContacts)...",
                                                    fontSize = 11.5.sp,
                                                    color = TextMuted
                                                )
                                            }
                                        }
                                    } else if (contacts.size < totalContacts) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            OutlinedButton(
                                                onClick = { vm.loadMoreCloudContacts() },
                                                border = BorderStroke(1.dp, GlassCyan.copy(alpha = 0.5f)),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    "Load More (${contacts.size} of $totalContacts)",
                                                    color = GlassCyan,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    } else if (contacts.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "Showing all $totalContacts cloud contacts",
                                                fontSize = 11.5.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Cloud SMS List
                        if (smsList.isEmpty() && !isLoading) {
                            EmptyCloudState(
                                icon = Icons.AutoMirrored.Filled.Chat,
                                title = if (searchQuery.isNotBlank()) "No SMS matching '$searchQuery'" else "No SMS messages found in cloud storage",
                                subtitle = "Sync device SMS from the SMS Ledger screen or check server logs."
                            )
                        } else {
                            LazyColumn(
                                state = smsListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(smsList, key = { it.id.ifBlank { "${it.address}_${it.date}" } }) { sms ->
                                    CloudSmsCard(
                                        sms = sms,
                                        onCopy = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("SMS Body", sms.body))
                                            Toast.makeText(context, "SMS text copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        onCall = {
                                            if (sms.address.isNotBlank()) {
                                                try {
                                                    val dial = Intent(Intent.ACTION_DIAL, "tel:${sms.address}".toUri())
                                                    context.startActivity(dial)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Cannot open dialer: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onSms = {
                                            if (sms.address.isNotBlank()) {
                                                try {
                                                    val smsIntent = Intent(Intent.ACTION_SENDTO, "smsto:${sms.address}".toUri())
                                                    context.startActivity(smsIntent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Cannot open SMS: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                }

                                item {
                                    if (isLoadingMore) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = GlassPurple
                                                )
                                                Text(
                                                    "Loading more SMS (${smsList.size} of $totalSms)...",
                                                    fontSize = 11.5.sp,
                                                    color = TextMuted
                                                )
                                            }
                                        }
                                    } else if (smsList.size < totalSms) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            OutlinedButton(
                                                onClick = { vm.loadMoreCloudSms() },
                                                border = BorderStroke(1.dp, GlassPurple.copy(alpha = 0.5f)),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    "Load More (${smsList.size} of $totalSms)",
                                                    color = GlassPurple,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    } else if (smsList.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "Showing all $totalSms cloud SMS logs",
                                                fontSize = 11.5.sp,
                                                color = TextMuted
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
}

@Composable
@Suppress("FunctionName") // Standard Jetpack Compose PascalCase UI component convention
private fun CloudStatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = TextMuted, maxLines = 1)
        }
    }
}

@Composable
@Suppress("FunctionName") // Standard Jetpack Compose PascalCase UI component convention
private fun CloudContactCard(
    contact: CloudContactItem,
    onCall: () -> Unit,
    onSms: () -> Unit,
    onWhatsApp: () -> Unit,
    onImport: () -> Unit
) {
    val formattedPhone = remember(contact.phone) {
        PhoneUtils.formatIndianPhone(contact.phone)
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Profile Row: Avatar, Name, Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF0284C7), GlassCyan))),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = contact.name.trim().take(1).uppercase()
                        Text(
                            text = initial.ifBlank { "?" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = contact.name.ifBlank { "Unnamed Contact" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (contact.saved) {
                                Spacer(Modifier.width(4.dp))
                                Text("⭐", fontSize = 12.sp)
                            }
                        }
                        Text(
                            text = formattedPhone.ifBlank { "No Phone" },
                            fontSize = 12.sp,
                            color = GlassCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (contact.deviceId.isNotBlank()) {
                    GlassBadge(
                        text = "📱 ${contact.deviceId}",
                        color = Color(0xFF38BDF8),
                        showDot = false
                    )
                }
            }

            // Loan Notes / Details
            if (contact.details.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x351E293B))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = contact.details,
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Actions Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (contact.phone.isNotBlank()) {
                    BentoActionButton(
                        icon = Icons.Default.Phone,
                        contentDescription = "Call",
                        tint = GlassMint,
                        onClick = onCall,
                        modifier = Modifier.weight(1f),
                        height = 36.dp
                    )
                    BentoActionButton(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "SMS",
                        tint = GlassCyan,
                        onClick = onSms,
                        modifier = Modifier.weight(1f),
                        height = 36.dp
                    )
                    BentoActionButton(
                        icon = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "WhatsApp",
                        tint = Color(0xFF25D366),
                        onClick = onWhatsApp,
                        modifier = Modifier.weight(1f),
                        height = 36.dp
                    )
                }

                // Import to Local
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0x3538BDF8), Color(0x356366F1))))
                        .border(BorderStroke(1.dp, GlassCyan.copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
                        .clickable(onClick = onImport),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = GlassCyan, modifier = Modifier.size(14.dp))
                        Text("Save Local", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlassCyan)
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionName") // Standard Jetpack Compose PascalCase UI component convention
private fun CloudSmsCard(
    sms: CloudSmsItem,
    onCopy: () -> Unit,
    onCall: () -> Unit,
    onSms: () -> Unit
) {
    val isInbox = sms.type.equals("INBOX", ignoreCase = true)
    val formattedDate = remember(sms.date) {
        if (sms.date > 0) {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            sdf.format(Date(sms.date))
        } else {
            "Unknown date"
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Address, Type, Device, Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = sms.address.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = GlassCyan
                    )
                    GlassBadge(
                        text = sms.type.uppercase(),
                        color = if (isInbox) GlassMint else GlassPurple,
                        showDot = false
                    )
                }

                if (sms.deviceId.isNotBlank()) {
                    Text(
                        "📱 ${sms.deviceId}",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Message Body Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x350F172A))
                    .border(BorderStroke(1.dp, Color(0x18FFFFFF)), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = sms.body,
                    fontSize = 12.5.sp,
                    color = TextPrimary,
                    lineHeight = 17.sp
                )
            }

            // Footer: Timestamp & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formattedDate,
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x3538BDF8))
                            .border(BorderStroke(1.dp, GlassCyan.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                            .clickable(onClick = onCopy)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = GlassCyan, modifier = Modifier.size(13.dp))
                            Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlassCyan)
                        }
                    }

                    if (sms.address.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x3510B981))
                                .border(BorderStroke(1.dp, GlassMint.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                                .clickable(onClick = onCall)
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Call", tint = GlassMint, modifier = Modifier.size(13.dp))
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x3538BDF8))
                                .border(BorderStroke(1.dp, GlassCyan.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                                .clickable(onClick = onSms)
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "SMS", tint = GlassCyan, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionName") // Standard Jetpack Compose PascalCase UI component convention
private fun EmptyCloudState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0x2538BDF8))
                    .border(BorderStroke(1.5.dp, GlassCyan.copy(alpha = 0.35f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = GlassCyan, modifier = Modifier.size(32.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
