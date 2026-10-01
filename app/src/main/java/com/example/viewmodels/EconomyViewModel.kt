package com.example.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diagnostics.AppLogger
import com.example.models.*
import com.example.repository.AdminRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class EconomyViewModel(
    private val repository: AdminRepository = AdminRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _isAuthorized = MutableStateFlow<Boolean>(true)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedTab = MutableStateFlow<Int>(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _userSearchQuery = MutableStateFlow<String>("")
    val userSearchQuery: StateFlow<String> = _userSearchQuery.asStateFlow()

    private val _selectedUser = MutableStateFlow<User?>(null)
    val selectedUser: StateFlow<User?> = _selectedUser.asStateFlow()

    // Realtime feeds from Firestore
    val featureConfig: StateFlow<FeatureControlConfig> = repository.getFeatureControlConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeatureControlConfig())

    val economyConfig: StateFlow<EconomyConfig> = repository.getEconomyConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EconomyConfig())

    val rewardTasks: StateFlow<List<RewardTask>> = repository.getAllRewardTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklyLeaderboard: StateFlow<WeeklyLeaderboard> = repository.getWeeklyLeaderboard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeeklyLeaderboard())

    val allUsers: StateFlow<List<User>> = repository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredUsers: StateFlow<List<User>> = combine(allUsers, _userSearchQuery) { users, query ->
        if (query.isBlank()) {
            users.take(20)
        } else {
            val q = query.trim().lowercase()
            users.filter {
                it.email.lowercase().contains(q) ||
                it.displayName.lowercase().contains(q) ||
                it.username.lowercase().contains(q) ||
                it.id.lowercase().contains(q) ||
                it.uid.lowercase().contains(q)
            }.take(30)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Transactions feed for currently selected user
    val userTransactions: StateFlow<List<PointTransaction>> = _selectedUser
        .flatMapLatest { user ->
            if (user != null && (user.uid.isNotBlank() || user.id.isNotBlank())) {
                val resolvedId = user.uid.ifBlank { user.id }
                repository.getUserPointTransactions(resolvedId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _auditFilter = MutableStateFlow<String>("ALL")
    val auditFilter: StateFlow<String> = _auditFilter.asStateFlow()

    // Real-time Economy Audit Trail (Phase 04B Deliverable)
    val economyAuditLogs: StateFlow<List<AuditLog>> = repository.getAuditLogs(100)
        .map { logs ->
            logs.filter { log ->
                val action = log.action.uppercase()
                action.contains("ECONOMY") ||
                action.contains("FEATURE") ||
                action.contains("TASK") ||
                action.contains("POINT") ||
                action.contains("SUBSCRIPTION") ||
                log.targetType.equals("CONFIG", ignoreCase = true) ||
                log.targetType.equals("TASK", ignoreCase = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAuditLogs: StateFlow<List<AuditLog>> = combine(economyAuditLogs, _auditFilter) { logs, filter ->
        when (filter) {
            "POINTS" -> logs.filter { it.action.contains("POINT", ignoreCase = true) }
            "FEATURES" -> logs.filter { it.action.contains("FEATURE", ignoreCase = true) }
            "CONFIG" -> logs.filter { it.action.contains("CONFIG", ignoreCase = true) || it.action.contains("ECONOMY", ignoreCase = true) }
            "TASKS" -> logs.filter { it.action.contains("TASK", ignoreCase = true) }
            else -> logs
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        verifyAdminAuthority()
    }

    private fun verifyAdminAuthority() {
        val user = auth.currentUser
        if (user == null) {
            _isAuthorized.value = false
            return
        }
        val email = user.email?.lowercase() ?: ""
        if (email == "sulopros01@gmail.com") {
            _isAuthorized.value = true
            return
        }
        viewModelScope.launch {
            try {
                val isAdmin = repository.checkIsAdmin(user)
                _isAuthorized.value = isAdmin
            } catch (e: Exception) {
                AppLogger.w("EconomyViewModel", "Admin authority check failed: ${e.message}")
                _isAuthorized.value = false
            }
        }
    }

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun setAuditFilter(filter: String) {
        _auditFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _userSearchQuery.value = query
    }

    fun selectUser(user: User?) {
        _selectedUser.value = user
    }

    fun clearMessages() {
        _userMessage.value = null
        _errorMessage.value = null
    }

    // -------------------------------------------------------------
    // 1. Feature Control Operations (/config/features)
    // -------------------------------------------------------------

    fun updateFeature(
        featureKey: String,
        state: FeatureState,
        comingSoonMsg: String? = null,
        disabledMsg: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.updateFeatureControl(
                    featureKey = featureKey,
                    state = state,
                    comingSoonMsg = comingSoonMsg,
                    disabledMsg = disabledMsg
                )
                _userMessage.value = "تم تحديث حالة الميزة ($featureKey -> ${state.name}) بنجاح"
            } catch (e: Exception) {
                AppLogger.e("EconomyViewModel", "Failed to update feature $featureKey: ${e.message}")
                _errorMessage.value = "فشل تحديث الميزة: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // 2. Subscription Pricing Operations (/config/economy)
    // -------------------------------------------------------------

    fun updateSubscriptionPrice(sku: String, newPrice: Long) {
        viewModelScope.launch {
            val cleanSku = sku.trim().lowercase()
            if (!EconomyConfig.CANONICAL_SKUS.contains(cleanSku)) {
                _errorMessage.value = "لا يمكن تعديل إلا باقات الاشتراك المعتمدة Canonical ($cleanSku)"
                return@launch
            }
            if (newPrice <= 0L) {
                _errorMessage.value = "سعر الباقة يجب أن يكون عدداً صحيحاً موجباً أكبر من الصفر ($newPrice)"
                return@launch
            }

            _isLoading.value = true
            try {
                val currentCosts = HashMap(economyConfig.value.redemptionCosts)
                currentCosts[cleanSku] = newPrice

                val errors = EconomyConfig.validateRedemptionCosts(currentCosts)
                if (errors.isNotEmpty()) {
                    _errorMessage.value = errors.first()
                    _isLoading.value = false
                    return@launch
                }

                val newConfig = economyConfig.value.copy(redemptionCosts = currentCosts)
                repository.saveEconomyConfig(newConfig)
                _userMessage.value = "تم تحديث سعر استبدال الباقة $cleanSku إلى $newPrice نقطة"
            } catch (e: Exception) {
                AppLogger.e("EconomyViewModel", "Failed to update subscription price: ${e.message}")
                _errorMessage.value = "فشل حفظ السعر الجديد: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // 3. Daily Login Ladder Operations (/config/economy)
    // -------------------------------------------------------------

    fun updateDailyLoginRewards(newRewards: List<Long>) {
        viewModelScope.launch {
            val errors = EconomyConfig.validateDailyLoginRewards(newRewards)
            if (errors.isNotEmpty()) {
                _errorMessage.value = errors.first()
                return@launch
            }

            _isLoading.value = true
            try {
                val newConfig = economyConfig.value.copy(dailyLoginRewards = newRewards)
                repository.saveEconomyConfig(newConfig)
                _userMessage.value = "تم تحديث مكافآت تسجيل الدخول اليومي (7 أيام) بنجاح"
            } catch (e: Exception) {
                AppLogger.e("EconomyViewModel", "Failed to update daily login rewards: ${e.message}")
                _errorMessage.value = "فشل حفظ مكافآت تسجيل الدخول: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // 4. Rewarded Ads Operations (/config/economy)
    // -------------------------------------------------------------

    fun updateRewardedAdsConfig(points: Long, dailyCap: Int, cooldownSeconds: Int) {
        viewModelScope.launch {
            val errors = EconomyConfig.validateRewardedAds(points, dailyCap, cooldownSeconds)
            if (errors.isNotEmpty()) {
                _errorMessage.value = errors.first()
                return@launch
            }

            _isLoading.value = true
            try {
                val newConfig = economyConfig.value.copy(
                    rewardedAdPoints = points,
                    rewardedAdDailyCap = dailyCap,
                    rewardedAdCooldownSeconds = cooldownSeconds
                )
                repository.saveEconomyConfig(newConfig)
                _userMessage.value = "تم تحديث إعدادات إعلانات المكافآت بنجاح"
            } catch (e: Exception) {
                AppLogger.e("EconomyViewModel", "Failed to update rewarded ads config: ${e.message}")
                _errorMessage.value = "فشل حفظ إعدادات الإعلانات: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // 5. Reward Tasks CRUD (/reward_tasks/{taskId})
    // -------------------------------------------------------------

    fun saveRewardTask(task: RewardTask, isNew: Boolean) {
        viewModelScope.launch {
            val existingIds = rewardTasks.value.map { it.taskId }.toSet()
            val errors = task.validate(existingIds, isNew)
            if (errors.isNotEmpty()) {
                _errorMessage.value = errors.first()
                return@launch
            }

            _isLoading.value = true
            try {
                repository.saveRewardTask(task)
                _userMessage.value = if (isNew) "تم إنشاء المهمة بنجاح: ${task.title}" else "تم تحديث المهمة بنجاح: ${task.title}"
            } catch (e: Exception) {
                AppLogger.e("EconomyViewModel", "Failed to save reward task: ${e.message}")
                _errorMessage.value = "فشل حفظ المهمة: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleTaskActive(task: RewardTask) {
        val updated = task.copy(isActive = !task.isActive, updatedAt = System.currentTimeMillis())
        saveRewardTask(updated, isNew = false)
    }

    fun deleteRewardTask(taskId: String) {
        viewModelScope.launch {
            if (taskId.isBlank()) {
                _errorMessage.value = "معرف المهمة غير صالح"
                return@launch
            }
            _isLoading.value = true
            try {
                repository.deleteRewardTask(taskId)
                _userMessage.value = "تم حذف المهمة ($taskId) نهائياً من الكتالوج"
            } catch (e: Exception) {
                AppLogger.e("EconomyViewModel", "Failed to delete reward task: ${e.message}")
                _errorMessage.value = "فشل حذف المهمة: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // 6. User Points Operations (Atomic Transaction & Ledger)
    // -------------------------------------------------------------

    fun grantPoints(userId: String, amount: Long, reason: String) {
        viewModelScope.launch {
            if (userId.isBlank()) {
                _errorMessage.value = "يجب تحديد المستخدم أولاً"
                return@launch
            }
            if (amount <= 0L) {
                _errorMessage.value = "قيمة المنح يجب أن تكون أكبر من الصفر ($amount)"
                return@launch
            }
            if (reason.trim().isBlank()) {
                _errorMessage.value = "يجب إدخال سبب المنح لأغراض التدقيق وسجل الأمان"
                return@launch
            }

            val user = _selectedUser.value ?: allUsers.value.find { it.uid == userId || it.id == userId }
            val currentBalance = user?.pointsBalance ?: 0L
            if (currentBalance + amount > 1_000_000L) {
                _errorMessage.value = "العملية مرفوضة: رصيد المستخدم سيتجاوز الحد الأقصى المسموح (1,000,000 نقطة)"
                return@launch
            }

            val adminUser = auth.currentUser
            val adminUid = adminUser?.uid ?: "admin_manual"
            val adminEmail = adminUser?.email ?: "admin@cinestream.com"

            _isLoading.value = true
            try {
                val createdTx = repository.adjustUserPoints(
                    userId = userId,
                    amount = amount,
                    reason = reason.trim(),
                    actorUid = adminUid,
                    actorEmail = adminEmail
                )
                // Refresh local selected user state
                if (_selectedUser.value?.uid == userId || _selectedUser.value?.id == userId) {
                    _selectedUser.value = _selectedUser.value?.copy(
                        pointsBalance = createdTx.balanceAfter,
                        totalPointsEarned = (_selectedUser.value?.totalPointsEarned ?: 0L) + amount
                    )
                }
                _userMessage.value = "تم منح $amount نقطة بنجاح (الرصيد الجديد: ${createdTx.balanceAfter})"
            } catch (e: Exception) {
                AppLogger.e("EconomyViewModel", "Failed to grant points: ${e.message}")
                _errorMessage.value = "فشل منح النقاط: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removePoints(userId: String, amount: Long, reason: String) {
        viewModelScope.launch {
            if (userId.isBlank()) {
                _errorMessage.value = "يجب تحديد المستخدم أولاً"
                return@launch
            }
            if (amount <= 0L) {
                _errorMessage.value = "قيمة الخصم يجب أن تكون أكبر من الصفر ($amount)"
                return@launch
            }
            if (reason.trim().isBlank()) {
                _errorMessage.value = "يجب إدخال سبب الخصم لأغراض التدقيق وسجل الأمان"
                return@launch
            }

            val user = _selectedUser.value ?: allUsers.value.find { it.uid == userId || it.id == userId }
            val currentBalance = user?.pointsBalance ?: 0L
            if (currentBalance - amount < 0L) {
                _errorMessage.value = "العملية مرفوضة: رصيد المستخدم الحالي ($currentBalance) أقل من المبلغ المطلوب خصمه ($amount)"
                return@launch
            }

            val adminUser = auth.currentUser
            val adminUid = adminUser?.uid ?: "admin_manual"
            val adminEmail = adminUser?.email ?: "admin@cinestream.com"

            _isLoading.value = true
            try {
                val createdTx = repository.adjustUserPoints(
                    userId = userId,
                    amount = -amount,
                    reason = reason.trim(),
                    actorUid = adminUid,
                    actorEmail = adminEmail
                )
                // Refresh local selected user state
                if (_selectedUser.value?.uid == userId || _selectedUser.value?.id == userId) {
                    _selectedUser.value = _selectedUser.value?.copy(
                        pointsBalance = createdTx.balanceAfter,
                        totalPointsSpent = (_selectedUser.value?.totalPointsSpent ?: 0L) + amount
                    )
                }
                _userMessage.value = "تم خصم $amount نقطة بنجاح (الرصيد المتبقي: ${createdTx.balanceAfter})"
            } catch (e: Exception) {
                AppLogger.e("EconomyViewModel", "Failed to remove points: ${e.message}")
                _errorMessage.value = "فشل خصم النقاط: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
