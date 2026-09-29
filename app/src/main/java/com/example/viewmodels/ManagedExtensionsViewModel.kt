package com.example.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diagnostics.AppLogger
import com.example.models.ManagedExtension
import com.example.models.ManagedExtensionStatus
import com.example.repository.ManagedExtensionRepository
import com.example.validation.ManagedExtensionValidator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ManagedExtensionsViewModel(
    private val repository: ManagedExtensionRepository = ManagedExtensionRepository()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow<ManagedExtensionStatus?>(null)
    val statusFilter = _statusFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.forceRefresh()
            } catch (e: Exception) {
                AppLogger.w("ManagedExtViewModel", "forceRefresh error: ${e.message}")
            } finally {
                kotlinx.coroutines.delay(400)
                _isRefreshing.value = false
            }
        }
    }

    private val allExtensions: StateFlow<List<ManagedExtension>> = repository.getAllManagedExtensions()
        .onEach { _isLoading.value = false }
        .catch { e ->
            AppLogger.w("ManagedExtViewModel", "Managed extensions notice: ${e.message}")
            _isLoading.value = false
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredExtensions: StateFlow<List<ManagedExtension>> = combine(
        allExtensions,
        _searchQuery,
        _statusFilter
    ) { list, query, filter ->
        var res = list
        if (filter != null) {
            res = res.filter { it.statusEnum == filter }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            res = res.filter {
                it.name.lowercase().contains(q) ||
                it.scraperKey.lowercase().contains(q) ||
                it.extensionId.lowercase().contains(q) ||
                it.baseUrl.lowercase().contains(q)
            }
        }
        res.sortedByDescending { it.priority }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalCount = allExtensions.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val activeCount = allExtensions.map { it.count { ext -> ext.statusEnum == ManagedExtensionStatus.ACTIVE } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val maintenanceCount = allExtensions.map { it.count { ext -> ext.statusEnum == ManagedExtensionStatus.MAINTENANCE } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val disabledCount = allExtensions.map { it.count { ext -> ext.statusEnum == ManagedExtensionStatus.DISABLED } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun setStatusFilter(filter: ManagedExtensionStatus?) {
        _statusFilter.value = filter
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun saveExtension(extension: ManagedExtension, isNew: Boolean, onSuccess: () -> Unit = {}) {
        val validationErrors = ManagedExtensionValidator.validate(extension)
        if (validationErrors.isNotEmpty()) {
            _statusMessage.value = "Validation error: ${validationErrors.first()}"
            return
        }

        viewModelScope.launch {
            try {
                repository.saveManagedExtension(extension, isNew)
                _statusMessage.value = if (isNew) "Managed Extension created successfully" else "Managed Extension updated"
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "Error saving extension: ${e.message}"
            }
        }
    }

    fun updateStatus(id: String, status: ManagedExtensionStatus) {
        viewModelScope.launch {
            try {
                repository.updateStatus(id, status)
                _statusMessage.value = "Status updated to ${status.name}"
            } catch (e: Exception) {
                _statusMessage.value = "Error updating status: ${e.message}"
            }
        }
    }

    fun updatePriority(id: String, priority: Int) {
        if (priority < 0) {
            _statusMessage.value = "Priority must be 0 or greater"
            return
        }
        viewModelScope.launch {
            try {
                repository.updatePriority(id, priority)
                _statusMessage.value = "Priority updated to $priority"
            } catch (e: Exception) {
                _statusMessage.value = "Error updating priority: ${e.message}"
            }
        }
    }

    fun updateBaseUrl(id: String, newBaseUrl: String) {
        if (newBaseUrl.isBlank()) {
            _statusMessage.value = "URL cannot be blank"
            return
        }
        viewModelScope.launch {
            try {
                repository.updateBaseUrl(id, newBaseUrl)
                _statusMessage.value = "Base URL updated successfully"
            } catch (e: Exception) {
                _statusMessage.value = "Error updating Base URL: ${e.message}"
            }
        }
    }

    fun seedDefaultScrapers() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val count = repository.seedDefaultExtensions()
                _statusMessage.value = "Synced $count default scrapers (qfilm, witanime, egydead, ...)"
            } catch (e: Exception) {
                _statusMessage.value = "Error syncing default scrapers: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun deprecateExtension(id: String) {
        updateStatus(id, ManagedExtensionStatus.DEPRECATED)
    }

    fun deleteExtension(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteManagedExtension(id)
                _statusMessage.value = "Managed Extension deleted"
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "Error deleting extension: ${e.message}"
            }
        }
    }
}
