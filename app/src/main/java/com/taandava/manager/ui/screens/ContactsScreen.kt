package com.taandava.manager.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.net.toUri
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.taandava.manager.data.ContactEntity
import com.taandava.manager.ui.*
import com.taandava.manager.ui.components.ContactEditDialog
import com.taandava.manager.util.PhoneUtils

fun handleSaveContactToDevice(
    context: Context,
    contact: ContactEntity,
    vm: MainViewModel,
    onRequestPermission: () -> Unit
) {
    val hasWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
    if (hasWrite) {
        vm.saveContactToDevice(contact) { success ->
            if (success) {
                Toast.makeText(context, "Saved '${contact.name}' directly to device contacts", Toast.LENGTH_SHORT).show()
            } else {
                openNativeSaveContactIntent(context, contact)
            }
        }
    } else {
        onRequestPermission()
        openNativeSaveContactIntent(context, contact)
    }
}

fun openNativeSaveContactIntent(context: Context, contact: ContactEntity) {
    try {
        val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
            putExtra(ContactsContract.Intents.Insert.NAME, contact.name)
            putExtra(ContactsContract.Intents.Insert.PHONE, contact.phone)
            if (contact.details.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.NOTES, contact.details)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Toast.makeText(context, "Opening contacts app to save '${contact.name}'", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open contacts app: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ContactsScreen(vm: MainViewModel, onWhatsAppClick: (String) -> Unit) {
    val context = LocalContext.current
    val writeContactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Contact permission granted. Tap to save directly.", Toast.LENGTH_SHORT).show()
        }
    }
    val contacts by vm.contacts.collectAsState()
    val query by vm.contactQuery.collectAsState()
    val savedOnly by vm.savedOnly.collectAsState()
    val sortOrder by vm.contactSortOrder.collectAsState()
    val selectedIds by vm.selectedContactIds.collectAsState()
    val isSelectionMode by vm.isContactSelectionMode.collectAsState()
    val showDeviceBadge by vm.showDeviceBadge.collectAsState()
    val contactViewMode by vm.contactViewMode.collectAsState()

    var editingContact by remember { mutableStateOf<ContactEntity?>(null) }
    var isNewContactDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val screenWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val isWideScreen = screenWidth >= 600.dp

    val gridColumns = when {
        contactViewMode == "GRID" -> {
            when {
                screenWidth >= 1200.dp -> GridCells.Fixed(4)
                screenWidth >= 840.dp -> GridCells.Fixed(3)
                else -> GridCells.Fixed(2)
            }
        }
        else -> {
            when {
                screenWidth >= 1200.dp -> GridCells.Fixed(2)
                else -> GridCells.Fixed(1)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (isWideScreen) {
            // Tablet / Landscape: Integrated Search & Toolbar Row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassTextField(
                    value = query,
                    onValueChange = { vm.updateContactQuery(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = "Search name, phone, notes...",
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GlassCyan) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.updateContactQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextPrimary)
                            }
                        }
                    }
                )

                // Star Filter Toggle
                IconButton(
                    onClick = { vm.toggleSavedOnly() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (savedOnly) GlassGold.copy(alpha = 0.2f) else Color(0x40131E33))
                        .border(BorderStroke(1.dp, if (savedOnly) GlassGold else Color(0x30FFFFFF)), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        if (savedOnly) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Starred",
                        tint = if (savedOnly) GlassGold else TextMuted
                    )
                }

                // Sort Menu Button
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x40131E33))
                            .border(BorderStroke(1.dp, Color(0x30FFFFFF)), RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = GlassCyan)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(Color(0xF00F172A))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name (A to Z)", color = if (sortOrder == "NAME_ASC") GlassCyan else TextPrimary) },
                            onClick = { vm.setContactSortOrder("NAME_ASC"); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Name (Z to A)", color = if (sortOrder == "NAME_DESC") GlassCyan else TextPrimary) },
                            onClick = { vm.setContactSortOrder("NAME_DESC"); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Newest Added First", color = if (sortOrder == "NEWEST") GlassCyan else TextPrimary) },
                            onClick = { vm.setContactSortOrder("NEWEST"); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Oldest Added First", color = if (sortOrder == "OLDEST") GlassCyan else TextPrimary) },
                            onClick = { vm.setContactSortOrder("OLDEST"); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Starred First", color = if (sortOrder == "STARRED_FIRST") GlassCyan else TextPrimary) },
                            onClick = { vm.setContactSortOrder("STARRED_FIRST"); showSortMenu = false }
                        )
                        HorizontalDivider(color = Color(0x20FFFFFF), modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Show Device Badge", color = TextPrimary, fontSize = 13.sp)
                                    Switch(
                                        checked = showDeviceBadge,
                                        onCheckedChange = { vm.setShowDeviceBadge(it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = GlassCyan, checkedTrackColor = GlassCyan.copy(alpha = 0.4f))
                                    )
                                }
                            },
                            onClick = { vm.setShowDeviceBadge(!showDeviceBadge) }
                        )
                    }
                }

                // Layout View Mode Toggle Button
                IconButton(
                    onClick = { vm.toggleContactViewMode() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (contactViewMode == "GRID") GlassPurple.copy(alpha = 0.25f) else Color(0x40131E33))
                        .border(BorderStroke(1.dp, if (contactViewMode == "GRID") GlassPurple else Color(0x30FFFFFF)), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        if (contactViewMode == "GRID") Icons.Default.ViewAgenda else Icons.Default.GridView,
                        contentDescription = "Toggle Grid/List View",
                        tint = if (contactViewMode == "GRID") GlassPurple else TextMuted
                    )
                }

                // Selection Mode Toggle Button
                IconButton(
                    onClick = { vm.toggleContactSelectionMode() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelectionMode) GlassMint.copy(alpha = 0.25f) else Color(0x40131E33))
                        .border(BorderStroke(1.dp, if (isSelectionMode) GlassMint else Color(0x30FFFFFF)), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        if (isSelectionMode) Icons.Default.CheckCircle else Icons.Default.Checklist,
                        contentDescription = "Selection Mode",
                        tint = if (isSelectionMode) GlassMint else TextMuted
                    )
                }

                // Add Contact Button
                Button(
                    onClick = { isNewContactDialog = true },
                    modifier = Modifier.height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF070B14))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", color = Color(0xFF070B14), fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Mobile Portrait: Clean Two-Tier Layout (Full-width Search Bar + Responsive Action Row)
            GlassTextField(
                value = query,
                onValueChange = { vm.updateContactQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Search name, phone, notes...",
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GlassCyan) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.updateContactQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextPrimary)
                        }
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            // Action & Quick Filter Row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Star Filter Toggle Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (savedOnly) GlassGold.copy(alpha = 0.22f) else Color(0x35131E33))
                        .border(BorderStroke(1.dp, if (savedOnly) GlassGold else Color(0x25FFFFFF)), RoundedCornerShape(12.dp))
                        .clickable { vm.toggleSavedOnly() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            if (savedOnly) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (savedOnly) GlassGold else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (savedOnly) "Starred" else "All",
                            fontSize = 12.sp,
                            fontWeight = if (savedOnly) FontWeight.Bold else FontWeight.Medium,
                            color = if (savedOnly) GlassGold else TextSecondary
                        )
                    }
                }

                // Action Controls on Right
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Sort Menu Button
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(0x40131E33))
                                .border(BorderStroke(1.dp, Color(0x30FFFFFF)), RoundedCornerShape(11.dp))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = GlassCyan, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(Color(0xF00F172A))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Name (A to Z)", color = if (sortOrder == "NAME_ASC") GlassCyan else TextPrimary) },
                                onClick = { vm.setContactSortOrder("NAME_ASC"); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Name (Z to A)", color = if (sortOrder == "NAME_DESC") GlassCyan else TextPrimary) },
                                onClick = { vm.setContactSortOrder("NAME_DESC"); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Newest Added First", color = if (sortOrder == "NEWEST") GlassCyan else TextPrimary) },
                                onClick = { vm.setContactSortOrder("NEWEST"); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Oldest Added First", color = if (sortOrder == "OLDEST") GlassCyan else TextPrimary) },
                                onClick = { vm.setContactSortOrder("OLDEST"); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Starred First", color = if (sortOrder == "STARRED_FIRST") GlassCyan else TextPrimary) },
                                onClick = { vm.setContactSortOrder("STARRED_FIRST"); showSortMenu = false }
                            )
                            HorizontalDivider(color = Color(0x20FFFFFF), modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Show Device Badge", color = TextPrimary, fontSize = 13.sp)
                                        Switch(
                                            checked = showDeviceBadge,
                                            onCheckedChange = { vm.setShowDeviceBadge(it) },
                                            colors = SwitchDefaults.colors(checkedThumbColor = GlassCyan, checkedTrackColor = GlassCyan.copy(alpha = 0.4f))
                                        )
                                    }
                                },
                                onClick = { vm.setShowDeviceBadge(!showDeviceBadge) }
                            )
                        }
                    }

                    // Layout View Mode Toggle Button (List vs 2-Column Grid)
                    IconButton(
                        onClick = { vm.toggleContactViewMode() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (contactViewMode == "GRID") GlassPurple.copy(alpha = 0.25f) else Color(0x40131E33))
                            .border(BorderStroke(1.dp, if (contactViewMode == "GRID") GlassPurple else Color(0x30FFFFFF)), RoundedCornerShape(11.dp))
                    ) {
                        Icon(
                            if (contactViewMode == "GRID") Icons.Default.ViewAgenda else Icons.Default.GridView,
                            contentDescription = "Toggle Grid/List View",
                            tint = if (contactViewMode == "GRID") GlassPurple else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Selection Mode Toggle Button
                    IconButton(
                        onClick = { vm.toggleContactSelectionMode() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isSelectionMode) GlassMint.copy(alpha = 0.25f) else Color(0x40131E33))
                            .border(BorderStroke(1.dp, if (isSelectionMode) GlassMint else Color(0x30FFFFFF)), RoundedCornerShape(11.dp))
                    ) {
                        Icon(
                            if (isSelectionMode) Icons.Default.CheckCircle else Icons.Default.Checklist,
                            contentDescription = "Selection Mode",
                            tint = if (isSelectionMode) GlassMint else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Add Contact Button
                    Button(
                        onClick = { isNewContactDialog = true },
                        modifier = Modifier.height(38.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassCyan),
                        shape = RoundedCornerShape(11.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF070B14), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF070B14))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Batch Action Toolbar (When in Selection Mode)
        if (isSelectionMode) {
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                borderColor = GlassMint.copy(alpha = 0.5f)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedIds.size} Selected", fontWeight = FontWeight.Bold, color = GlassMint, fontSize = 12.sp)

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.selectAllContacts() }) {
                            Text("All", fontSize = 11.sp, color = GlassCyan)
                        }
                        TextButton(onClick = { vm.deselectAllContacts() }) {
                            Text("None", fontSize = 11.sp, color = TextMuted)
                        }
                        if (selectedIds.isNotEmpty()) {
                            IconButton(onClick = { vm.starSelectedContacts(true) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Star, contentDescription = "Star Selected", tint = GlassGold, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { showBatchDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = GlassCoral, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        } else {
            // Normal Info Banner
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${contacts.size} contacts found" + if (savedOnly) " (Starred only)" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                GlassBadge(
                    text = "Cloud: contacts.csv",
                    color = GlassCyan,
                    showDot = false
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        if (contacts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassCard(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PeopleOutline, contentDescription = null, tint = GlassCyan, modifier = Modifier.size(44.dp))
                        Text("No Contacts Found", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            "Tap + Add to add a contact, import from CSV/vCard, or scan from Device in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
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
                items(contacts, key = { it.id }) { c ->
                    val isChecked = selectedIds.contains(c.id)
                    if (contactViewMode == "GRID") {
                        ContactGridCardItem(
                            contact = c,
                            isSelectionMode = isSelectionMode,
                            isSelected = isChecked,
                            showDeviceBadge = showDeviceBadge,
                            onSelectToggle = { vm.toggleContactSelection(c.id) },
                            onStarToggle = { vm.toggleContactStar(c) },
                            onEdit = { editingContact = c },
                            onDelete = { vm.deleteContact(c) },
                            onCall = {
                                val intent = Intent(Intent.ACTION_DIAL, "tel:${c.phone}".toUri())
                                context.startActivity(intent)
                            },
                            onSms = {
                                val intent = Intent(Intent.ACTION_SENDTO, "smsto:${c.phone}".toUri())
                                context.startActivity(intent)
                            },
                            onWhatsApp = {
                                onWhatsAppClick(c.phone)
                            },
                            onSaveToDevice = {
                                handleSaveContactToDevice(context, c, vm) {
                                    writeContactsLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                                }
                            },
                            onShare = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${c.name}\n${PhoneUtils.formatIndianPhone(c.phone)}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Contact"))
                            },
                            onCopy = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("Phone", c.phone))
                                Toast.makeText(context, "Copied phone number", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        ContactCardItem(
                            contact = c,
                            isSelectionMode = isSelectionMode,
                            isSelected = isChecked,
                            showDeviceBadge = showDeviceBadge,
                            onSelectToggle = { vm.toggleContactSelection(c.id) },
                            onStarToggle = { vm.toggleContactStar(c) },
                            onEdit = { editingContact = c },
                            onDelete = { vm.deleteContact(c) },
                            onCall = {
                                val intent = Intent(Intent.ACTION_DIAL, "tel:${c.phone}".toUri())
                                context.startActivity(intent)
                            },
                            onSms = {
                                val intent = Intent(Intent.ACTION_SENDTO, "smsto:${c.phone}".toUri())
                                context.startActivity(intent)
                            },
                            onWhatsApp = {
                                onWhatsAppClick(c.phone)
                            },
                            onSaveToDevice = {
                                handleSaveContactToDevice(context, c, vm) {
                                    writeContactsLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                                }
                            },
                            onShare = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${c.name}\n${PhoneUtils.formatIndianPhone(c.phone)}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Contact"))
                            }
                        )
                    }
                }
            }
        }
    }

    if (editingContact != null) {
        ContactEditDialog(
            contact = editingContact!!,
            isNew = false,
            onDismiss = { editingContact = null },
            onSave = { updated ->
                vm.saveContact(updated)
                editingContact = null
            }
        )
    }

    if (isNewContactDialog) {
        ContactEditDialog(
            contact = ContactEntity(),
            isNew = true,
            onDismiss = { isNewContactDialog = false },
            onSave = { newC ->
                vm.saveContact(newC)
                isNewContactDialog = false
            }
        )
    }

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            containerColor = Color(0xF20F172A),
            shape = RoundedCornerShape(22.dp),
            title = { Text("Delete Selected Contacts", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("Permanently delete ${selectedIds.size} selected contacts?", color = TextSecondary) },
            confirmButton = {
                GlassDangerButton(
                    text = "Delete",
                    onClick = {
                        vm.deleteSelectedContacts()
                        showBatchDeleteConfirm = false
                    }
                )
            },
            dismissButton = {
                GlassSecondaryButton(text = "Cancel", onClick = { showBatchDeleteConfirm = false })
            }
        )
    }
}

