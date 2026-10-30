package com.taandava.manager.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.taandava.manager.platform.SmsRoleHelper
import com.taandava.manager.ui.*
import com.taandava.manager.util.WhatsAppHelper

@Composable
fun SettingsScreen(vm: MainViewModel, onSetDefaultSms: () -> Unit = {}) {
    val context = LocalContext.current
    val cloudUrl by vm.cloudUrl.collectAsState()
    val cloudToken by vm.cloudToken.collectAsState()
    val diagnosticResult by vm.diagnosticResult.collectAsState()
    val whatsAppPref by vm.whatsAppPref.collectAsState()

    var inputUrl by remember { mutableStateOf(cloudUrl) }
    var inputToken by remember { mutableStateOf(cloudToken) }

    // CSV Launchers
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) vm.exportContactsToCsv(uri)
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importContactsFromCsv(uri)
    }

    // vCard (VCF) Launchers
    val exportVcfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-vcard")
    ) { uri ->
        if (uri != null) vm.exportContactsToVcf(uri)
    }

    val importVcfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importContactsFromVcf(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("⚙️ Cloud Configuration & Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)

        // WhatsApp Client Preference Card
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💬 WhatsApp Client Selection", fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Choose which client to open when tapping WhatsApp chat actions:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "ASK" to "Always Ask",
                        WhatsAppHelper.PKG_WHATSAPP to "WhatsApp",
                        WhatsAppHelper.PKG_WHATSAPP_BUSINESS to "WA Business"
                    ).forEach { (prefKey, label) ->
                        val isSelected = whatsAppPref == prefKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) GlassMint.copy(alpha = 0.25f) else Color(0x35131E33))
                                .border(BorderStroke(1.dp, if (isSelected) GlassMint else Color(0x25FFFFFF)), RoundedCornerShape(10.dp))
                                .clickable { vm.setWhatsAppPref(prefKey) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) GlassMint else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        val showDeviceBadge by vm.showDeviceBadge.collectAsState()
        val contactViewMode by vm.contactViewMode.collectAsState()

        // Contact Display & Grid Layout Settings
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🎨 Contact Display & Layout", fontWeight = FontWeight.Bold, color = TextPrimary)

                // Layout Selector
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Card Layout Style", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                        Text("List View (1 column) or Bento Grid (2 columns)", fontSize = 11.sp, color = TextMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("LIST" to "List View", "GRID" to "2-Col Grid").forEach { (mode, label) ->
                            val isSel = contactViewMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) GlassCyan.copy(alpha = 0.25f) else Color(0x35131E33))
                                    .border(BorderStroke(1.dp, if (isSel) GlassCyan else Color(0x25FFFFFF)), RoundedCornerShape(8.dp))
                                    .clickable { vm.setContactViewMode(mode) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) GlassCyan else TextSecondary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0x18FFFFFF), thickness = 0.5.dp)

                // Show Device Badge Switch
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Show Device Name Badge", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                        Text("Displays device model badge (e.g. 📱 SM-G935F)", fontSize = 11.sp, color = TextMuted)
                    }
                    Switch(
                        checked = showDeviceBadge,
                        onCheckedChange = { vm.setShowDeviceBadge(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = GlassCyan, checkedTrackColor = GlassCyan.copy(alpha = 0.4f))
                    )
                }
            }
        }

        // Hostinger Configuration Card
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("☁️ Hostinger Cloud Sync Engine", fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Backend index.php manages contacts.csv and sms.csv.", style = MaterialTheme.typography.bodySmall, color = TextMuted)

                GlassTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    placeholder = "Hostinger Sync URL",
                    modifier = Modifier.fillMaxWidth()
                )

                GlassTextField(
                    value = inputToken,
                    onValueChange = { inputToken = it },
                    placeholder = "Security Token",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPrimaryButton(
                        text = "Save Config",
                        onClick = { vm.updateCloudConfig(inputUrl, inputToken) }
                    )
                    GlassSecondaryButton(
                        text = "Ping Server",
                        onClick = { vm.pingServer() }
                    )
                }

                GlassPrimaryButton(
                    text = "🌐 Explore & Search Cloud Data",
                    onClick = { vm.openCloudDataViewer() },
                    modifier = Modifier.fillMaxWidth()
                )

                if (diagnosticResult != null) {
                    Text(
                        diagnosticResult!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (diagnosticResult!!.contains("OK")) GlassMint else GlassCoral,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // CSV & vCard File Backup & Restore Card
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📁 Backup & Restore (CSV / vCard)", fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Universal backup formats compatible with Excel, Google Contacts & iOS.", style = MaterialTheme.typography.bodySmall, color = TextMuted)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassSecondaryButton(
                        text = "Export CSV",
                        onClick = { exportCsvLauncher.launch("manager_contacts.csv") },
                        modifier = Modifier.weight(1f)
                    )
                    GlassSecondaryButton(
                        text = "Import CSV",
                        onClick = { importCsvLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "*/*")) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassSecondaryButton(
                        text = "Export vCard (.vcf)",
                        onClick = { exportVcfLauncher.launch("manager_contacts.vcf") },
                        modifier = Modifier.weight(1f)
                    )
                    GlassSecondaryButton(
                        text = "Import vCard (.vcf)",
                        onClick = { importVcfLauncher.launch(arrayOf("text/x-vcard", "text/vcard", "*/*")) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Device Management Card
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📲 Device Contacts & SMS Actions", fontWeight = FontWeight.Bold, color = TextPrimary)

                GlassSecondaryButton(
                    text = "Auto Scan Device Contacts",
                    onClick = { vm.scanDeviceContacts() },
                    modifier = Modifier.fillMaxWidth()
                )

                GlassSecondaryButton(
                    text = "Auto Scan Device SMS & Messages",
                    onClick = { vm.scanDeviceSms() },
                    modifier = Modifier.fillMaxWidth()
                )

                GlassSecondaryButton(
                    text = "Save ALL Contacts to Device Address Book",
                    onClick = { vm.saveAllToDevice() },
                    modifier = Modifier.fillMaxWidth()
                )

                GlassDangerButton(
                    text = "Purge SMS Headers & Invalid Contacts",
                    onClick = { vm.purgeInvalidContacts() },
                    modifier = Modifier.fillMaxWidth()
                )

                GlassSecondaryButton(
                    text = "Scan & Clean Device Duplicate Contacts",
                    onClick = { vm.cleanDeviceDuplicates() },
                    modifier = Modifier.fillMaxWidth()
                )

                GlassDangerButton(
                    text = "Clear Local Contact Cache",
                    onClick = { vm.clearLocalCache() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Permissions Card
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔒 System App Permissions", fontWeight = FontWeight.Bold, color = TextPrimary)
                val contactsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                val smsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                val isDefaultSmsRole by vm.isDefaultSmsRole.collectAsState()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Contacts Permission:", color = TextSecondary)
                    GlassBadge(text = if (contactsPerm) "GRANTED" else "DENIED", color = if (contactsPerm) GlassMint else GlassCoral, showDot = false)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SMS Read Permission:", color = TextSecondary)
                    GlassBadge(text = if (smsPerm) "GRANTED" else "DENIED", color = if (smsPerm) GlassMint else GlassCoral, showDot = false)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Default SMS Role (Device Delete):", color = TextSecondary)
                    GlassBadge(text = if (isDefaultSmsRole) "ACTIVE" else "INACTIVE", color = if (isDefaultSmsRole) GlassMint else GlassAmber, showDot = false)
                }

                if (!isDefaultSmsRole) {
                    GlassPrimaryButton(
                        text = "Set Manager as Default SMS App",
                        onClick = onSetDefaultSms,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                GlassSecondaryButton(
                    text = "Open System Default Apps Settings",
                    onClick = {
                        SmsRoleHelper.openDefaultAppsSettings(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                GlassSecondaryButton(
                    text = "Open App Info (Permissions & Details)",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
