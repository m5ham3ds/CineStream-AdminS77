package com.example.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diagnostics.AppLogger
import com.example.models.ManagedExtension
import com.example.models.SearchOrderCategory
import com.example.models.SearchOrderConfig
import com.example.repository.ManagedExtensionRepository
import com.example.repository.SearchOrderRepository
import com.example.validation.SearchOrderValidator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SearchOrderViewModel(
    private val searchOrderRepository: SearchOrderRepository = SearchOrderRepository(),
    private val extensionRepository: ManagedExtensionRepository = ManagedExtensionRepository()
) : ViewModel() {

    private val _availableExtensionsMap = MutableStateFlow<Map<String, ManagedExtension>>(emptyMap())
    val availableExtensionsMap = _availableExtensionsMap.asStateFlow()

    private val _savedOrderConfig = MutableStateFlow(SearchOrderConfig())
    val savedOrderConfig = _savedOrderConfig.asStateFlow()

    private val _currentOrderConfig = MutableStateFlow(SearchOrderConfig())
    val currentOrderConfig = _currentOrderConfig.asStateFlow()

    private val _selectedCategory = MutableStateFlow(SearchOrderCategory.MOVIE)
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    val isDirty: StateFlow<Boolean> = combine(_currentOrderConfig, _savedOrderConfig) { current, saved ->
        current.movie != saved.movie || current.tv != saved.tv || current.anime != saved.anime
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // 1. Observe all extensions from canonical catalog
        viewModelScope.launch {
            extensionRepository.getAllManagedExtensions()
                .catch { e ->
                    AppLogger.w("SearchOrderViewModel", "Error fetching extensions: ${e.message}")
                }
                .collect { list ->
                    val map = list.associateBy { it.extensionId }
                    _availableExtensionsMap.value = map

                    // If order config is still empty and we have extensions, populate initial defaults if saved is empty
                    if (_savedOrderConfig.value.movie.isEmpty() &&
                        _savedOrderConfig.value.tv.isEmpty() &&
                        _savedOrderConfig.value.anime.isEmpty() &&
                        list.isNotEmpty()
                    ) {
                        val initialDefaults = searchOrderRepository.getDefaultSearchOrder(list)
                        _currentOrderConfig.value = initialDefaults
                    }
                }
        }

        // 2. Observe canonical search order configuration from Firestore
        viewModelScope.launch {
            searchOrderRepository.getSearchOrder()
                .catch { e ->
                    AppLogger.w("SearchOrderViewModel", "Error observing search order: ${e.message}")
                    _isLoading.value = false
                }
                .collect { config ->
                    _isLoading.value = false
                    _savedOrderConfig.value = config

                    // If user has not made unsaved changes, sync current with saved
                    if (!isDirty.value) {
                        if (config.movie.isNotEmpty() || config.tv.isNotEmpty() || config.anime.isNotEmpty()) {
                            _currentOrderConfig.value = config
                        }
                    }
                }
        }
    }

    fun setSelectedCategory(cat: SearchOrderCategory) {
        _selectedCategory.value = cat
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun moveUp(category: SearchOrderCategory, index: Int) {
        if (index <= 0) return
        val list = _currentOrderConfig.value.getOrderForCategory(category).toMutableList()
        if (index >= list.size) return

        val temp = list[index]
        list[index] = list[index - 1]
        list[index - 1] = temp

        _currentOrderConfig.value = _currentOrderConfig.value.withOrderForCategory(category, list)
    }

    fun moveDown(category: SearchOrderCategory, index: Int) {
        val list = _currentOrderConfig.value.getOrderForCategory(category).toMutableList()
        if (index < 0 || index >= list.size - 1) return

        val temp = list[index]
        list[index] = list[index + 1]
        list[index + 1] = temp

        _currentOrderConfig.value = _currentOrderConfig.value.withOrderForCategory(category, list)
    }

    fun remove(category: SearchOrderCategory, index: Int) {
        val list = _currentOrderConfig.value.getOrderForCategory(category).toMutableList()
        if (index < 0 || index >= list.size) return

        val removedId = list.removeAt(index)
        _currentOrderConfig.value = _currentOrderConfig.value.withOrderForCategory(category, list)
        _statusMessage.value = "Removed $removedId from ${category.labelEn}"
    }

    fun add(category: SearchOrderCategory, extensionId: String) {
        val normalized = extensionId.trim()
        val ext = _availableExtensionsMap.value[normalized]
        if (ext == null) {
            _statusMessage.value = "Extension '$normalized' not found"
            return
        }

        if (!SearchOrderValidator.isExtensionEligibleForCategory(ext, category)) {
            _statusMessage.value = "Extension '${ext.name}' does not support ${category.labelEn}"
            return
        }

        val list = _currentOrderConfig.value.getOrderForCategory(category).toMutableList()
        if (list.contains(normalized)) {
            _statusMessage.value = "Extension '${ext.name}' is already in ${category.labelEn} search order"
            return
        }

        list.add(normalized)
        _currentOrderConfig.value = _currentOrderConfig.value.withOrderForCategory(category, list)
        _statusMessage.value = "Added '${ext.name}' to ${category.labelEn} search order"
    }

    fun resetToDefaults() {
        val extensionsList = _availableExtensionsMap.value.values.toList()
        val defaults = searchOrderRepository.getDefaultSearchOrder(extensionsList)
        _currentOrderConfig.value = defaults
        _statusMessage.value = "Reset search orders to capability defaults"
    }

    fun discardChanges() {
        _currentOrderConfig.value = _savedOrderConfig.value
        _statusMessage.value = "Discarded unsaved changes"
    }

    fun save(adminEmail: String = "", onSuccess: () -> Unit = {}) {
        val configToSave = _currentOrderConfig.value
        val extensionsMap = _availableExtensionsMap.value

        val errors = SearchOrderValidator.validate(configToSave, extensionsMap)
        if (errors.isNotEmpty()) {
            _statusMessage.value = "Validation error: ${errors.first()}"
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            val result = searchOrderRepository.saveSearchOrder(configToSave, extensionsMap, adminEmail)
            _isSaving.value = false

            result.onSuccess {
                _savedOrderConfig.value = configToSave
                _statusMessage.value = "Search order saved successfully to /config/search_order"
                onSuccess()
            }.onFailure { e ->
                _statusMessage.value = "Failed to save search order: ${e.message}"
            }
        }
    }
}
