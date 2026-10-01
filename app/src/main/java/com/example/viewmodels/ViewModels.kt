package com.example.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diagnostics.AppLogger
import com.example.models.*
import com.example.repository.AdminRepository
import com.example.repository.ManagedExtensionRepository
import com.example.repository.ProRequestRepository
import com.example.repository.SupportRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class UserFilter { ALL, ACTIVE, INACTIVE, PREMIUM, BANNED }
enum class UserSort { NEWEST, RECENT_LOGIN, USERNAME }

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: AdminRepository = AdminRepository(),
    private val extensionRepository: ManagedExtensionRepository = ManagedExtensionRepository(repository),
    private val supportRepository: SupportRepository = SupportRepository(repository),
    private val proRequestRepository: ProRequestRepository = ProRequestRepository(repository)
) : ViewModel() {
    private val _currentTab = MutableStateFlow(DashboardTab.OVERVIEW)
    val currentTab = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(UserFilter.ALL)
    val filter = _filter.asStateFlow()

    private val _sort = MutableStateFlow(UserSort.NEWEST)
    val sort = _sort.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val refreshTrigger = MutableStateFlow(0L)

    private val allUsers: StateFlow<List<User>> = refreshTrigger
        .flatMapLatest { repository.getAllUsers() }
        .onEach {
            _isLoading.value = false
            _isRefreshing.value = false
        }
        .catch { e ->
            AppLogger.w("DashboardViewModel", "allUsers non-fatal notice: ${e.message}")
            _isLoading.value = false
            _isRefreshing.value = false
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Phase C6: User Metrics State
    val userMetrics: StateFlow<DashboardSectionState<UserMetrics>> = allUsers
        .map<List<User>, DashboardSectionState<UserMetrics>> { userList ->
            DashboardSectionState.Success(DashboardAnalyticsCalculator.calculateUserMetrics(userList))
        }
        .catch { e ->
            AppLogger.e("DashboardViewModel", "Error calculating user metrics: ${e.message}", e)
            emit(DashboardSectionState.Error(e.message ?: "Failed to calculate user metrics"))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DashboardSectionState.Loading)

    // Phase C6: Feature Restriction Metrics State
    val featureRestrictionMetrics: StateFlow<DashboardSectionState<FeatureRestrictionMetrics>> = allUsers
        .map<List<User>, DashboardSectionState<FeatureRestrictionMetrics>> { userList ->
            DashboardSectionState.Success(DashboardAnalyticsCalculator.calculateFeatureRestrictions(userList))
        }
        .catch { e ->
            AppLogger.e("DashboardViewModel", "Error calculating restriction metrics: ${e.message}", e)
            emit(DashboardSectionState.Error(e.message ?: "Failed to calculate restriction metrics"))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DashboardSectionState.Loading)

    // Phase C6: Support Metrics State
    val supportMetrics: StateFlow<DashboardSectionState<SupportMetrics>> = refreshTrigger
        .flatMapLatest { supportRepository.getAllConversations() }
        .map<List<SupportConversation>, DashboardSectionState<SupportMetrics>> { conversations ->
            DashboardSectionState.Success(DashboardAnalyticsCalculator.calculateSupportMetrics(conversations))
        }
        .catch { e ->
            AppLogger.e("DashboardViewModel", "Error loading support metrics: ${e.message}", e)
            emit(DashboardSectionState.Error(e.message ?: "Failed to load support metrics"))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DashboardSectionState.Loading)

    // Phase C6: Pro Request Metrics State
    val proRequestMetrics: StateFlow<DashboardSectionState<ProRequestMetrics>> = refreshTrigger
        .flatMapLatest { proRequestRepository.getAllProRequests() }
        .map<List<ProRequest>, DashboardSectionState<ProRequestMetrics>> { requests ->
            DashboardSectionState.Success(DashboardAnalyticsCalculator.calculateProRequestMetrics(requests))
        }
        .catch { e ->
            AppLogger.e("DashboardViewModel", "Error loading pro request metrics: ${e.message}", e)
            emit(DashboardSectionState.Error(e.message ?: "Failed to load pro request metrics"))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DashboardSectionState.Loading)

    // Phase C6: Managed Extensions Metrics State
    val extensionMetrics: StateFlow<DashboardSectionState<ManagedExtensionMetrics>> = refreshTrigger
        .flatMapLatest { extensionRepository.getAllManagedExtensions() }
        .map<List<ManagedExtension>, DashboardSectionState<ManagedExtensionMetrics>> { extensions ->
            DashboardSectionState.Success(DashboardAnalyticsCalculator.calculateManagedExtensionMetrics(extensions))
        }
        .catch { e ->
            AppLogger.e("DashboardViewModel", "Error loading extension metrics: ${e.message}", e)
            emit(DashboardSectionState.Error(e.message ?: "Failed to load extension metrics"))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DashboardSectionState.Loading)

    // Phase C6: Recent Administrative Activity (bounded query of 20 logs)
    val recentActivity: StateFlow<DashboardSectionState<List<AuditLog>>> = refreshTrigger
        .flatMapLatest { repository.getAuditLogs(limit = 20) }
        .map<List<AuditLog>, DashboardSectionState<List<AuditLog>>> { logs ->
            DashboardSectionState.Success(logs)
        }
        .catch { e ->
            AppLogger.e("DashboardViewModel", "Error loading audit activity: ${e.message}", e)
            emit(DashboardSectionState.Error(e.message ?: "Failed to load audit activity"))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DashboardSectionState.Loading)

    fun setTab(tab: DashboardTab) {
        _currentTab.value = tab
    }

    val users: StateFlow<List<User>> = combine(allUsers, _searchQuery, _filter, _sort) { users, query, filter, sort ->
        var list = users

        // 1. Search by username, email, displayName, or UID
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.username.lowercase().contains(q) ||
                it.email.lowercase().contains(q) ||
                it.id.lowercase().contains(q) ||
                it.displayName.lowercase().contains(q)
            }
        }

        // 2. Filter
        val threshold = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        list = when (filter) {
            UserFilter.ALL -> list
            UserFilter.ACTIVE -> list.filter { it.lastLoginTimestamp >= threshold }
            UserFilter.INACTIVE -> list.filter { it.lastLoginTimestamp < threshold }
            UserFilter.PREMIUM -> list.filter { it.subscriptionState == SubscriptionState.ACTIVE_PRO }
            UserFilter.BANNED -> list.filter {
                it.isAccountBanned || it.hasFeatureRestrictions
            }
        }

        // 3. Sort
        when (sort) {
            UserSort.NEWEST -> list.sortedByDescending { it.createdAt }
            UserSort.RECENT_LOGIN -> list.sortedByDescending { it.lastLoginTimestamp }
            UserSort.USERNAME -> list.sortedBy { it.username.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalUsers = allUsers.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    // Active if logged in within the last 30 days
    val activeUsers = allUsers.map { userList -> 
        val threshold = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        userList.count { it.lastLoginTimestamp >= threshold }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val inactiveUsers = combine(totalUsers, activeUsers) { total, active -> 
        (total - active).coerceAtLeast(0) 
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val proUsersCount = allUsers.map { userList -> userList.count { it.subscriptionState == SubscriptionState.ACTIVE_PRO } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val bannedUsersCount = allUsers.map { userList -> userList.count { it.isAccountBanned } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(newFilter: UserFilter) {
        _filter.value = newFilter
    }

    fun setSort(newSort: UserSort) {
        _sort.value = newSort
    }

    fun resetFilters() {
        _searchQuery.value = ""
        _filter.value = UserFilter.ALL
        _sort.value = UserSort.NEWEST
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                repository.forceRefreshUsers()
                supportRepository.forceRefresh()
            } catch (e: Exception) {
                AppLogger.w("DashboardViewModel", "Refresh failed: ${e.message}")
            } finally {
                refreshTrigger.value = System.currentTimeMillis()
                kotlinx.coroutines.delay(400)
                _isRefreshing.value = false
            }
        }
    }

    fun createUser(
        username: String,
        email: String,
        role: String = "user",
        isPremium: Boolean = false,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val newUser = User(
                    username = username,
                    email = email,
                    role = role,
                    isPremium = isPremium,
                    canWatch = true,
                    canDownload = true,
                    canChat = true,
                    canStory = true,
                    canP2P = true,
                    createdAt = System.currentTimeMillis(),
                    lastLoginTimestamp = 0L
                )
                val createdId = repository.createUser(newUser)
                if (role == "admin") {
                    repository.setUserAdminRole(createdId, email, true)
                }
                refresh()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to create user")
            }
        }
    }

    fun deleteUser(userId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteUser(userId)
                refresh()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete user")
            }
        }
    }

    fun toggleUserPremium(user: User) {
        viewModelScope.launch {
            try {
                if (user.subscriptionState.isAdFree) {
                    repository.revokeSubscription(user.id)
                } else {
                    repository.updateSubscription(user.id, tier = "PRO", durationDays = 30)
                }
                refresh()
            } catch (e: Exception) {
                AppLogger.e("DashboardViewModel", "Error toggling premium: ${e.message}")
            }
        }
    }

    fun toggleUserBan(user: User) {
        viewModelScope.launch {
            try {
                if (user.isAccountBanned) {
                    repository.unbanAccount(user.id)
                } else {
                    repository.banAccount(user.id, reason = "Banned by administrator", expiresAt = null)
                }
                refresh()
            } catch (e: Exception) {
                AppLogger.e("DashboardViewModel", "Error toggling ban: ${e.message}")
            }
        }
    }
}

