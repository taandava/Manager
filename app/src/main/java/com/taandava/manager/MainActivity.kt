@file:Suppress("FunctionName")

package com.taandava.manager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taandava.manager.ui.*
import com.taandava.manager.ui.components.ManagerLogo
import com.taandava.manager.ui.components.WhatsAppChooserDialog
import com.taandava.manager.ui.screens.*
import androidx.activity.viewModels
import com.taandava.manager.util.WhatsAppHelper

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.handleIncomingIntent(intent)
        setContent {
            ManagerApp(vm)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleIncomingIntent(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val currentTab by vm.currentTab.collectAsState()
    val isSyncing by vm.isSyncing.collectAsState()
    val statusMsg by vm.statusMessage.collectAsState()
    val whatsAppPref by vm.whatsAppPref.collectAsState()

    val screenWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val isWideScreen = screenWidthDp >= 600.dp

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // WhatsApp chooser dialog state
    var whatsAppTargetPhone by remember { mutableStateOf<String?>(null) }
    val installedWhatsAppClients = remember(context) { WhatsAppHelper.getInstalledClients(context) }

    fun handleWhatsAppClick(rawPhone: String) {
        if (installedWhatsAppClients.size > 1) {
            when (whatsAppPref) {
                WhatsAppHelper.PKG_WHATSAPP -> WhatsAppHelper.launch(context, rawPhone, WhatsAppHelper.PKG_WHATSAPP)
                WhatsAppHelper.PKG_WHATSAPP_BUSINESS -> WhatsAppHelper.launch(context, rawPhone, WhatsAppHelper.PKG_WHATSAPP_BUSINESS)
                else -> whatsAppTargetPhone = rawPhone
            }
        } else if (installedWhatsAppClients.size == 1) {
            WhatsAppHelper.launch(context, rawPhone, installedWhatsAppClients[0].packageName)
        } else {
            WhatsAppHelper.launch(context, rawPhone, null)
        }
    }

    val duplicateGroups by vm.duplicateGroups.collectAsState()
    val isCloudViewerOpen by vm.isCloudViewerOpen.collectAsState()

    // Permissions launcher
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val contactsGranted = grants[Manifest.permission.READ_CONTACTS] == true
        val callLogGranted = grants[Manifest.permission.READ_CALL_LOG] == true
        val smsGranted = grants[Manifest.permission.READ_SMS] == true
        if (contactsGranted || callLogGranted) vm.autoScanDeviceContactsIfEmpty()
        if (smsGranted) vm.autoScanDeviceSmsIfEmpty()
    }

    // Default SMS Role Activity Result Launcher (Required by Android RoleManager)
    val smsRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        vm.refreshDefaultSmsRole(context)
        vm.refreshStats()
    }

    val onTriggerSetDefaultSms: () -> Unit = {
        val intent = com.taandava.manager.platform.SmsRoleHelper.getRequestRoleIntent(context)
        if (intent != null) {
            try {
                smsRoleLauncher.launch(intent)
            } catch (_: Exception) {
                com.taandava.manager.platform.SmsRoleHelper.openDefaultAppsSettings(context)
            }
        } else {
            com.taandava.manager.platform.SmsRoleHelper.openDefaultAppsSettings(context)
        }
    }

    LaunchedEffect(Unit) {
        vm.refreshDefaultSmsRole(context)
        val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasCallLog = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

        val needed = mutableListOf<String>()
        if (!hasContacts) {
            needed.add(Manifest.permission.READ_CONTACTS)
            needed.add(Manifest.permission.WRITE_CONTACTS)
        }
        if (!hasCallLog) {
            needed.add(Manifest.permission.READ_CALL_LOG)
        }
        if (!hasSms) {
            needed.add(Manifest.permission.READ_SMS)
        }
        if (needed.isNotEmpty()) {
            permLauncher.launch(needed.toTypedArray())
        } else {
            if (hasContacts || hasCallLog) vm.autoScanDeviceContactsIfEmpty()
            if (hasSms) vm.autoScanDeviceSmsIfEmpty()
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == 1 || currentTab == 4) {
            vm.refreshDefaultSmsRole(context)
        }
        if (currentTab == 1) {
            val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
            if (hasSms) {
                vm.autoScanDeviceSmsIfEmpty()
            }
        }
    }

    val destinations = listOf(
        Pair("Contacts", Icons.Default.People),
        Pair("SMS Ledger", Icons.AutoMirrored.Filled.Chat),
        Pair("Duplicates", Icons.AutoMirrored.Filled.CallMerge),
        Pair("Analytics", Icons.Default.BarChart),
        Pair("Settings", Icons.Default.Settings)
    )

    GlassBackground(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Surface(
                    color = Color(0x85090D16),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ManagerLogo(size = 34.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "MANAGER",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            statusMsg,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(onClick = { vm.openCloudDataViewer() }) {
                                    Icon(Icons.Default.CloudQueue, contentDescription = "View Cloud Data", tint = GlassCyan)
                                }
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp).padding(end = 8.dp),
                                        strokeWidth = 2.dp,
                                        color = GlassCyan
                                    )
                                } else {
                                    IconButton(onClick = { vm.syncNow() }) {
                                        Icon(Icons.Default.Sync, contentDescription = "Sync Cloud", tint = GlassCyan)
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                        // Specular border
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0x10FFFFFF), Color(0x4038BDF8), Color(0x10FFFFFF))
                                    )
                                )
                        )
                    }
                }
            },
            bottomBar = {
                if (!isWideScreen) {
                    Surface(
                        color = Color(0x95090D16),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0x10FFFFFF), Color(0x4038BDF8), Color(0x10FFFFFF))
                                        )
                                    )
                            )
                            NavigationBar(
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp
                            ) {
                                destinations.forEachIndexed { index, dest ->
                                    val isSelected = currentTab == index
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            vm.setTab(index)
                                        },
                                        icon = {
                                            BadgedBox(
                                                badge = {
                                                    if (index == 2 && duplicateGroups.isNotEmpty()) {
                                                        Badge(
                                                            containerColor = GlassAmber,
                                                            contentColor = Color(0xFF070B14)
                                                        ) {
                                                            Text(
                                                                text = if (duplicateGroups.size > 99) "99+" else duplicateGroups.size.toString(),
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    dest.second,
                                                    contentDescription = dest.first,
                                                    tint = if (isSelected) GlassCyan else TextMuted
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                dest.first,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) TextPrimary else TextMuted
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = Color(0x3038BDF8)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isWideScreen) {
                    Surface(color = Color(0x80090D16)) {
                        NavigationRail(containerColor = Color.Transparent) {
                            destinations.forEachIndexed { index, dest ->
                                val isSelected = currentTab == index
                                NavigationRailItem(
                                    selected = isSelected,
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        vm.setTab(index)
                                    },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (index == 2 && duplicateGroups.isNotEmpty()) {
                                                    Badge(
                                                        containerColor = GlassAmber,
                                                        contentColor = Color(0xFF070B14)
                                                    ) {
                                                        Text(
                                                            text = if (duplicateGroups.size > 99) "99+" else duplicateGroups.size.toString(),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                dest.second,
                                                contentDescription = dest.first,
                                                tint = if (isSelected) GlassCyan else TextMuted
                                            )
                                        }
                                    },
                                    label = { Text(dest.first, fontSize = 11.sp, color = if (isSelected) TextPrimary else TextMuted) },
                                    colors = NavigationRailItemDefaults.colors(indicatorColor = Color(0x3038BDF8))
                                )
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    when (currentTab) {
                        0 -> ContactsScreen(vm, onWhatsAppClick = { handleWhatsAppClick(it) })
                        1 -> SmsLedgerScreen(vm, onWhatsAppClick = { handleWhatsAppClick(it) }, onSetDefaultSms = onTriggerSetDefaultSms)
                        2 -> DuplicatesScreen(vm, onNavigateToContacts = { vm.setTab(0) })
                        3 -> AnalyticsScreen(vm)
                        4 -> SettingsScreen(vm, onSetDefaultSms = onTriggerSetDefaultSms)
                    }
                }
            }
        }
    }

    // WhatsApp Chooser Dialog (when both apps are installed on device)
    if (whatsAppTargetPhone != null) {
        WhatsAppChooserDialog(
            phone = whatsAppTargetPhone!!,
            clients = installedWhatsAppClients,
            onDismiss = { whatsAppTargetPhone = null },
            onChoose = { selectedPkg, rememberChoice ->
                if (rememberChoice) {
                    vm.setWhatsAppPref(selectedPkg)
                }
                WhatsAppHelper.launch(context, whatsAppTargetPhone!!, selectedPkg)
                whatsAppTargetPhone = null
            }
        )
    }

    // Cloud Data Explorer Dialog
    if (isCloudViewerOpen) {
        CloudDataViewerDialog(
            vm = vm,
            onDismiss = { vm.closeCloudDataViewer() },
            onWhatsAppClick = { handleWhatsAppClick(it) }
        )
    }
}
