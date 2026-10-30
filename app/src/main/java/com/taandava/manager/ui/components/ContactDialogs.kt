package com.taandava.manager.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taandava.manager.data.*
import com.taandava.manager.ui.*
import com.taandava.manager.util.PhoneUtils
import com.taandava.manager.util.WhatsAppClient
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WhatsAppChooserDialog(
    phone: String,
    clients: List<WhatsAppClient>,
    onDismiss: () -> Unit,
    onChoose: (packageName: String, rememberChoice: Boolean) -> Unit
) {
    var rememberChoice by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20F172A),
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GlassMint))
                Spacer(Modifier.width(8.dp))
                Text("Select WhatsApp Client", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Choose which app to open for ${PhoneUtils.formatIndianPhone(phone)}:",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                clients.forEach { client ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onChoose(client.packageName, rememberChoice) }
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = if (client.isBusiness) GlassCyan else GlassMint,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(client.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(
                                        if (client.isBusiness) "WhatsApp Business" else "Personal WhatsApp",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { rememberChoice = !rememberChoice }
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it },
                        colors = CheckboxDefaults.colors(checkedColor = GlassCyan)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Always use this choice (can change in Settings)", fontSize = 12.sp, color = TextSecondary)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            GlassSecondaryButton(text = "Cancel", onClick = onDismiss)
        }
    )
}

@Composable
fun ContactEditDialog(
    contact: ContactEntity,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ContactEntity) -> Unit
) {
    var name by remember { mutableStateOf(contact.name) }
    var phone by remember { mutableStateOf(contact.phone) }
    var details by remember { mutableStateOf(contact.details) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20F172A),
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                if (isNew) "Add Contact" else "Edit Contact",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Full Name",
                    modifier = Modifier.fillMaxWidth()
                )
                GlassTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    placeholder = "Phone Number (e.g. 9876543210)",
                    modifier = Modifier.fillMaxWidth()
                )
                GlassTextField(
                    value = details,
                    onValueChange = { details = it },
                    placeholder = "Notes / Details (Optional)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
            }
        },
        confirmButton = {
            GlassPrimaryButton(
                text = "Save",
                onClick = {
                    onSave(
                        contact.copy(
                            name = name.trim(),
                            phone = PhoneUtils.normalizePhone(phone),
                            details = details.trim(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            )
        },
        dismissButton = {
            GlassSecondaryButton(text = "Cancel", onClick = onDismiss)
        }
    )
}

@Composable
fun SmsInspectorDialog(
    sms: SmsRecordEntity,
    onDismiss: () -> Unit,
    onWhatsApp: (String) -> Unit
) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val isMobileNumber = PhoneUtils.normalizePhone(sms.address).length >= 10

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20F172A),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape).background(GlassCyan)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (sms.type == 1) "📥 Received SMS Details" else "📤 Sent SMS Details",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Address / Sender:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(sms.address, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassBadge(text = sms.category, color = GlassCyan, showDot = false)
                            GlassBadge(text = sms.senderType, color = GlassIndigo, showDot = false)
                            if (!sms.extractedAmount.isNullOrBlank()) {
                                GlassBadge(
                                    text = "${if (sms.isDebit == true) "-" else "+"}${sms.extractedAmount}",
                                    color = if (sms.isDebit == true) GlassCoral else GlassMint,
                                    showDot = false
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Date & Time: ${sdf.format(Date(sms.date))}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                if (!sms.extractedOtp.isNullOrBlank()) {
                    GlassCard(
                        Modifier.fillMaxWidth(),
                        borderColor = GlassPurple,
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("OTP", sms.extractedOtp))
                            Toast.makeText(context, "Copied OTP: ${sms.extractedOtp}", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Intelligent OTP Extracted:", fontSize = 11.sp, color = TextMuted)
                                Text(sms.extractedOtp, fontSize = 20.sp, fontWeight = FontWeight.Black, color = GlassPurple)
                            }
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy OTP", tint = GlassPurple)
                        }
                    }
                }

                Text("Message Body:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        text = sms.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isMobileNumber) {
                    IconButton(onClick = { onWhatsApp(sms.address) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "WhatsApp", tint = GlassMint)
                    }
                }
                GlassPrimaryButton(
                    text = "Copy Text",
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("SMS Body", sms.body))
                        Toast.makeText(context, "Copied SMS text to clipboard", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
            }
        },
        dismissButton = {
            GlassSecondaryButton(text = "Close", onClick = onDismiss)
        }
    )
}

