package com.taandava.manager.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import java.util.UUID
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taandava.manager.data.*
import com.taandava.manager.platform.ContactScanner
import com.taandava.manager.platform.SmsRoleHelper
import com.taandava.manager.platform.SmsScanner
import com.taandava.manager.sync.CloudSyncManager
import com.taandava.manager.util.ContactValidator
import com.taandava.manager.util.CsvParser
import com.taandava.manager.util.PhoneUtils
import com.taandava.manager.util.VcfParser
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext get() = getApplication<Application>()
    private val db = LocalDb.getInstance(application)
    private val contactScanner = ContactScanner(application)
    private val smsScanner = SmsScanner(application)
    private val syncManager = CloudSyncManager(application)
    private val prefs: SharedPreferences = application.getSharedPreferences("contact_finder", Context.MODE_PRIVATE)

    // Thread Safety Mutex & Coroutine Exception Handler
    private val dbMutex = Mutex()
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("MainViewModel", "Unhandled coroutine error: ${throwable.message}", throwable)
        _statusMessage.value = "Error: ${throwable.localizedMessage ?: "Operation failed"}"
    }

    // Navigation Tab: 0=Contacts, 1=SMS Ledger, 2=Duplicates, 3=Analytics, 4=Settings
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Contacts State
    private val _contacts = MutableStateFlow<List<ContactEntity>>(emptyList())
    val contacts: StateFlow<List<ContactEntity>> = _contacts.asStateFlow()

    private val _contactQuery = MutableStateFlow("")
    val contactQuery: StateFlow<String> = _contactQuery.asStateFlow()

    private val _savedOnly = MutableStateFlow(false)
    val savedOnly: StateFlow<Boolean> = _savedOnly.asStateFlow()

    // Contacts Sort Order: "NAME_ASC", "NAME_DESC", "NEWEST", "OLDEST", "STARRED_FIRST"
    private val _contactSortOrder = MutableStateFlow("NAME_ASC")
    val contactSortOrder: StateFlow<String> = _contactSortOrder.asStateFlow()

    // Contacts Multi-Selection
    private val _selectedContactIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedContactIds: StateFlow<Set<String>> = _selectedContactIds.asStateFlow()

    private val _isContactSelectionMode = MutableStateFlow(false)
    val isContactSelectionMode: StateFlow<Boolean> = _isContactSelectionMode.asStateFlow()

    // SMS Ledger State
    private val _smsRecords = MutableStateFlow<List<SmsRecordEntity>>(emptyList())
    val smsRecords: StateFlow<List<SmsRecordEntity>> = _smsRecords.asStateFlow()

    private val _smsQuery = MutableStateFlow("")
    val smsQuery: StateFlow<String> = _smsQuery.asStateFlow()

    private val _smsFilter = MutableStateFlow("ALL")
    val smsFilter: StateFlow<String> = _smsFilter.asStateFlow()

    private val _selectedSmsIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSmsIds: StateFlow<Set<Long>> = _selectedSmsIds.asStateFlow()

    // Duplicates State & Safe Threaded Deletion Tracking
    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups.asStateFlow()

    private val _sameNameGroups = MutableStateFlow<List<SameNameGroup>>(emptyList())
    val sameNameGroups: StateFlow<List<SameNameGroup>> = _sameNameGroups.asStateFlow()

    // 0 = Phone Duplicates, 1 = Same Name Disambiguation
    private val _duplicateTab = MutableStateFlow(0)
    val duplicateTab: StateFlow<Int> = _duplicateTab.asStateFlow()

    // Checkbox selections for Phone Duplicates and Same Name groups
    private val _selectedDuplicatePhones = MutableStateFlow<Set<String>>(emptySet())
    val selectedDuplicatePhones: StateFlow<Set<String>> = _selectedDuplicatePhones.asStateFlow()

    private val _selectedSameNameKeys = MutableStateFlow<Set<String>>(emptySet())
    val selectedSameNameKeys: StateFlow<Set<String>> = _selectedSameNameKeys.asStateFlow()

    private val _disambiguateSentenceCase = MutableStateFlow(true)
    val disambiguateSentenceCase: StateFlow<Boolean> = _disambiguateSentenceCase.asStateFlow()

    private val _isDisambiguating = MutableStateFlow(false)
    val isDisambiguating: StateFlow<Boolean> = _isDisambiguating.asStateFlow()

    private val _disambiguatingKeys = MutableStateFlow<Set<String>>(emptySet())
    val disambiguatingKeys: StateFlow<Set<String>> = _disambiguatingKeys.asStateFlow()

    private val _deletingContactIds = MutableStateFlow<Set<String>>(emptySet())
    val deletingContactIds: StateFlow<Set<String>> = _deletingContactIds.asStateFlow()

    private val _isDeduplicating = MutableStateFlow(false)
    val isDeduplicating: StateFlow<Boolean> = _isDeduplicating.asStateFlow()

    private val _mergingGroupPhones = MutableStateFlow<Set<String>>(emptySet())
    val mergingGroupPhones: StateFlow<Set<String>> = _mergingGroupPhones.asStateFlow()

    // Smart Merge Customization Configuration
    private val _smartMergeConfig = MutableStateFlow(loadSmartMergeConfig())
    val smartMergeConfig: StateFlow<SmartMergeConfig> = _smartMergeConfig.asStateFlow()

    private fun loadSmartMergeConfig(): SmartMergeConfig {
        return try {
            val nameStr = prefs.getString("sm_name_strategy", MergeNameStrategy.LONGEST.name) ?: MergeNameStrategy.LONGEST.name
            val notesStr = prefs.getString("sm_notes_strategy", MergeNotesStrategy.COMBINE_ALL.name) ?: MergeNotesStrategy.COMBINE_ALL.name
            SmartMergeConfig(
                nameStrategy = MergeNameStrategy.valueOf(nameStr),
                notesStrategy = MergeNotesStrategy.valueOf(notesStr),
                preserveStarred = prefs.getBoolean("sm_preserve_starred", true),
                combineSources = prefs.getBoolean("sm_combine_sources", true),
                filterSpamInName = prefs.getBoolean("sm_filter_spam", true)
            )
        } catch (_: Exception) {
            SmartMergeConfig()
        }
    }

    fun updateSmartMergeConfig(config: SmartMergeConfig) {
        _smartMergeConfig.value = config
        prefs.edit {
            putString("sm_name_strategy", config.nameStrategy.name)
            putString("sm_notes_strategy", config.notesStrategy.name)
            putBoolean("sm_preserve_starred", config.preserveStarred)
            putBoolean("sm_combine_sources", config.combineSources)
            putBoolean("sm_filter_spam", config.filterSpamInName)
        }
    }

    private var debouncedRefreshJob: Job? = null

    private fun scheduleDebouncedRefresh() {
        debouncedRefreshJob?.cancel()
        debouncedRefreshJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            delay(500.milliseconds)
            try {
                dbMutex.withLock {
                    refreshContacts()
                    refreshDuplicates()
                    refreshStats()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in debounced refresh: ${e.message}", e)
            }
        }
    }

    // Default SMS Role State
    private val _isDefaultSmsRole = MutableStateFlow(false)
    val isDefaultSmsRole: StateFlow<Boolean> = _isDefaultSmsRole.asStateFlow()

    // Analytics / Stats State
    private val _stats = MutableStateFlow(AppStats())
    val stats: StateFlow<AppStats> = _stats.asStateFlow()

    // Cloud / Sync State
    private val _cloudUrl = MutableStateFlow(prefs.getString("cloud_url", CloudSyncManager.DEFAULT_URL) ?: CloudSyncManager.DEFAULT_URL)
    val cloudUrl: StateFlow<String> = _cloudUrl.asStateFlow()

    private val _cloudToken = MutableStateFlow(prefs.getString("cloud_token", CloudSyncManager.DEFAULT_TOKEN) ?: CloudSyncManager.DEFAULT_TOKEN)
    val cloudToken: StateFlow<String> = _cloudToken.asStateFlow()

    // Cloud Data Explorer State
    private val _isCloudViewerOpen = MutableStateFlow(false)
    val isCloudViewerOpen: StateFlow<Boolean> = _isCloudViewerOpen.asStateFlow()

    private val _cloudViewerTab = MutableStateFlow(0) // 0 = Contacts, 1 = SMS
    val cloudViewerTab: StateFlow<Int> = _cloudViewerTab.asStateFlow()

    private val _cloudSearchQuery = MutableStateFlow("")
    val cloudSearchQuery: StateFlow<String> = _cloudSearchQuery.asStateFlow()

    private val _cloudContacts = MutableStateFlow<List<CloudContactItem>>(emptyList())
    val cloudContacts: StateFlow<List<CloudContactItem>> = _cloudContacts.asStateFlow()

    private val _cloudTotalContacts = MutableStateFlow(0)
    val cloudTotalContacts: StateFlow<Int> = _cloudTotalContacts.asStateFlow()

    private val _cloudSms = MutableStateFlow<List<CloudSmsItem>>(emptyList())
    val cloudSms: StateFlow<List<CloudSmsItem>> = _cloudSms.asStateFlow()

    private val _cloudTotalSms = MutableStateFlow(0)
    val cloudTotalSms: StateFlow<Int> = _cloudTotalSms.asStateFlow()

    private val _cloudOverview = MutableStateFlow<CloudOverview?>(null)
    val cloudOverview: StateFlow<CloudOverview?> = _cloudOverview.asStateFlow()

    private val _isCloudLoading = MutableStateFlow(false)
    val isCloudLoading: StateFlow<Boolean> = _isCloudLoading.asStateFlow()

    private val _isCloudLoadingMore = MutableStateFlow(false)
    val isCloudLoadingMore: StateFlow<Boolean> = _isCloudLoadingMore.asStateFlow()

    private val _cloudErrorMessage = MutableStateFlow<String?>(null)
    val cloudErrorMessage: StateFlow<String?> = _cloudErrorMessage.asStateFlow()

    private var cloudSearchJob: Job? = null

    // WhatsApp Client Preference: "ASK", "com.whatsapp", "com.whatsapp.w4b"
    private val _whatsAppPref = MutableStateFlow(prefs.getString("whatsapp_client_pref", "ASK") ?: "ASK")
    val whatsAppPref: StateFlow<String> = _whatsAppPref.asStateFlow()

    // Device Badge Preference: false = hidden (default, keeps contact name full & clean), true = show
    private val _showDeviceBadge = MutableStateFlow(prefs.getBoolean("show_device_badge", false))
    val showDeviceBadge: StateFlow<Boolean> = _showDeviceBadge.asStateFlow()

    fun setShowDeviceBadge(show: Boolean) {
        _showDeviceBadge.value = show
        prefs.edit { putBoolean("show_device_badge", show) }
    }

    // Contacts View Mode: "LIST" (1 column) vs "GRID" (2 columns)
    private val _contactViewMode = MutableStateFlow(prefs.getString("contact_view_mode", "LIST") ?: "LIST")
    val contactViewMode: StateFlow<String> = _contactViewMode.asStateFlow()

    fun setContactViewMode(mode: String) {
        _contactViewMode.value = mode
        prefs.edit { putString("contact_view_mode", mode) }
    }

    fun toggleContactViewMode() {
        val next = if (_contactViewMode.value == "GRID") "LIST" else "GRID"
        setContactViewMode(next)
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isScanningSms = MutableStateFlow(false)
    val isScanningSms: StateFlow<Boolean> = _isScanningSms.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _diagnosticResult = MutableStateFlow<String?>(null)
    val diagnosticResult: StateFlow<String?> = _diagnosticResult.asStateFlow()

    init {
        loadAll()
    }

    fun setTab(tab: Int) {
        _currentTab.value = tab
    }

    fun loadAll() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                dbMutex.withLock {
                    val purged = db.purgeInvalidContacts()
                    if (purged > 0) {
                        _statusMessage.value = "Cleaned $purged invalid number contacts"
                    }
                    refreshContacts()
                    refreshSms()
                    refreshDuplicates()
                    refreshStats()
                }
                autoSyncCloudInBackground()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in loadAll: ${e.message}", e)
            }
        }
    }

    private fun autoSyncCloudInBackground() {
        if (_isSyncing.value || _isDeduplicating.value) return
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                _isSyncing.value = true
                val devId = getDeviceModelName()
                val (pushed, pulled, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                val (smsPushed, _) = syncManager.syncSms(_cloudUrl.value, _cloudToken.value, devId)
                if (pushed > 0 || pulled > 0 || smsPushed > 0) {
                    _statusMessage.value = "Auto-synced: Contacts (+$pushed, -$pulled) | SMS: +$smsPushed"
                    refreshContacts()
                    refreshSms()
                    refreshDuplicates()
                    refreshStats()
                }
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "Auto-sync deferred: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun handleIncomingIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val action = intent.action
        val uri = intent.data

        // 1. Direct tab navigation (e.g., from SMS notification)
        val targetTab = intent.getIntExtra("tab", -1)
        if (targetTab in 0..4) {
            _currentTab.value = targetTab
        }

        val senderExtra = intent.getStringExtra("sender")
        if (!senderExtra.isNullOrBlank()) {
            _smsQuery.value = senderExtra
            _currentTab.value = 1 // SMS Ledger
            viewModelScope.launch(Dispatchers.IO) { refreshSms() }
        }

        // 2. Handle Default SMS handler SENDTO intent filter (smsto:, sms:, mms:, mmsto:)
        if (android.content.Intent.ACTION_SENDTO == action) {
            val scheme = uri?.scheme?.lowercase(java.util.Locale.ROOT)
            if (scheme in listOf("sms", "smsto", "mms", "mmsto")) {
                val recipient = uri?.schemeSpecificPart ?: ""
                val body = intent.getStringExtra("sms_body") ?: intent.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
                if (recipient.isNotBlank()) {
                    _smsQuery.value = PhoneUtils.normalizePhone(recipient)
                    _currentTab.value = 1
                    _statusMessage.value = "SMS compose request: $recipient"
                    viewModelScope.launch(Dispatchers.IO) { refreshSms() }
                }
            }
        } else if (android.content.Intent.ACTION_SEND == action) {
            // 3. Handle shared content intent filter
            val sharedText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
            if (sharedText.isNotBlank()) {
                val norm = PhoneUtils.normalizePhone(sharedText)
                if (norm.length >= 10) {
                    _contactQuery.value = norm
                    _currentTab.value = 0
                    _statusMessage.value = "Shared phone: $norm"
                    viewModelScope.launch(Dispatchers.IO) { refreshContacts() }
                }
            }
        }
    }

    // =========================================================================
    // CONTACTS ACTIONS
    // =========================================================================

    fun updateContactQuery(q: String) {
        _contactQuery.value = q
        viewModelScope.launch(Dispatchers.IO) { refreshContacts() }
    }

    fun toggleSavedOnly() {
        _savedOnly.value = !_savedOnly.value
        viewModelScope.launch(Dispatchers.IO) { refreshContacts() }
    }

    fun setContactSortOrder(order: String) {
        _contactSortOrder.value = order
        viewModelScope.launch(Dispatchers.IO) { refreshContacts() }
    }

    fun toggleContactSelectionMode() {
        _isContactSelectionMode.value = !_isContactSelectionMode.value
        if (!_isContactSelectionMode.value) {
            _selectedContactIds.value = emptySet()
        }
    }

    fun toggleContactSelection(id: String) {
        val cur = _selectedContactIds.value.toMutableSet()
        if (cur.contains(id)) cur.remove(id) else cur.add(id)
        _selectedContactIds.value = cur
        if (cur.isNotEmpty() && !_isContactSelectionMode.value) {
            _isContactSelectionMode.value = true
        }
    }

    fun selectAllContacts() {
        _selectedContactIds.value = _contacts.value.map { it.id }.toSet()
        _isContactSelectionMode.value = true
    }

    fun deselectAllContacts() {
        _selectedContactIds.value = emptySet()
        _isContactSelectionMode.value = false
    }

    fun deleteSelectedContacts() {
        val ids = _selectedContactIds.value.toList()
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        _selectedContactIds.value = emptySet()
        _isContactSelectionMode.value = false

        // Optimistic UI update
        _contacts.value = _contacts.value.filterNot { idSet.contains(it.id) }
        val currentGroups = _duplicateGroups.value
        val updatedGroups = currentGroups.mapNotNull { group ->
            val remaining = group.contacts.filterNot { idSet.contains(it.id) }
            if (remaining.size > 1) group.copy(contacts = remaining) else null
        }
        _duplicateGroups.value = updatedGroups
        val curStats = _stats.value
        _stats.value = curStats.copy(
            totalContacts = (curStats.totalContacts - ids.size).coerceAtLeast(0),
            totalDuplicates = updatedGroups.size
        )

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val deletedList = dbMutex.withLock {
                    db.deleteContacts(ids, soft = true)
                }
                for (contact in deletedList) {
                    contactScanner.deleteDeviceContactsByPhone(contact.phone)
                }
                val devId = getDeviceModelName()
                syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                _statusMessage.value = "Deleted ${deletedList.size} contacts on device and cloud database"
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error deleting contacts: ${e.message}", e)
            } finally {
                scheduleDebouncedRefresh()
            }
        }
    }

    fun starSelectedContacts(star: Boolean) {
        val ids = _selectedContactIds.value.toList()
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        _selectedContactIds.value = emptySet()
        _isContactSelectionMode.value = false

        // Optimistic UI update
        _contacts.value = _contacts.value.map {
            if (idSet.contains(it.id)) it.copy(saved = star) else it
        }

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val count = dbMutex.withLock {
                    db.starContacts(ids, star)
                }
                _statusMessage.value = "${if (star) "Starred" else "Unstarred"} $count contacts"
                scheduleDebouncedRefresh()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error starring contacts: ${e.message}", e)
            }
        }
    }

    fun saveContact(c: ContactEntity) {
        val updated = c.copy(edited = true, syncStatus = "PENDING", updatedAt = System.currentTimeMillis())
        _contacts.value = _contacts.value.map { if (it.id == updated.id) updated else it }

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                dbMutex.withLock {
                    db.upsertContact(updated)
                    refreshContacts()
                    refreshDuplicates()
                    refreshStats()
                }
                val devId = getDeviceModelName()
                val (pushed, _, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                if (pushed > 0) {
                    _statusMessage.value = "Saved '${c.name.ifBlank { c.phone }}' and backed up to cloud"
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error saving contact: ${e.message}", e)
            }
        }
    }

    fun deleteContact(c: ContactEntity) {
        val contactId = c.id
        if (_deletingContactIds.value.contains(contactId)) return // Skip rapid double-taps on the same contact

        // 1. Mark as deleting
        _deletingContactIds.value += contactId

        // 2. OPTIMISTIC IN-MEMORY UI UPDATE (Instant 0ms feedback)
        val currentGroups = _duplicateGroups.value
        val updatedGroups = currentGroups.mapNotNull { group ->
            if (group.contacts.any { it.id == contactId }) {
                val remaining = group.contacts.filterNot { it.id == contactId }
                if (remaining.size > 1) {
                    group.copy(contacts = remaining)
                } else {
                    null // Deduplication resolved!
                }
            } else {
                group
            }
        }
        _duplicateGroups.value = updatedGroups
        _contacts.value = _contacts.value.filterNot { it.id == contactId }

        val curStats = _stats.value
        _stats.value = curStats.copy(
            totalContacts = (curStats.totalContacts - 1).coerceAtLeast(0),
            totalDuplicates = updatedGroups.size
        )

        // 3. BACKGROUND THREADED WRITE via Mutex
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                dbMutex.withLock {
                    db.deleteContact(contactId, soft = true)
                }
                contactScanner.deleteDeviceContactsByPhone(c.phone)
                val devId = getDeviceModelName()
                syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                _statusMessage.value = "Deleted '${c.name.ifBlank { c.phone }}' from device and cloud database"
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error deleting contact $contactId: ${e.message}", e)
            } finally {
                _deletingContactIds.value -= contactId
                scheduleDebouncedRefresh()
            }
        }
    }

    fun toggleContactStar(c: ContactEntity) {
        val updated = c.copy(saved = !c.saved, edited = true, syncStatus = "PENDING", updatedAt = System.currentTimeMillis())
        _contacts.value = _contacts.value.map { if (it.id == updated.id) updated else it }

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                dbMutex.withLock {
                    db.upsertContact(updated)
                    refreshContacts()
                    refreshStats()
                }
                val devId = getDeviceModelName()
                syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error toggling star: ${e.message}", e)
            }
        }
    }

    private fun refreshContacts() {
        val rawList = db.getAllContacts(savedOnly = _savedOnly.value, query = _contactQuery.value)
        val sorted = when (_contactSortOrder.value) {
            "NAME_DESC" -> rawList.sortedByDescending { it.name.lowercase() }
            "NEWEST" -> rawList.sortedByDescending { it.updatedAt }
            "OLDEST" -> rawList.sortedBy { it.updatedAt }
            "STARRED_FIRST" -> rawList.sortedWith(compareByDescending<ContactEntity> { it.saved }.thenBy { it.name.lowercase() })
            else -> rawList.sortedBy { it.name.lowercase() }
        }
        _contacts.value = sorted
    }

    // =========================================================================
    // SMS ACTIONS
    // =========================================================================

    fun updateSmsQuery(q: String) {
        _smsQuery.value = q
        viewModelScope.launch(Dispatchers.IO) { refreshSms() }
    }

    fun updateSmsFilter(f: String) {
        _smsFilter.value = f
        _selectedSmsIds.value = emptySet()
        viewModelScope.launch(Dispatchers.IO) { refreshSms() }
    }

    fun toggleSmsSelection(id: Long) {
        val current = _selectedSmsIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedSmsIds.value = current
    }

    fun selectAllSms() {
        _selectedSmsIds.value = _smsRecords.value.map { it.id }.toSet()
    }

    fun deselectAllSms() {
        _selectedSmsIds.value = emptySet()
    }

    fun deleteSelectedSms() {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = _selectedSmsIds.value.toList()
            val (deletedCount, deviceSmsIds) = db.deleteSmsRecords(ids)
            val devDeleted = SmsRoleHelper.deleteDeviceSms(appContext, deviceSmsIds)
            _selectedSmsIds.value = emptySet()
            val isDef = SmsRoleHelper.isDefaultSmsApp(appContext)
            _statusMessage.value = if (isDef && devDeleted > 0) {
                "Deleted $deletedCount SMS permanently from device storage & ledger"
            } else {
                "Deleted $deletedCount SMS from ledger"
            }
            refreshSms()
            refreshStats()
        }
    }

    fun deleteFilteredSms() {
        viewModelScope.launch(Dispatchers.IO) {
            val (deletedCount, deviceSmsIds) = db.deleteFilteredSmsRecords(_smsQuery.value, _smsFilter.value)
            val devDeleted = SmsRoleHelper.deleteDeviceSms(appContext, deviceSmsIds)
            _selectedSmsIds.value = emptySet()
            val isDef = SmsRoleHelper.isDefaultSmsApp(appContext)
            _statusMessage.value = if (isDef && devDeleted > 0) {
                "Deleted $deletedCount filtered SMS permanently from device storage & ledger"
            } else {
                "Deleted $deletedCount filtered SMS from ledger"
            }
            refreshSms()
            refreshStats()
        }
    }

    fun cleanSmsDupes() {
        viewModelScope.launch(Dispatchers.IO) {
            db.cleanDuplicateSms()
            _statusMessage.value = "SMS duplicates cleaned"
            refreshSms()
            refreshStats()
        }
    }

    private fun refreshSms() {
        val list = db.getSmsRecords(query = _smsQuery.value, filter = _smsFilter.value, limit = 50000)
        _smsRecords.value = list
    }

    // =========================================================================
    // DUPLICATES ACTIONS
    // =========================================================================

    fun cleanAppDuplicates() {
        if (_isDeduplicating.value) return
        _isDeduplicating.value = true
        _statusMessage.value = "Cleaning duplicate contacts..."
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val results = dbMutex.withLock {
                    db.cleanAllAppDuplicates()
                }
                _statusMessage.value = "Cleaning device address book duplicates..."
                val survivorNamesMap = results.associate { it.normalizedPhone to it.survivor.name }
                val devCleaned = contactScanner.cleanDeviceDuplicates(survivorNamesMap)

                val devId = getDeviceModelName()
                if (results.isNotEmpty()) {
                    _statusMessage.value = "Syncing ${results.size} merged groups with Hostinger cloud..."
                    val (pushed, _, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                    _statusMessage.value = "Cleaned ${results.size} duplicate groups (device: $devCleaned cleaned, cloud: $pushed pushed)"
                } else {
                    _statusMessage.value = "No duplicate contacts found"
                }
                refreshContacts()
                refreshDuplicates()
                refreshStats()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in cleanAppDuplicates: ${e.message}", e)
                _statusMessage.value = "Clean error: ${e.message}"
            } finally {
                _isDeduplicating.value = false
            }
        }
    }

    fun smartMergeGroup(phone: String, config: SmartMergeConfig = _smartMergeConfig.value) {
        if (_mergingGroupPhones.value.contains(phone) || _isDeduplicating.value) return
        _mergingGroupPhones.value += phone

        // Optimistic in-memory update: compute survivor and update states immediately
        val currentGroups = _duplicateGroups.value
        val group = currentGroups.firstOrNull { it.phone == phone }
        if (group != null) {
            val survivor = SmartMergeConfig.resolveSurvivor(group.contacts, phone, config)
            val survivorIds = group.contacts.map { it.id }.toSet()
            _contacts.value = _contacts.value.filterNot { it.id in survivorIds } + survivor
            _duplicateGroups.value = currentGroups.filterNot { it.phone == phone }
        } else {
            _duplicateGroups.value = currentGroups.filterNot { it.phone == phone }
        }

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val mergeResult = dbMutex.withLock {
                    db.smartMergeGroup(phone, config)
                }
                if (mergeResult != null) {
                    // Clean duplicate raw contacts on Android device address book & update survivor name
                    contactScanner.deleteDeviceDuplicatesForPhone(phone, mergeResult.survivor.name)
                    // Push changes to cloud database immediately
                    val devId = getDeviceModelName()
                    syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                    _statusMessage.value = "Smart-merged group for ${PhoneUtils.formatIndianPhone(phone)} on device & cloud database"
                }
                refreshContacts()
                refreshDuplicates()
                refreshStats()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in smartMergeGroup: ${e.message}", e)
                _statusMessage.value = "Merge error: ${e.message}"
                dbMutex.withLock { refreshDuplicates() }
            } finally {
                _mergingGroupPhones.value -= phone
            }
        }
    }

    fun smartMergeAllGroups(config: SmartMergeConfig = _smartMergeConfig.value) {
        if (_isDeduplicating.value) return
        _isDeduplicating.value = true
        _statusMessage.value = "Smart-merging all duplicate groups..."
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val results = dbMutex.withLock {
                    db.smartMergeAllGroups(config)
                }
                // Clean duplicate raw contacts on Android device address book & update survivor names in ONE single fast pass
                _statusMessage.value = "Cleaning duplicate contacts on device address book..."
                val survivorNamesMap = results.associate { it.normalizedPhone to it.survivor.name }
                val devCleaned = contactScanner.cleanDeviceDuplicates(survivorNamesMap)

                // Push to cloud database
                val devId = getDeviceModelName()
                if (results.isNotEmpty()) {
                    _statusMessage.value = "Syncing ${results.size} merged groups with Hostinger cloud..."
                    val (pushed, _, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                    _statusMessage.value = "Smart-merged ${results.size} duplicate groups (device: $devCleaned cleaned, cloud: $pushed pushed)"
                } else {
                    _statusMessage.value = "No duplicate groups to merge"
                }
                refreshContacts()
                refreshDuplicates()
                refreshStats()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in smartMergeAllGroups: ${e.message}", e)
                _statusMessage.value = "Merge all error: ${e.message}"
            } finally {
                _isDeduplicating.value = false
            }
        }
    }

    fun saveContactToDevice(contact: ContactEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val success = try {
                contactScanner.saveSingleContactToDevice(contact)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to save contact to device: ${e.message}", e)
                false
            }
            withContext(Dispatchers.Main) {
                if (success) {
                    _statusMessage.value = "Saved '${contact.name}' directly to device contacts"
                }
                onResult(success)
            }
        }
    }

    fun purgeInvalidContacts() {
        if (_isDeduplicating.value) return
        _isDeduplicating.value = true
        _statusMessage.value = "Purging invalid/mismatched contacts..."
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val count = dbMutex.withLock {
                    val purged = db.purgeInvalidContacts()
                    refreshContacts()
                    refreshDuplicates()
                    refreshStats()
                    purged
                }
                _statusMessage.value = "Purged $count invalid/mismatched number contacts"
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in purgeInvalidContacts: ${e.message}", e)
                _statusMessage.value = "Purge error: ${e.message}"
            } finally {
                _isDeduplicating.value = false
            }
        }
    }

    fun clearLocalCache() {
        if (_isDeduplicating.value) return
        _statusMessage.value = "Clearing local database cache..."
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                dbMutex.withLock {
                    db.clearAllContacts()
                    refreshContacts()
                    refreshDuplicates()
                    refreshStats()
                }
                _statusMessage.value = "Local contact cache cleared"
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error clearing local cache: ${e.message}", e)
                _statusMessage.value = "Clear cache error: ${e.message}"
            }
        }
    }

    fun refreshDuplicates() {
        try {
            val groups = db.getDuplicateGroups()
            _duplicateGroups.value = groups
            val sameNames = db.getSameNameDifferentNumberGroups()
            _sameNameGroups.value = sameNames
            val validPhones = groups.map { it.phone }.toSet()
            _selectedDuplicatePhones.value = _selectedDuplicatePhones.value.filter { it in validPhones }.toSet()
            val validKeys = sameNames.map { it.nameKey }.toSet()
            _selectedSameNameKeys.value = _selectedSameNameKeys.value.filter { it in validKeys }.toSet()
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error in refreshDuplicates: ${e.message}", e)
        }
    }

    // =========================================================================
    // DUPLICATE SELECTION & SAME NAME DISAMBIGUATION ACTIONS
    // =========================================================================

    fun setDuplicateTab(tab: Int) {
        _duplicateTab.value = tab
    }

    fun setDisambiguateSentenceCase(enabled: Boolean) {
        _disambiguateSentenceCase.value = enabled
    }

    fun toggleDuplicatePhoneSelection(phone: String) {
        _selectedDuplicatePhones.value = if (_selectedDuplicatePhones.value.contains(phone)) {
            _selectedDuplicatePhones.value - phone
        } else {
            _selectedDuplicatePhones.value + phone
        }
    }

    fun selectAllDuplicatePhones() {
        _selectedDuplicatePhones.value = _duplicateGroups.value.map { it.phone }.toSet()
    }

    fun clearDuplicatePhoneSelection() {
        _selectedDuplicatePhones.value = emptySet()
    }

    fun toggleSameNameKeySelection(nameKey: String) {
        _selectedSameNameKeys.value = if (_selectedSameNameKeys.value.contains(nameKey)) {
            _selectedSameNameKeys.value - nameKey
        } else {
            _selectedSameNameKeys.value + nameKey
        }
    }

    fun selectAllSameNameKeys() {
        _selectedSameNameKeys.value = _sameNameGroups.value.map { it.nameKey }.toSet()
    }

    fun clearSameNameKeySelection() {
        _selectedSameNameKeys.value = emptySet()
    }

    fun smartMergeSelectedGroups(config: SmartMergeConfig = _smartMergeConfig.value) {
        val targets = _selectedDuplicatePhones.value.toList()
        if (targets.isEmpty() || _isDeduplicating.value) return
        _isDeduplicating.value = true
        _statusMessage.value = "Smart-merging ${targets.size} selected duplicate groups..."
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val survivorNamesMap = mutableMapOf<String, String>()
                var mergedCount = 0
                dbMutex.withLock {
                    for (phone in targets) {
                        val result = db.smartMergeGroup(phone, config)
                        if (result != null) {
                            survivorNamesMap[result.normalizedPhone] = result.survivor.name
                            mergedCount++
                        }
                    }
                }
                val devCleaned = contactScanner.cleanDeviceDuplicates(survivorNamesMap)
                val devId = getDeviceModelName()
                val (pushed, _, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                _statusMessage.value = "Smart-merged $mergedCount selected groups (device: $devCleaned cleaned, cloud: $pushed pushed)"
                clearDuplicatePhoneSelection()
                refreshContacts()
                refreshDuplicates()
                refreshStats()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in smartMergeSelectedGroups: ${e.message}", e)
                _statusMessage.value = "Merge error: ${e.message}"
            } finally {
                _isDeduplicating.value = false
            }
        }
    }

    fun disambiguateSameNameGroup(nameKey: String, applySentenceCase: Boolean = _disambiguateSentenceCase.value) {
        if (_disambiguatingKeys.value.contains(nameKey) || _isDisambiguating.value) return
        _disambiguatingKeys.value += nameKey
        _isDisambiguating.value = true
        _statusMessage.value = "Disambiguating duplicate name group..."
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val updatedContacts = dbMutex.withLock {
                    db.disambiguateSameNameGroup(nameKey, applySentenceCase)
                }
                if (updatedContacts.isNotEmpty()) {
                    var deviceUpdated = 0
                    for (c in updatedContacts) {
                        if (contactScanner.updateDeviceContactNameForPhone(c.phone, c.name)) {
                            deviceUpdated++
                        }
                    }
                    val devId = getDeviceModelName()
                    val (pushed, _, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                    _statusMessage.value = "Disambiguated ${updatedContacts.size} contacts (device: $deviceUpdated updated, cloud: $pushed pushed)"
                } else {
                    _statusMessage.value = "No changes needed for group"
                }
                refreshContacts()
                refreshDuplicates()
                refreshStats()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error disambiguating group: ${e.message}", e)
                _statusMessage.value = "Disambiguation error: ${e.message}"
            } finally {
                _disambiguatingKeys.value -= nameKey
                _isDisambiguating.value = false
            }
        }
    }

    fun disambiguateSelectedSameNameGroups(applySentenceCase: Boolean = _disambiguateSentenceCase.value) {
        val targets = _selectedSameNameKeys.value.toList()
        if (targets.isEmpty() || _isDisambiguating.value) return
        _isDisambiguating.value = true
        _statusMessage.value = "Disambiguating ${targets.size} selected same-name groups..."
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val allUpdated = mutableListOf<ContactEntity>()
                dbMutex.withLock {
                    for (key in targets) {
                        val updated = db.disambiguateSameNameGroup(key, applySentenceCase)
                        allUpdated.addAll(updated)
                    }
                }
                var deviceUpdated = 0
                for (c in allUpdated) {
                    if (contactScanner.updateDeviceContactNameForPhone(c.phone, c.name)) {
                        deviceUpdated++
                    }
                }
                val devId = getDeviceModelName()
                val (pushed, _, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                _statusMessage.value = "Disambiguated ${allUpdated.size} contacts across ${targets.size} groups (device: $deviceUpdated updated, cloud: $pushed pushed)"
                clearSameNameKeySelection()
                refreshContacts()
                refreshDuplicates()
                refreshStats()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in disambiguateSelectedSameNameGroups: ${e.message}", e)
                _statusMessage.value = "Disambiguation error: ${e.message}"
            } finally {
                _isDisambiguating.value = false
            }
        }
    }

    fun disambiguateAllSameNameGroups(applySentenceCase: Boolean = _disambiguateSentenceCase.value) {
        if (_isDisambiguating.value) return
        _isDisambiguating.value = true
        _statusMessage.value = "Disambiguating all same-name groups..."
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val allUpdated = dbMutex.withLock {
                    db.disambiguateAllSameNameGroups(applySentenceCase)
                }
                var deviceUpdated = 0
                for (c in allUpdated) {
                    if (contactScanner.updateDeviceContactNameForPhone(c.phone, c.name)) {
                        deviceUpdated++
                    }
                }
                val devId = getDeviceModelName()
                val (pushed, _, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                _statusMessage.value = "Disambiguated all ${allUpdated.size} same-name contacts (device: $deviceUpdated updated, cloud: $pushed pushed)"
                clearSameNameKeySelection()
                refreshContacts()
                refreshDuplicates()
                refreshStats()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in disambiguateAllSameNameGroups: ${e.message}", e)
                _statusMessage.value = "Disambiguation all error: ${e.message}"
            } finally {
                _isDisambiguating.value = false
            }
        }
    }

    fun refreshStats() {
        try {
            val st = db.getStats()
            _stats.value = st
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error in refreshStats: ${e.message}", e)
        }
    }

    fun refreshDefaultSmsRole(context: Context) {
        _isDefaultSmsRole.value = SmsRoleHelper.isDefaultSmsApp(context)
    }

    // =========================================================================
    // PLATFORM SCANNERS
    // =========================================================================

    fun scanDeviceContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            _statusMessage.value = "Scanning device contacts..."
            val added = contactScanner.scanDeviceContacts()
            _statusMessage.value = "Discovered $added new device contacts"
            refreshContacts()
            refreshDuplicates()
            refreshStats()
            if (added > 0) {
                val devId = getDeviceModelName()
                val (pushed, _, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                if (pushed > 0) {
                    _statusMessage.value = "Discovered $added contacts (synced $pushed to cloud)"
                }
            }
        }
    }

    fun scanDeviceSms() {
        if (_isScanningSms.value) return
        _isScanningSms.value = true
        _statusMessage.value = "Scanning device SMS..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val smsCount = smsScanner.scanDeviceSms { scanned, inserted ->
                    _statusMessage.value = "Scanning SMS: $scanned analyzed ($inserted in ledger)..."
                }
                _statusMessage.value = "Imported $smsCount SMS messages into Ledger"
                refreshSms()
                refreshStats()
                if (smsCount > 0) {
                    val devId = getDeviceModelName()
                    val (pushed, _) = syncManager.syncSms(_cloudUrl.value, _cloudToken.value, devId)
                    if (pushed > 0) {
                        _statusMessage.value = "Imported $smsCount SMS (synced $pushed to cloud)"
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "SMS scan failed: ${e.message}"
            } finally {
                _isScanningSms.value = false
            }
        }
    }

    fun autoScanDeviceSmsIfEmpty() {
        if (_smsRecords.value.isEmpty() && !_isScanningSms.value) {
            scanDeviceSms()
        }
    }

    fun autoScanDeviceContactsIfEmpty() {
        if (_contacts.value.isEmpty()) {
            scanDeviceContacts()
        }
    }

    fun saveAllToDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            _statusMessage.value = "Saving contacts to device address book..."
            val count = contactScanner.saveAllToDeviceContacts()
            _statusMessage.value = "Saved $count contacts to device address book"
        }
    }

    fun cleanDeviceDuplicates() {
        if (_isDeduplicating.value || _isSyncing.value) return
        _isDeduplicating.value = true
        _statusMessage.value = "Cleaning duplicates on device address book..."
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val count = contactScanner.cleanDeviceDuplicates()
                val results = dbMutex.withLock {
                    db.cleanAllAppDuplicates()
                }
                val devId = getDeviceModelName()
                if (results.isNotEmpty()) {
                    _statusMessage.value = "Syncing ${results.size} merged groups with Hostinger cloud..."
                    val (pushed, _, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                    _statusMessage.value = "Cleaned $count duplicates from device (cloud: $pushed pushed)"
                } else {
                    _statusMessage.value = "Cleaned $count duplicates from device address book"
                }
                refreshContacts()
                refreshDuplicates()
                refreshStats()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in cleanDeviceDuplicates: ${e.message}", e)
                _statusMessage.value = "Device clean error: ${e.message}"
            } finally {
                _isDeduplicating.value = false
            }
        }
    }

    fun vacuumDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            db.vacuumDatabase()
            _statusMessage.value = "Database vacuumed & storage compacted"
            refreshStats()
        }
    }

    // =========================================================================
    // CLOUD SYNC
    // =========================================================================

    fun syncNow() {
        if (_isSyncing.value || _isDeduplicating.value) return
        _isSyncing.value = true
        _statusMessage.value = "Syncing with Hostinger..."
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val devId = getDeviceModelName()
                val (pushed, pulled, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                val (smsPushed, smsMsg) = syncManager.syncSms(_cloudUrl.value, _cloudToken.value, devId)

                _statusMessage.value = "Sync complete: Pushed $pushed, Pulled $pulled contacts | SMS: $smsPushed ($smsMsg)"
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in syncNow: ${e.message}", e)
                _statusMessage.value = "Sync failed: ${e.message}"
            } finally {
                _isSyncing.value = false
                refreshContacts()
                refreshSms()
                refreshDuplicates()
                refreshStats()
            }
        }
    }

    fun pingServer() {
        viewModelScope.launch(Dispatchers.IO) {
            _diagnosticResult.value = "Pinging..."
            val (ok, code, latency) = syncManager.testConnection(_cloudUrl.value, _cloudToken.value)
            _diagnosticResult.value = if (ok) {
                "HTTP $code OK (${latency}ms) - Endpoint reachable"
            } else {
                "HTTP $code Failed (${latency}ms) - Check URL/Token"
            }
        }
    }

    fun updateCloudConfig(url: String, token: String) {
        _cloudUrl.value = url.trim()
        _cloudToken.value = token.trim()
        prefs.edit {
            putString("cloud_url", _cloudUrl.value)
            putString("cloud_token", _cloudToken.value)
        }
        _statusMessage.value = "Hostinger configuration updated"
    }

    fun setWhatsAppPref(pref: String) {
        _whatsAppPref.value = pref
        prefs.edit { putString("whatsapp_client_pref", pref) }
        _statusMessage.value = "Default WhatsApp client: $pref"
    }

    // =========================================================================
    // CLOUD DATA EXPLORER & SEARCH
    // =========================================================================

    fun openCloudDataViewer() {
        _isCloudViewerOpen.value = true
        _cloudErrorMessage.value = null
        loadCloudOverview()
        refreshCloudData()
    }

    fun closeCloudDataViewer() {
        _isCloudViewerOpen.value = false
    }

    fun setCloudViewerTab(tab: Int) {
        _cloudViewerTab.value = tab
        refreshCloudData()
    }

    fun updateCloudSearchQuery(query: String) {
        _cloudSearchQuery.value = query
        cloudSearchJob?.cancel()
        cloudSearchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300.milliseconds)
            refreshCloudData()
        }
    }

    fun loadCloudOverview() {
        viewModelScope.launch(Dispatchers.IO) {
            syncManager.fetchCloudOverview(_cloudUrl.value, _cloudToken.value)
                .onSuccess { overview ->
                    _cloudOverview.value = overview
                }
        }
    }

    fun refreshCloudData() {
        _isCloudLoading.value = true
        _cloudErrorMessage.value = null
        val query = _cloudSearchQuery.value
        val targetTab = _cloudViewerTab.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (targetTab == 0) {
                    // Instantly show locally cached cloud contacts from SQLite for 0ms fast search
                    val (localTotal, localCached) = db.getCloudContacts(query = query, limit = 200, offset = 0)
                    if (localCached.isNotEmpty() && _cloudContacts.value.isEmpty()) {
                        _cloudTotalContacts.value = localTotal
                        _cloudContacts.value = localCached
                    }

                    syncManager.fetchCloudContacts(_cloudUrl.value, _cloudToken.value, query = query, limit = 200)
                        .onSuccess { (total, items) ->
                            _cloudTotalContacts.value = total
                            _cloudContacts.value = items
                            refreshContacts()
                            refreshStats()
                        }
                        .onFailure { err ->
                            // If network failed but we have local cached records, keep displaying them
                            if (localCached.isNotEmpty()) {
                                _cloudTotalContacts.value = localTotal
                                _cloudContacts.value = localCached
                            } else {
                                _cloudErrorMessage.value = "Failed to load cloud contacts: ${err.message}"
                            }
                        }
                } else {
                    syncManager.fetchCloudSms(_cloudUrl.value, _cloudToken.value, query = query, limit = 200)
                        .onSuccess { (total, items) ->
                            _cloudTotalSms.value = total
                            _cloudSms.value = items
                        }
                        .onFailure { err ->
                            _cloudErrorMessage.value = "Failed to load cloud SMS: ${err.message}"
                        }
                }
            } catch (e: Exception) {
                _cloudErrorMessage.value = "Cloud error: ${e.message}"
            } finally {
                _isCloudLoading.value = false
            }
        }
    }

    fun loadMoreCloudContacts() {
        if (_isCloudLoading.value || _isCloudLoadingMore.value) return
        val current = _cloudContacts.value
        val total = _cloudTotalContacts.value
        if (current.size >= total) return

        _isCloudLoadingMore.value = true
        val query = _cloudSearchQuery.value
        val offset = current.size
        viewModelScope.launch(Dispatchers.IO) {
            try {
                syncManager.fetchCloudContacts(_cloudUrl.value, _cloudToken.value, query = query, limit = 200, offset = offset)
                    .onSuccess { (newTotal, items) ->
                        _cloudTotalContacts.value = newTotal
                        if (items.isNotEmpty()) {
                            val existingIds = current.map { it.id.ifBlank { it.phone } }.toSet()
                            val uniqueNew = items.filter { !existingIds.contains(it.id.ifBlank { it.phone }) }
                            _cloudContacts.value = current + uniqueNew
                            refreshContacts()
                            refreshStats()
                        }
                    }
                    .onFailure { err ->
                        android.util.Log.w("MainViewModel", "Failed to load more cloud contacts: ${err.message}")
                    }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error loading more cloud contacts: ${e.message}", e)
            } finally {
                _isCloudLoadingMore.value = false
            }
        }
    }

    fun loadMoreCloudSms() {
        if (_isCloudLoading.value || _isCloudLoadingMore.value) return
        val current = _cloudSms.value
        val total = _cloudTotalSms.value
        if (current.size >= total) return

        _isCloudLoadingMore.value = true
        val query = _cloudSearchQuery.value
        val offset = current.size
        viewModelScope.launch(Dispatchers.IO) {
            try {
                syncManager.fetchCloudSms(_cloudUrl.value, _cloudToken.value, query = query, limit = 200, offset = offset)
                    .onSuccess { (newTotal, items) ->
                        _cloudTotalSms.value = newTotal
                        if (items.isNotEmpty()) {
                            val existingKeys = current.map { "${it.address}_${it.date}" }.toSet()
                            val uniqueNew = items.filter { !existingKeys.contains("${it.address}_${it.date}") }
                            _cloudSms.value = current + uniqueNew
                        }
                    }
                    .onFailure { err ->
                        android.util.Log.w("MainViewModel", "Failed to load more cloud SMS: ${err.message}")
                    }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error loading more cloud SMS: ${e.message}", e)
            } finally {
                _isCloudLoadingMore.value = false
            }
        }
    }

    fun scanCallLogContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            _statusMessage.value = "Scanning device call log..."
            val added = contactScanner.scanCallLogContacts()
            _statusMessage.value = "Discovered $added contacts from call log"
            refreshContacts()
            refreshDuplicates()
            refreshStats()
            if (added > 0) {
                val devId = getDeviceModelName()
                val (pushed, _, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                if (pushed > 0) {
                    _statusMessage.value = "Discovered $added call log contacts (synced $pushed to cloud)"
                }
            }
        }
    }

    fun importCloudContact(contact: CloudContactItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = ContactEntity(
                id = contact.id.ifBlank { UUID.randomUUID().toString() },
                name = contact.name,
                phone = contact.phone,
                details = contact.details,
                saved = contact.saved,
                edited = false,
                deleted = false,
                updatedAt = if (contact.updatedAt > 0) contact.updatedAt else System.currentTimeMillis(),
                source = "HOSTINGER_CSV",
                syncStatus = "SYNCED",
                deviceId = contact.deviceId
            )
            db.upsertContacts(listOf(entity))
            refreshContacts()
            refreshDuplicates()
            refreshStats()
            _statusMessage.value = "Imported '${contact.name}' into local contacts"
        }
    }

    // =========================================================================
    // CSV & VCARD IMPORT / EXPORT
    // =========================================================================

    fun exportContactsToCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allContacts = db.getAllContactsRaw()
                appContext.contentResolver.openOutputStream(uri)?.use { os ->
                    val writer = java.io.OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8)
                    writer.write("id,name,phone,details,saved,updated_at,deleted,device_id\n")
                    for (c in allContacts) {
                        val row = listOf(
                            CsvParser.escape(c.id),
                            CsvParser.escape(c.name),
                            CsvParser.escape(c.phone),
                            CsvParser.escape(c.details),
                            if (c.saved) "1" else "0",
                            c.updatedAt.toString(),
                            if (c.deleted) "1" else "0",
                            CsvParser.escape(c.deviceId)
                        ).joinToString(",")
                        writer.write(row + "\n")
                    }
                    writer.flush()
                }
                _statusMessage.value = "Exported ${allContacts.size} contacts to CSV"
            } catch (e: Exception) {
                _statusMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun importContactsFromCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))
                    val content = reader.readText()
                    val rows = CsvParser.parse(content)
                    if (rows.isNotEmpty()) {
                        val imported = mutableListOf<ContactEntity>()
                        val startIndex = if (rows[0].isNotEmpty() && (rows[0][0].equals("id", ignoreCase = true) || rows[0][0].equals("name", ignoreCase = true))) 1 else 0
                        for (i in startIndex until rows.size) {
                            val r = rows[i]
                            if (r.size >= 8) {
                                val name = r[1].trim()
                                val phone = PhoneUtils.normalizePhone(r[2])
                                if (ContactValidator.isValidContact(name, phone)) {
                                    imported.add(
                                        ContactEntity(
                                            id = r[0].ifBlank { java.util.UUID.randomUUID().toString() },
                                            name = name,
                                            phone = phone,
                                            details = r[3],
                                            saved = r[4] == "1" || r[4].equals("true", ignoreCase = true),
                                            updatedAt = r[5].toLongOrNull() ?: System.currentTimeMillis(),
                                            deleted = r[6] == "1" || r[6].equals("true", ignoreCase = true),
                                            deviceId = r[7],
                                            edited = true,
                                            syncStatus = "PENDING"
                                        )
                                    )
                                }
                            } else if (r.size >= 2) {
                                val name = r[0].trim()
                                val phone = PhoneUtils.normalizePhone(r[1])
                                if (ContactValidator.isValidContact(name, phone)) {
                                    imported.add(
                                        ContactEntity(
                                            name = name,
                                            phone = phone,
                                            details = if (r.size > 2) r[2] else "",
                                            edited = true,
                                            syncStatus = "PENDING"
                                        )
                                    )
                                }
                            }
                        }
                        if (imported.isNotEmpty()) {
                            db.upsertContacts(imported)
                            _statusMessage.value = "Imported ${imported.size} contacts from CSV"
                            refreshContacts()
                            refreshDuplicates()
                            refreshStats()
                            val devId = getDeviceModelName()
                            val (pushed, _, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                            if (pushed > 0) {
                                _statusMessage.value = "Imported ${imported.size} contacts from CSV (synced to cloud)"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "Import failed: ${e.message}"
            }
        }
    }

    fun exportContactsToVcf(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allContacts = db.getAllContactsRaw()
                val vcfContent = VcfParser.exportToVcf(allContacts)
                appContext.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(vcfContent.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                    os.flush()
                }
                _statusMessage.value = "Exported ${allContacts.size} contacts to vCard (.vcf)"
            } catch (e: Exception) {
                _statusMessage.value = "vCard export failed: ${e.message}"
            }
        }
    }

    fun importContactsFromVcf(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader().readText()
                    val imported = VcfParser.parseVcf(content)
                    if (imported.isNotEmpty()) {
                        db.upsertContacts(imported)
                        _statusMessage.value = "Imported ${imported.size} contacts from vCard (.vcf)"
                        refreshContacts()
                        refreshDuplicates()
                        refreshStats()
                        val devId = getDeviceModelName()
                        val (pushed, _, _) = syncManager.syncContacts(_cloudUrl.value, _cloudToken.value, devId)
                        if (pushed > 0) {
                            _statusMessage.value = "Imported ${imported.size} contacts from vCard (synced to cloud)"
                        }
                    } else {
                        _statusMessage.value = "No valid vCard records found"
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "vCard import failed: ${e.message}"
            }
        }
    }

    fun getDeviceModelName(): String {
        val m = Build.MANUFACTURER ?: ""
        val mod = Build.MODEL ?: ""
        val name = if (mod.startsWith(m, ignoreCase = true)) mod else "$m $mod"
        return name.trim().replace(" ", "_")
    }
}
