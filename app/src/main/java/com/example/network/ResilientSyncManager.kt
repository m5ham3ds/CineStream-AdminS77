package com.example.network

import android.content.Context
import com.example.diagnostics.AppLogger
import com.example.models.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

enum class OperationSyncState {
    IDLE,
    PENDING_OFFLINE, // Waiting for network connection
    SYNCING,         // Currently in flight to Firestore server
    SUCCESS,         // Confirmed by Firestore server
    FAILED           // Failed after retries
}

data class SyncOperation(
    val id: String,
    val collectionName: String,
    val documentId: String,
    val payload: Map<String, Any?>,
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
    val state: OperationSyncState = OperationSyncState.IDLE,
    val retryCount: Int = 0
)

/**
 * ResilientSyncManager guarantees transactional resilience across weak networks and offline states.
 * 
 * Features:
 * 1. Application-scoped SupervisorJob: Operations survive screen navigation, ViewModel lifecycle,
 *    and app backgrounding.
 * 2. Automatic Offline Queueing: Operations executed during weak or dropped internet are held in a
 *    safe dormant state and immediately re-dispatched when network connectivity stabilizes.
 * 3. Real-time Pending State Observation: UI components can observe pending sync status per document/field
 *    to provide transparent, confidence-inspiring feedback to administrators.
 */
class ResilientSyncManager private constructor(private val context: Context) {

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val networkMonitor by lazy { NetworkMonitor(context) }

    private val _pendingOperations = MutableStateFlow<Map<String, SyncOperation>>(emptyMap())
    val pendingOperations: StateFlow<Map<String, SyncOperation>> = _pendingOperations.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val activeJobMap = ConcurrentHashMap<String, Job>()

    companion object {
        private const val TAG = "ResilientSyncManager"

        @Volatile
        private var INSTANCE: ResilientSyncManager? = null

        fun getInstance(context: Context): ResilientSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ResilientSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        // Monitor network state and auto-flush queue when connection is restored
        applicationScope.launch {
            networkMonitor.status.collect { status ->
                AppLogger.d(TAG, "Network status changed to: $status. Checking pending operations queue.")
                if (status == NetworkStatus.ONLINE) {
                    flushQueue()
                }
            }
        }
    }

    /**
     * Dispatches a resilient Firestore write operation.
     * Guarantees local cache write immediately, persists operation across network failure,
     * and automatically retries upon network reconnection.
     */
    fun enqueueWrite(
        opId: String,
        collection: String,
        documentId: String,
        payload: Map<String, Any?>,
        description: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        activeJobMap[opId]?.cancel()

        val operation = SyncOperation(
            id = opId,
            collectionName = collection,
            documentId = documentId,
            payload = payload,
            description = description,
            state = if (networkMonitor.isOnline.value) OperationSyncState.SYNCING else OperationSyncState.PENDING_OFFLINE
        )

        _pendingOperations.update { current ->
            current + (opId to operation)
        }

        AppLogger.i(TAG, "Enqueued resilient write operation: $opId ($description)")

        val job = applicationScope.launch {
            executeOperation(operation, onSuccess, onError)
        }
        activeJobMap[opId] = job
    }

    private suspend fun executeOperation(
        operation: SyncOperation,
        onSuccess: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        val opId = operation.id
        val docRef = firestore.collection(operation.collectionName).document(operation.documentId)

        try {
            // 1. Immediately persist to Firestore's local disk cache.
            // This guarantees immediate reflection in UI snapshot listeners.
            docRef.set(operation.payload, SetOptions.merge())

            // 2. If offline or unstable, remain dormant in queue until connection is restored
            if (!networkMonitor.isOnline.value) {
                _pendingOperations.update { current ->
                    val existing = current[opId] ?: operation
                    current + (opId to existing.copy(state = OperationSyncState.PENDING_OFFLINE))
                }
                AppLogger.i(TAG, "Operation $opId safely dormant in queue; awaiting network connection.")
                return
            }

            _pendingOperations.update { current ->
                val existing = current[opId] ?: operation
                current + (opId to existing.copy(state = OperationSyncState.SYNCING))
            }

            // 3. Await server acknowledgment with safety timeout
            val serverAck = withTimeoutOrNull(8000L) {
                docRef.set(operation.payload, SetOptions.merge()).await()
                true
            }

            if (serverAck == true) {
                // Acknowledged by cloud server!
                _pendingOperations.update { current -> current - opId }
                AppLogger.i(TAG, "Operation $opId successfully synchronized with cloud server.")
                withContext(Dispatchers.Main) {
                    onSuccess?.invoke()
                }
            } else {
                // Timeout due to weak connection: preserve in queue as dormant
                AppLogger.w(TAG, "Operation $opId server ack timed out. Preserving in queue as PENDING_OFFLINE.")
                _pendingOperations.update { current ->
                    val existing = current[opId] ?: operation
                    current + (opId to existing.copy(state = OperationSyncState.PENDING_OFFLINE))
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Operation $opId encountered exception: ${e.message}. Keeping in pending queue.")
            _pendingOperations.update { current ->
                val existing = current[opId] ?: operation
                current + (opId to existing.copy(
                    state = OperationSyncState.PENDING_OFFLINE,
                    retryCount = existing.retryCount + 1
                ))
            }
            withContext(Dispatchers.Main) {
                onError?.invoke(e)
            }
        } finally {
            activeJobMap.remove(opId)
        }
    }

    /**
     * Flushes all pending dormant operations when network stabilizes.
     */
    fun flushQueue() {
        val pending = _pendingOperations.value.values.filter { 
            it.state == OperationSyncState.PENDING_OFFLINE || it.state == OperationSyncState.FAILED 
        }

        if (pending.isEmpty()) return

        AppLogger.i(TAG, "Flushing ${pending.size} pending operations after network stabilization...")
        _isSyncing.value = true

        applicationScope.launch {
            for (op in pending) {
                executeOperation(op)
            }
            _isSyncing.value = false
        }
    }

    fun isOperationPending(opId: String): Boolean {
        return _pendingOperations.value.containsKey(opId)
    }

    fun isDocumentPending(documentId: String): Boolean {
        return _pendingOperations.value.values.any { it.documentId == documentId }
    }

    fun getPendingOperationsForDoc(documentId: String): List<SyncOperation> {
        return _pendingOperations.value.values.filter { it.documentId == documentId }
    }
}
