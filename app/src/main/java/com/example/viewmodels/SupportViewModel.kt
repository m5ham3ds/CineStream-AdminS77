package com.example.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diagnostics.AppLogger
import com.example.models.SupportConversation
import com.example.models.SupportConversationStatus
import com.example.models.SupportMessage
import com.example.repository.SupportRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SupportViewModel(
    private val repository: SupportRepository = SupportRepository()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow<SupportConversationStatus?>(null)
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
                AppLogger.w("SupportViewModel", "forceRefresh error: ${e.message}")
            } finally {
                kotlinx.coroutines.delay(400)
                _isRefreshing.value = false
            }
        }
    }

    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    private val _selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversationId = _selectedConversationId.asStateFlow()

    val allConversations: StateFlow<List<SupportConversation>> = repository.getAllConversations()
        .onEach { _isLoading.value = false }
        .catch { e ->
            AppLogger.w("SupportViewModel", "Conversations notice: ${e.message}")
            _isLoading.value = false
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredConversations: StateFlow<List<SupportConversation>> = combine(
        allConversations,
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
                it.subject.lowercase().contains(q) ||
                it.lastMessage.lowercase().contains(q) ||
                it.conversationId.lowercase().contains(q) ||
                it.userId.lowercase().contains(q)
            }
        }
        res.sortedByDescending { it.lastMessageAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedConversation: StateFlow<SupportConversation?> = combine(
        allConversations,
        _selectedConversationId
    ) { list, id ->
        if (id == null) null else list.firstOrNull { it.conversationId == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val messages: StateFlow<List<SupportMessage>> = _selectedConversationId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.getMessages(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalCount = allConversations.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val openCount = allConversations.map { it.count { c -> c.statusEnum == SupportConversationStatus.OPEN } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val pendingCount = allConversations.map { it.count { c -> c.statusEnum == SupportConversationStatus.PENDING } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val resolvedCount = allConversations.map { it.count { c -> c.statusEnum == SupportConversationStatus.RESOLVED } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val unreadCount = allConversations.map { it.count { c -> c.unreadByAdmin } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun setStatusFilter(filter: SupportConversationStatus?) {
        _statusFilter.value = filter
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun selectConversation(conversationId: String) {
        _selectedConversationId.value = conversationId
        viewModelScope.launch {
            repository.markAsReadByAdmin(conversationId)
        }
    }

    fun clearSelectedConversation() {
        _selectedConversationId.value = null
    }

    fun sendReply(conversationId: String, text: String, onSuccess: () -> Unit = {}) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            _statusMessage.value = "Cannot send an empty message"
            return
        }
        if (_isSending.value) return // Prevent accidental duplicate sends

        _isSending.value = true
        viewModelScope.launch {
            try {
                repository.sendAdminReply(conversationId, trimmed)
                _statusMessage.value = "Reply sent"
                onSuccess()
            } catch (e: Exception) {
                AppLogger.e("SupportViewModel", "Failed to send reply: ${e.message}", e)
                _statusMessage.value = "Error sending reply: ${e.message}"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun updateStatus(conversationId: String, newStatus: SupportConversationStatus) {
        viewModelScope.launch {
            try {
                repository.updateStatus(conversationId, newStatus)
                _statusMessage.value = "Status updated to ${newStatus.name}"
            } catch (e: Exception) {
                AppLogger.e("SupportViewModel", "Failed to update status: ${e.message}", e)
                _statusMessage.value = "Error updating status: ${e.message}"
            }
        }
    }

    fun deleteConversation(conversationId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteConversation(conversationId)
                _statusMessage.value = "Conversation deleted"
                if (_selectedConversationId.value == conversationId) {
                    _selectedConversationId.value = null
                }
                onSuccess()
            } catch (e: Exception) {
                AppLogger.e("SupportViewModel", "Failed to delete conversation: ${e.message}", e)
                _statusMessage.value = "Error deleting conversation: ${e.message}"
            }
        }
    }
}