@Composable
fun SmartMergeConfigDialog(
    targetPhone: String?,
    groupCount: Int,
    initialConfig: SmartMergeConfig,
    onDismiss: () -> Unit,
    onConfirm: (SmartMergeConfig) -> Unit
) {
    var nameStrategy by remember { mutableStateOf(initialConfig.nameStrategy) }
    var notesStrategy by remember { mutableStateOf(initialConfig.notesStrategy) }
    var preserveStarred by remember { mutableStateOf(initialConfig.preserveStarred) }
    var combineSources by remember { mutableStateOf(initialConfig.combineSources) }
    var filterSpamInName by remember { mutableStateOf(initialConfig.filterSpamInName) }
    var rememberDefaults by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF50D1527),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassCyan.copy(alpha = 0.2f))
                        .border(BorderStroke(1.dp, GlassCyan.copy(alpha = 0.5f)), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = GlassCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        if (targetPhone == null) "Smart Merge All ($groupCount Groups)" else "Smart Merge Group",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        if (targetPhone == null)
                            "Choose merge rules for all duplicate groups"
                        else
                            PhoneUtils.formatIndianPhone(targetPhone),
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassCyan
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Name Resolution
                Text(
                    "NAME RESOLUTION STRATEGY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlassCyan
                )

                MergeNameStrategy.entries.forEach { strategy ->
                    val isSelected = nameStrategy == strategy
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) GlassCyan.copy(alpha = 0.15f) else Color(0x20FFFFFF))
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isSelected) GlassCyan else Color(0x20FFFFFF)
                                ),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { nameStrategy = strategy }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { nameStrategy = strategy },
                                colors = RadioButtonDefaults.colors(selectedColor = GlassCyan)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    strategy.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                                Text(
                                    strategy.description,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0x20FFFFFF), thickness = 0.5.dp)

                // Section 2: Notes & Details Resolution
                Text(
                    "NOTES & DETAILS STRATEGY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlassMint
                )

                MergeNotesStrategy.entries.forEach { strategy ->
                    val isSelected = notesStrategy == strategy
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) GlassMint.copy(alpha = 0.15f) else Color(0x20FFFFFF))
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isSelected) GlassMint else Color(0x20FFFFFF)
                                ),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { notesStrategy = strategy }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { notesStrategy = strategy },
                                colors = RadioButtonDefaults.colors(selectedColor = GlassMint)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    strategy.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                                Text(
                                    strategy.description,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0x20FFFFFF), thickness = 0.5.dp)

                // Section 3: Preservation Rules
                Text(
                    "PRESERVATION & CLEANUP OPTIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlassAmber
                )

                // Starred preservation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { preserveStarred = !preserveStarred }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Preserve Starred Status", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Mark survivor as favorite if any copy was starred", fontSize = 10.sp, color = TextMuted)
                    }
                    Switch(
                        checked = preserveStarred,
                        onCheckedChange = { preserveStarred = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = GlassGold, checkedTrackColor = GlassGold.copy(alpha = 0.3f))
                    )
                }

                // Sources combination
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { combineSources = !combineSources }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Combine Device & Sources", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Retain all device IDs and source provenance tags", fontSize = 10.sp, color = TextMuted)
                    }
                    Switch(
                        checked = combineSources,
                        onCheckedChange = { combineSources = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = GlassCyan, checkedTrackColor = GlassCyan.copy(alpha = 0.3f))
                    )
                }

                // Filter spam / SMS headers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { filterSpamInName = !filterSpamInName }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Filter Spam & Numbers In Name", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Ignore SMS headers (e.g., VM-HDFC) or phone digits as names", fontSize = 10.sp, color = TextMuted)
                    }
                    Switch(
                        checked = filterSpamInName,
                        onCheckedChange = { filterSpamInName = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = GlassMint, checkedTrackColor = GlassMint.copy(alpha = 0.3f))
                    )
                }

                // Remember preferences
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberDefaults = !rememberDefaults }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberDefaults,
                        onCheckedChange = { rememberDefaults = it },
                        colors = CheckboxDefaults.colors(checkedColor = GlassCyan)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Save as default merge settings", fontSize = 12.sp, color = TextSecondary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val config = SmartMergeConfig(
                        nameStrategy = nameStrategy,
                        notesStrategy = notesStrategy,
                        preserveStarred = preserveStarred,
                        combineSources = combineSources,
                        filterSpamInName = filterSpamInName
                    )
                    onConfirm(config)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GlassCyan, contentColor = Color(0xFF070B14)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (targetPhone == null) "Merge All Groups" else "Merge Group", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
