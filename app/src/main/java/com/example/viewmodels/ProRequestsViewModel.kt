package com.example.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diagnostics.AppLogger
import com.example.models.ProRequest
import com.example.models.ProRequestStatus
import com.example.repository.ProRequestRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProRequestsViewModel(
    private val repository: ProRequestRepository = ProRequestRepository()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow<ProRequestStatus?>(null)
    val statusFilter = _statusFilter.asStateFlow()

    private val _selectedRequest = MutableStateFlow<ProRequest?>(null)
    val selectedRequest = _selectedRequest.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isPerformingAction = MutableStateFlow(false)
    val isPerformingAction = _isPerformingAction.asStateFlow()

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
                AppLogger.w("ProRequestsViewModel", "forceRefresh error: ${e.message}")
            } finally {
                kotlinx.coroutines.delay(400)
                _isRefreshing.value = false
            }
        }
    }

    val allRequests: StateFlow<List<ProRequest>> = repository.getAllProRequests()
        .onEach { _isLoading.value = false }
        .catch { e ->
            AppLogger.w("ProRequestsViewModel", "Pro requests notice: ${e.message}")
            _isLoading.value = false
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredRequests: StateFlow<List<ProRequest>> = combine(
        allRequests,
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
                it.userEmail.lowercase().contains(q) ||
                it.userName.lowercase().contains(q) ||
                it.requestId.lowercase().contains(q) ||
                it.requestedPlan.lowercase().contains(q) ||
                it.paymentMethod?.lowercase()?.contains(q) == true ||
                it.userNote.lowercase().contains(q)
            }
        }
        res.sortedByDescending { it.createdAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalCount = allRequests.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val pendingCount = allRequests.map { it.count { r -> r.isPending } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val approvedCount = allRequests.map { it.count { r -> r.isApproved } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val rejectedCount = allRequests.map { it.count { r -> r.isRejected } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun setStatusFilter(filter: ProRequestStatus?) {
        _statusFilter.value = filter
    }

    fun selectRequest(request: ProRequest?) {
        _selectedRequest.value = request
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun approveRequest(requestId: String, adminNote: String? = null, onSuccess: () -> Unit = {}) {
        if (_isPerformingAction.value) return // Guard against duplicate action
        _isPerformingAction.value = true

        viewModelScope.launch {
            try {
                repository.approveRequest(requestId, adminNote)
                _statusMessage.value = "Request approved successfully"
                onSuccess()
            } catch (e: Exception) {
                AppLogger.e("ProRequestsViewModel", "Approval error: ${e.message}", e)
                _statusMessage.value = "Error approving request: ${e.message}"
            } finally {
                _isPerformingAction.value = false
            }
        }
    }

    fun rejectRequest(requestId: String, reason: String, adminNote: String? = null, onSuccess: () -> Unit = {}) {
        if (_isPerformingAction.value) return // Guard against duplicate action
        val trimmed = reason.trim()
        if (trimmed.isBlank()) {
            _statusMessage.value = "Rejection reason cannot be blank"
            return
        }

        _isPerformingAction.value = true
        viewModelScope.launch {
            try {
                repository.rejectRequest(requestId, trimmed, adminNote)
                _statusMessage.value = "Request rejected"
                onSuccess()
            } catch (e: Exception) {
                AppLogger.e("ProRequestsViewModel", "Rejection error: ${e.message}", e)
                _statusMessage.value = "Error rejecting request: ${e.message}"
            } finally {
                _isPerformingAction.value = false
            }
        }
    }

    fun deleteRequest(requestId: String, onSuccess: () -> Unit = {}) {
        if (_isPerformingAction.value) return
        _isPerformingAction.value = true

        viewModelScope.launch {
            try {
                repository.deleteRequest(requestId)
                _statusMessage.value = "Request deleted"
                if (_selectedRequest.value?.requestId == requestId) {
                    _selectedRequest.value = null
                }
                onSuccess()
            } catch (e: Exception) {
                AppLogger.e("ProRequestsViewModel", "Delete error: ${e.message}", e)
                _statusMessage.value = "Error deleting request: ${e.message}"
            } finally {
                _isPerformingAction.value = false
            }
        }
    }
}