@Composable
fun ContactCardItem(
    contact: ContactEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    showDeviceBadge: Boolean = false,
    onSelectToggle: () -> Unit,
    onStarToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit,
    onSms: () -> Unit,
    onWhatsApp: () -> Unit,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val cleanDetails = contact.details.trim()
    val isDeviceDuplicate = cleanDetails.startsWith("Device:", ignoreCase = true) ||
                            cleanDetails.equals(contact.deviceId, ignoreCase = true) ||
                            (contact.deviceId.isNotBlank() && cleanDetails.contains(contact.deviceId, ignoreCase = true))

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (isSelectionMode) onSelectToggle else null
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Avatar, Contact Name, and Star Button on top right
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() },
                        colors = CheckboxDefaults.colors(checkedColor = GlassMint),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                // Avatar Circle with initial letter
                val initial = contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(GlassCyan.copy(alpha = 0.25f), Color(0x30131E33))
                            )
                        )
                        .border(BorderStroke(1.dp, GlassCyan.copy(alpha = 0.45f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        fontWeight = FontWeight.Bold,
                        color = GlassCyan,
                        fontSize = 16.sp
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Contact Name: Full width across header, wraps up to 2 lines so it is NEVER truncated!
                    Text(
                        text = contact.name.ifBlank { "Unnamed Contact" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Phone Number Row (and optional Device badge if enabled)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = PhoneUtils.formatIndianPhone(contact.phone),
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassCyan,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )

                        // Device badge is optional and only shown when enabled in Settings/Menu!
                        if (showDeviceBadge && contact.deviceId.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x351E293B))
                                    .border(BorderStroke(0.5.dp, Color(0x30FFFFFF)), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "📱 ${contact.deviceId}",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Favorite Star Button on the top right
                IconButton(onClick = onStarToggle, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (contact.saved) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Star",
                        tint = if (contact.saved) GlassGold else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Clean Details (Notes only - never duplicate device name!)
            if (cleanDetails.isNotBlank() && !isDeviceDuplicate) {
                Text(
                    text = cleanDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = Color(0x18FFFFFF), thickness = 0.5.dp)

            // Action Buttons Row: Bigger and aligned width to width across the card
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Call
                BentoActionButton(
                    icon = Icons.Default.Phone,
                    contentDescription = "Call",
                    tint = GlassMint,
                    onClick = onCall,
                    modifier = Modifier.weight(1f)
                )
                // 2. SMS
                BentoActionButton(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "SMS",
                    tint = GlassCyan,
                    onClick = onSms,
                    modifier = Modifier.weight(1f)
                )
                // 3. WhatsApp
                BentoActionButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "WhatsApp",
                    tint = GlassMint,
                    onClick = onWhatsApp,
                    modifier = Modifier.weight(1f)
                )
                // 4. Save to Device
                BentoActionButton(
                    icon = Icons.Default.PersonAdd,
                    contentDescription = "Save to Device",
                    tint = GlassMint,
                    onClick = onSaveToDevice,
                    modifier = Modifier.weight(1f)
                )
                // 5. Share
                BentoActionButton(
                    icon = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = GlassPurple,
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
                // 6. Copy Phone
                BentoActionButton(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = TextSecondary,
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Phone", contact.phone))
                        Toast.makeText(context, "Copied phone number", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )
                // 7. Edit
                BentoActionButton(
                    icon = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = GlassAmber,
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                )
                // 8. Delete
                BentoActionButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = GlassCoral,
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ContactGridCardItem(
    contact: ContactEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    showDeviceBadge: Boolean = false,
    onSelectToggle: () -> Unit,
    onStarToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit,
    onSms: () -> Unit,
    onWhatsApp: () -> Unit,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (isSelectionMode) onSelectToggle else null
    ) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Avatar on left | Checkbox & Star on right
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val initial = contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(GlassCyan.copy(alpha = 0.25f), Color(0x30131E33))
                            )
                        )
                        .border(BorderStroke(1.dp, GlassCyan.copy(alpha = 0.45f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        fontWeight = FontWeight.Bold,
                        color = GlassCyan,
                        fontSize = 14.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectToggle() },
                            colors = CheckboxDefaults.colors(checkedColor = GlassMint),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = onStarToggle, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (contact.saved) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Star",
                            tint = if (contact.saved) GlassGold else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Name & Number Block
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = contact.name.ifBlank { "Unnamed Contact" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = PhoneUtils.formatIndianPhone(contact.phone),
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassCyan,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (showDeviceBadge && contact.deviceId.isNotBlank()) {
                    Text(
                        text = "📱 ${contact.deviceId}",
                        fontSize = 9.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = Color(0x18FFFFFF), thickness = 0.5.dp)

            // Bottom Bento Actions (Call, WhatsApp, SMS, More)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call
                BentoActionButton(
                    icon = Icons.Default.Phone,
                    contentDescription = "Call",
                    tint = GlassMint,
                    onClick = onCall,
                    modifier = Modifier.weight(1f)
                )
                // WhatsApp
                BentoActionButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "WhatsApp",
                    tint = GlassMint,
                    onClick = onWhatsApp,
                    modifier = Modifier.weight(1f)
                )
                // SMS
                BentoActionButton(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "SMS",
                    tint = GlassCyan,
                    onClick = onSms,
                    modifier = Modifier.weight(1f)
                )
                // More (...)
                Box(modifier = Modifier.weight(1f)) {
                    BentoActionButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = TextSecondary,
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier.background(Color(0xF00F172A))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save to Device", color = GlassMint) },
                            leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = GlassMint) },
                            onClick = { showMoreMenu = false; onSaveToDevice() }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy Number", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextMuted) },
                            onClick = { showMoreMenu = false; onCopy() }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Contact", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = TextMuted) },
                            onClick = { showMoreMenu = false; onShare() }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Contact", color = GlassAmber) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = GlassAmber) },
                            onClick = { showMoreMenu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Contact", color = GlassCoral) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = GlassCoral) },
                            onClick = { showMoreMenu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}