class UserDetailViewModel(
    private val userId: String,
    private val repository: AdminRepository = AdminRepository()
) : ViewModel() {
    val pendingOperations: StateFlow<Set<String>> = repository.pendingSyncKeys

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.forceRefreshUsers()
            } catch (e: Exception) {
                // non-fatal
            } finally {
                kotlinx.coroutines.delay(400)
                _isRefreshing.value = false
            }
        }
    }

    val user: StateFlow<User?> = repository.getUser(userId)
        .catch { e ->
            AppLogger.e("UserDetailViewModel", "Error in user flow: ${e.message}", e)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    /**
     * Authoritative Global Account Ban (Phase C1).
     */
    fun banAccount(reason: String?, expiresAt: Long?) {
        viewModelScope.launch {
            try {
                repository.banAccount(userId, reason, expiresAt)
                _statusMessage.value = "Account has been suspended"
            } catch (e: Exception) {
                _statusMessage.value = "Error banning account: ${e.message}"
            }
        }
    }

    /**
     * Authoritative Unban & Lift Suspension (Phase C1).
     */
    fun unbanAccount() {
        viewModelScope.launch {
            try {
                repository.unbanAccount(userId)
                _statusMessage.value = "Account ban lifted"
            } catch (e: Exception) {
                _statusMessage.value = "Error lifting ban: ${e.message}"
            }
        }
    }

    fun toggleAccountBan(isCurrentlyBanned: Boolean, reason: String? = null, expiresAt: Long? = null) {
        if (isCurrentlyBanned) {
            unbanAccount()
        } else {
            banAccount(reason, expiresAt)
        }
    }

    /**
     * Authoritative Subscription Grant / Extension (Phase 03A).
     */
    fun grantSubscription(tier: String, durationDays: Int?) {
        viewModelScope.launch {
            try {
                repository.updateSubscription(userId = userId, tier = tier, durationDays = durationDays)
                _statusMessage.value = "Subscription granted: ${tier.uppercase()}"
            } catch (e: Exception) {
                _statusMessage.value = "Error granting subscription: ${e.message}"
            }
        }
    }

    /**
     * Authoritative Subscription Revocation (Phase 03A).
     */
    fun revokeSubscription() {
        viewModelScope.launch {
            try {
                repository.revokeSubscription(userId)
                _statusMessage.value = "Subscription revoked to Free plan"
            } catch (e: Exception) {
                _statusMessage.value = "Error revoking subscription: ${e.message}"
            }
        }
    }

    fun togglePremium() {
        val currentUser = user.value ?: return
        if (currentUser.subscriptionState.isAdFree) {
            revokeSubscription()
        } else {
            grantSubscription("PRO", 30)
        }
    }

    fun toggleRole() {
        val currentUser = user.value ?: return
        val willBeAdmin = currentUser.role != "admin"
        viewModelScope.launch {
            try {
                repository.setUserAdminRole(userId, currentUser.email, willBeAdmin)
                _statusMessage.value = if (willBeAdmin) "Granted administrator access" else "Revoked administrator access"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    /**
     * Fine-grained Feature Permission Toggle (Phase C1).
     */
    fun togglePermission(permissionKey: String, isCurrentlyAllowed: Boolean) {
        val newAllowed = !isCurrentlyAllowed
        viewModelScope.launch {
            try {
                repository.updateFeaturePermission(userId, permissionKey, newAllowed)
                _statusMessage.value = "Feature permission updated"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun toggleBan(banType: String, currentValue: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateUser(userId, mapOf(banType to !currentValue))
                _statusMessage.value = "$banType set to ${!currentValue}"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun updateOfflineOverrides(days: Int?, ads: Int?) {
        viewModelScope.launch {
            try {
                repository.updateUser(userId, mapOf(
                    "offlineDaysOverride" to days,
                    "forcedAdsOverride" to ads
                ))
                _statusMessage.value = "Offline DRM overrides saved"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    val pointTransactions: StateFlow<List<PointTransaction>> = repository.getUserPointTransactions(userId)
        .catch { e ->
            AppLogger.w("UserDetailViewModel", "Error fetching point transactions: ${e.message}")
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun adjustUserPoints(amount: Long, reason: String) {
        viewModelScope.launch {
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                val adminUid = currentUser?.uid ?: "admin_system"
                val adminEmail = currentUser?.email ?: "admin@cinestream.com"
                repository.adjustUserPoints(
                    userId = userId,
                    amount = amount,
                    reason = reason,
                    actorUid = adminUid,
                    actorEmail = adminEmail
                )
                _statusMessage.value = if (amount > 0) "Points granted successfully (+$amount)" else "Points deducted successfully ($amount)"
            } catch (e: Exception) {
                _statusMessage.value = "Error adjusting points: ${e.message}"
            }
        }
    }
}

class ConfigViewModel(private val repository: AdminRepository = AdminRepository()) : ViewModel() {
    val config: StateFlow<AppConfig> = repository.getAppConfig()
        .catch { e ->
            AppLogger.e("ConfigViewModel", "Error in config flow: ${e.message}", e)
            emit(AppConfig())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppConfig())

    val featureConfig: StateFlow<FeatureControlConfig> = repository.getFeatureControlConfig()
        .catch { e ->
            AppLogger.e("ConfigViewModel", "Error in feature config flow: ${e.message}", e)
            emit(FeatureControlConfig())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeatureControlConfig())

    val economyConfig: StateFlow<EconomyConfig> = repository.getEconomyConfig()
        .catch { e ->
            AppLogger.e("ConfigViewModel", "Error in economy config flow: ${e.message}", e)
            emit(EconomyConfig())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EconomyConfig())

    fun updateFeature(featureKey: String, state: FeatureState, comingSoonMsg: String? = null, disabledMsg: String? = null) {
        viewModelScope.launch {
            try {
                repository.updateFeatureControl(featureKey, state, comingSoonMsg, disabledMsg)
                _statusMessage.value = "Feature $featureKey set to ${state.name}"
            } catch (e: Exception) {
                _statusMessage.value = "Error updating feature: ${e.message}"
            }
        }
    }

    fun saveEconomy(economy: EconomyConfig) {
        viewModelScope.launch {
            try {
                repository.saveEconomyConfig(economy)
                _statusMessage.value = "Economy parameters updated"
            } catch (e: Exception) {
                _statusMessage.value = "Error saving economy: ${e.message}"
            }
        }
    }

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.forceRefreshAppConfig()
            } catch (e: Exception) {
                AppLogger.w("ConfigViewModel", "forceRefresh error: ${e.message}")
            } finally {
                kotlinx.coroutines.delay(400)
                _isRefreshing.value = false
            }
        }
    }

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _apkUploadState = MutableStateFlow<com.example.media.ApkUploadState>(com.example.media.ApkUploadState.Idle)
    val apkUploadState = _apkUploadState.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun resetApkUploadState() {
        _apkUploadState.value = com.example.media.ApkUploadState.Idle
    }

    fun handleSelectedApk(
        context: android.content.Context,
        uri: android.net.Uri,
        onMetadataExtracted: (sha256: String, versionName: String?, versionCode: Int?, url: String?) -> Unit
    ) {
        viewModelScope.launch {
            _apkUploadState.value = com.example.media.ApkUploadState.Processing("Analyzing APK file & computing SHA-256...")
            val result = com.example.media.ApkUploadHelper.analyzeApkUri(context, uri)
            result.onSuccess { info ->
                val cachedFile = info.localCachedFile
                val hasCloudinary = com.example.media.CloudinaryManager.getCloudName().isNotBlank() &&
                                    com.example.media.CloudinaryManager.getUploadPreset().isNotBlank()

                if (hasCloudinary && cachedFile != null && cachedFile.exists()) {
                    _apkUploadState.value = com.example.media.ApkUploadState.Uploading("Uploading ${info.fileName} (${info.sizeFormatted}) to server...")
                    val uploadResult = com.example.media.ApkUploadHelper.uploadToCloudinary(cachedFile)
                    uploadResult.onSuccess { uploadedUrl ->
                        _apkUploadState.value = com.example.media.ApkUploadState.Success(info, uploadedUrl)
                        onMetadataExtracted(info.sha256Hex, info.extractedVersionName, info.extractedVersionCode, uploadedUrl)
                        _statusMessage.value = "APK uploaded successfully! SHA-256 and URL updated."
                    }.onFailure { uploadErr ->
                        _apkUploadState.value = com.example.media.ApkUploadState.HashCalculated(info, "SHA-256 calculated. Upload notice: ${uploadErr.message}")
                        onMetadataExtracted(info.sha256Hex, info.extractedVersionName, info.extractedVersionCode, null)
                        _statusMessage.value = "SHA-256 calculated! You can enter APK URL manually."
                    }
                } else {
                    _apkUploadState.value = com.example.media.ApkUploadState.HashCalculated(info, "SHA-256 computed: ${info.sizeFormatted}")
                    onMetadataExtracted(info.sha256Hex, info.extractedVersionName, info.extractedVersionCode, null)
                    _statusMessage.value = "APK SHA-256 computed successfully (${info.sizeFormatted})"
                }
            }.onFailure { error ->
                _apkUploadState.value = com.example.media.ApkUploadState.Error(error.message ?: "Failed to process APK")
                _statusMessage.value = "Error analyzing APK: ${error.message}"
            }
        }
    }

    fun updateMaintenance(enabled: Boolean, title: String, message: String, minVersion: Int) {
        if (minVersion < 0) {
            _statusMessage.value = "Minimum version code cannot be negative"
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.updateAppConfig(
                    mapOf(
                        "maintenanceEnabled" to enabled,
                        "maintenanceTitle" to title,
                        "maintenanceMessage" to message,
                        "minimumVersionCode" to minVersion
                    )
                )
                _statusMessage.value = "Maintenance settings updated successfully"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to update maintenance: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateOta(
        versionCode: Int,
        versionName: String,
        apkUrl: String,
        apkSha256: String,
        mandatory: Boolean,
        releaseNotes: String,
        minimumVersionCode: Int? = null,
        broadcastNotification: Boolean = false
    ) {
        if (versionCode <= 0) {
            _statusMessage.value = "Version code must be greater than 0"
            return
        }
        if (apkUrl.isNotBlank() && !apkUrl.startsWith("http://") && !apkUrl.startsWith("https://")) {
            _statusMessage.value = "APK URL must start with https:// or http://"
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val configUpdates = mutableMapOf<String, Any?>(
                    "latestVersionCode" to versionCode,
                    "latestVersionName" to versionName,
                    "apkUrl" to apkUrl,
                    "apkSha256" to apkSha256,
                    "mandatoryUpdate" to mandatory,
                    "releaseNotes" to releaseNotes
                )
                if (minimumVersionCode != null && minimumVersionCode > 0) {
                    configUpdates["minimumVersionCode"] = minimumVersionCode
                } else if (mandatory) {
                    configUpdates["minimumVersionCode"] = versionCode
                }
                repository.updateAppConfig(configUpdates)

                // Canonical storage in /app_updates/{updateId}
                val minCode = if (minimumVersionCode != null && minimumVersionCode > 0) minimumVersionCode else if (mandatory) versionCode else 1
                repository.createOrUpdateAppUpdate(
                    AppUpdate(
                        id = versionCode.toString(),
                        versionCode = versionCode,
                        versionName = versionName,
                        minVersionCode = minCode,
                        apkUrl = apkUrl,
                        apkSha256 = apkSha256,
                        mandatoryUpdate = mandatory,
                        releaseNotes = releaseNotes,
                        status = "PUBLISHED"
                    )
                )

                if (broadcastNotification) {
                    val notifBody = if (releaseNotes.isNotBlank()) releaseNotes else "يتوفر إصدار جديد من تطبيق CineStream ($versionName). يرجى التحديث لتجربة أفضل وميزات جديدة."
                    repository.sendNotification(
                        NotificationRequest(
                            title = "تحديث جديد متوفر: CineStream v$versionName",
                            body = notifBody,
                            message = notifBody,
                            type = "UPDATE",
                            target = "ALL",
                            targetType = NotificationRequest.TargetType.ALL
                        )
                    )
                }

                _statusMessage.value = if (broadcastNotification) 
                    "CineStream OTA Update published & broadcast sent to users" 
                else 
                    "CineStream OTA Update published successfully"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to publish OTA: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateDrmSettings(days: Int, ads: Int) {
        if (days < 0 || ads < 0) {
            _statusMessage.value = "Offline days and forced ads cannot be negative"
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.updateAppConfig(
                    mapOf(
                        "defaultOfflineDays" to days,
                        "defaultForcedAds" to ads
                    )
                )
                _statusMessage.value = "Default DRM rules saved"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateProvidersJson(json: String) {
        val trimmed = json.trim()
        if (trimmed.isNotBlank()) {
            try {
                if (trimmed.startsWith("[")) {
                    org.json.JSONArray(trimmed)
                } else {
                    org.json.JSONObject(trimmed)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Invalid JSON syntax: ${e.message}"
                return
            }
        }
        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.updateAppConfig(mapOf("providersJson" to trimmed))
                _statusMessage.value = "Dynamic providers JSON deployed"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateCloudinaryConfig(cloudName: String, uploadPreset: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.updateAppConfig(
                    mapOf(
                        "cloudinaryCloudName" to cloudName.trim(),
                        "cloudinaryUploadPreset" to uploadPreset.trim()
                    )
                )
                com.example.media.CloudinaryManager.updateConfig(cloudName.trim(), uploadPreset.trim())
                _statusMessage.value = "Cloudinary media storage config saved"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }
}

class NotificationViewModel(private val repository: AdminRepository = AdminRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationState())
    val uiState = _uiState.asStateFlow()

    val recentNotifications: StateFlow<List<NotificationRequest>> = repository.getRecentNotifications()
        .catch { e ->
            AppLogger.e("NotificationViewModel", "Error fetching notifications: ${e.message}", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.forceRefreshNotifications()
            } catch (e: Exception) {
                AppLogger.w("NotificationViewModel", "forceRefresh error: ${e.message}")
            } finally {
                kotlinx.coroutines.delay(400)
                _isRefreshing.value = false
            }
        }
    }

    fun deleteNotification(notificationId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteNotification(notificationId)
                _statusMessage.value = "Notification permanently deleted"
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "Error deleting notification: ${e.message}"
            }
        }
    }

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun updateTitle(title: String) { _uiState.value = _uiState.value.copy(title = title) }
    fun updateMessage(msg: String) { _uiState.value = _uiState.value.copy(message = msg, body = msg) }
    fun updateBody(body: String) { _uiState.value = _uiState.value.copy(body = body, message = body) }
    fun updateType(type: String) { _uiState.value = _uiState.value.copy(type = type) }
    fun updateTargetType(type: NotificationRequest.TargetType) { _uiState.value = _uiState.value.copy(targetType = type) }
    fun updateTargetUid(uid: String) { _uiState.value = _uiState.value.copy(targetUid = uid) }

    fun sendNotification() {
        val state = _uiState.value
        val bodyContent = state.body.ifBlank { state.message }.trim()
        if (state.title.isBlank() || bodyContent.isBlank()) {
            _statusMessage.value = "Please fill in title and message"
            return
        }
        if (state.targetType == NotificationRequest.TargetType.UID && state.targetUid.isBlank()) {
            _statusMessage.value = "Please enter target user UID"
            return
        }

        val targetStr = when (state.targetType) {
            NotificationRequest.TargetType.ALL -> "ALL"
            NotificationRequest.TargetType.PRO -> "PRO"
            NotificationRequest.TargetType.UID -> "UID"
        }

        viewModelScope.launch {
            _isSending.value = true
            try {
                repository.sendNotification(
                    NotificationRequest(
                        title = state.title.trim(),
                        body = bodyContent,
                        message = bodyContent,
                        type = state.type,
                        target = targetStr,
                        targetType = state.targetType,
                        targetUid = if (state.targetType == NotificationRequest.TargetType.UID) state.targetUid.trim() else ""
                    )
                )
                _statusMessage.value = "Notification dispatched to /notifications"
                _uiState.value = NotificationState()
            } catch (e: Exception) {
                _statusMessage.value = "Failed to send: ${e.message}"
            } finally {
                _isSending.value = false
            }
        }
    }
}

data class NotificationState(
    val title: String = "",
    val message: String = "",
    val body: String = "",
    val type: String = "SYSTEM",
    val targetType: NotificationRequest.TargetType = NotificationRequest.TargetType.ALL,
    val targetUid: String = ""
)

enum class LogCategory { ALL, USER, CONFIG, NOTIFICATION, EXTENSION }

class AuditLogsViewModel(private val repository: AdminRepository = AdminRepository()) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _category = MutableStateFlow(LogCategory.ALL)
    val category = _category.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.forceRefreshAuditLogs()
            } catch (e: Exception) {
                AppLogger.w("AuditLogsViewModel", "forceRefresh error: ${e.message}")
            } finally {
                kotlinx.coroutines.delay(400)
                _isRefreshing.value = false
            }
        }
    }

    private val allLogs = repository.getAuditLogs()
        .catch { e ->
            AppLogger.e("AuditLogsViewModel", "Error in audit logs: ${e.message}", e)
            emit(emptyList())
        }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

    val logs: StateFlow<List<AuditLog>> = combine(allLogs, _searchQuery, _category) { list, query, cat ->
        var filtered = list

        if (cat != LogCategory.ALL) {
            val targetKeyword = cat.name
            filtered = filtered.filter {
                it.targetType.equals(targetKeyword, ignoreCase = true) ||
                it.action.contains(targetKeyword, ignoreCase = true)
            }
        }

        if (query.isNotBlank()) {
            val q = query.lowercase().trim()
            filtered = filtered.filter {
                it.action.lowercase().contains(q) ||
                it.adminEmail.lowercase().contains(q) ||
                it.targetId.lowercase().contains(q) ||
                it.details.lowercase().contains(q)
            }
        }
        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun setCategory(cat: LogCategory) {
        _category.value = cat
    }
}

class ExtensionsViewModel(private val repository: AdminRepository = AdminRepository()) : ViewModel() {
    val extensions: StateFlow<List<ExtensionItem>> = repository.getExtensions()
        .catch { e ->
            AppLogger.e("ExtensionsViewModel", "Error fetching extensions: ${e.message}", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.forceRefreshExtensions()
            } catch (e: Exception) {
                AppLogger.w("ExtensionsViewModel", "forceRefresh error: ${e.message}")
            } finally {
                kotlinx.coroutines.delay(400)
                _isRefreshing.value = false
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun saveExtension(extension: ExtensionItem) {
        viewModelScope.launch {
            try {
                repository.saveExtension(extension)
                _statusMessage.value = "Extension saved"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun toggleExtension(item: ExtensionItem) {
        viewModelScope.launch {
            try {
                repository.saveExtension(item.copy(enabled = !item.enabled))
                _statusMessage.value = "Extension ${if (!item.enabled) "enabled" else "disabled"}"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun deleteExtension(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteExtension(id)
                _statusMessage.value = "Extension deleted"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }
}
