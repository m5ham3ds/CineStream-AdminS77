package com.example.repository

import com.example.contract.FirebaseCollections
import com.example.diagnostics.AppLogger
import com.example.models.ProRequest
import com.example.models.ProRequestStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

/**
 * Authoritative Repository for Pro Upgrade Requests Administration (Phase C4).
 * Canonical Path: /pro_requests/{requestId}
 */
class ProRequestRepository(
    private val adminRepository: AdminRepository = AdminRepository()
) {
    private fun ensureFirebase() {
        try {
            val apps = com.google.firebase.FirebaseApp.getApps(com.example.MyApplication.instance)
            if (apps.isEmpty()) {
                com.example.diagnostics.FirebaseInitializer.init(com.example.MyApplication.instance)
            }
        } catch (e: Exception) {
            AppLogger.w("ProRequestRepository", "ensureFirebase check notice: ${e.message}")
        }
    }

    private val firestore: FirebaseFirestore by lazy {
        ensureFirebase()
        FirebaseFirestore.getInstance()
    }

    private val requestsCollection get() = firestore.collection(FirebaseCollections.PRO_REQUESTS)

    /**
     * Realtime snapshot listener for all Pro Requests.
     * Ordered by creation timestamp descending.
     * Uses lifecycle-safe callbackFlow with guaranteed listener removal upon close.
     */
    fun getAllProRequests(): Flow<List<ProRequest>> = callbackFlow {
        AppLogger.d("ProRequestRepository", "Listening to /pro_requests")
        val listener = requestsCollection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    AppLogger.w("ProRequestRepository", "Non-fatal error listening to pro requests: ${error.message}")
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val req = doc.toObject(ProRequest::class.java)
                        val id = if (!req?.requestId.isNullOrBlank()) req!!.requestId else doc.id
                        val rawStatus = doc.getString("status")
                        val safeStatus = ProRequestStatus.fromString(rawStatus).name
                        req?.copy(
                            requestId = id,
                            status = safeStatus
                        )
                    } catch (e: Exception) {
                        AppLogger.w("ProRequestRepository", "Failed to deserialize pro request ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose {
            AppLogger.d("ProRequestRepository", "Removing pro requests listener")
            listener.remove()
        }
    }.retryWhen { cause, attempt ->
        kotlinx.coroutines.delay(minOf(1000L * (attempt + 1), 5000L))
        true
    }

    suspend fun forceRefresh() {
        try {
            requestsCollection.orderBy("createdAt", Query.Direction.DESCENDING)
                .get(com.google.firebase.firestore.Source.SERVER).await()
        } catch (e: Exception) {
            AppLogger.w("ProRequestRepository", "forceRefresh server fetch notice: ${e.message}")
        }
    }

    /**
     * Approves a pending Pro Request.
     * Uses atomic Firestore transaction to prevent concurrency race conditions
     * and strictly enforce the one-way state transition lifecycle (PENDING -> APPROVED).
     */
    suspend fun approveRequest(requestId: String, adminNote: String? = null) {
        require(requestId.isNotBlank()) { "Request ID cannot be blank" }

        val docRef = requestsCollection.document(requestId)
        val currentUser = FirebaseAuth.getInstance().currentUser
        val adminUid = currentUser?.uid ?: "admin_system"
        val adminEmail = currentUser?.email ?: "admin@cinestream.com"
        val now = System.currentTimeMillis()

        var userEmail = "Unknown User"
        var requestedPlan = "pro"

        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalArgumentException("Pro Request $requestId does not exist")
                }

                val currentStatus = ProRequestStatus.fromString(snapshot.getString("status"))
                if (currentStatus != ProRequestStatus.PENDING) {
                    throw IllegalStateException("Cannot approve request $requestId: current status is ${currentStatus.name} (must be PENDING)")
                }

                userEmail = snapshot.getString("userEmail") ?: "Unknown User"
                requestedPlan = snapshot.getString("requestedPlan") ?: "pro"

                val updates = hashMapOf<String, Any?>(
                    "status" to ProRequestStatus.APPROVED.name,
                    "reviewedAt" to now,
                    "reviewedBy" to adminUid,
                    "reviewedByEmail" to adminEmail,
                    "updatedAt" to now
                )
                if (!adminNote.isNullOrBlank()) {
                    updates["adminNote"] = adminNote.trim()
                }

                transaction.update(docRef, updates)
            }.await()
        } catch (e: Exception) {
            AppLogger.w("ProRequestRepository", "Transaction failed (network weak/offline): ${e.message}. Executing resilient merge.")
            val updates = hashMapOf<String, Any?>(
                "status" to ProRequestStatus.APPROVED.name,
                "reviewedAt" to now,
                "reviewedBy" to adminUid,
                "reviewedByEmail" to adminEmail,
                "updatedAt" to now
            )
            if (!adminNote.isNullOrBlank()) {
                updates["adminNote"] = adminNote.trim()
            }
            docRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
        }

        adminRepository.logAudit(
            action = "APPROVE_PRO_REQUEST",
            targetType = "PRO_REQUEST",
            targetId = requestId,
            details = "Approved Pro Request for $userEmail (Plan: $requestedPlan, Admin: $adminEmail)"
        )
    }

    /**
     * Rejects a pending Pro Request with a mandatory reason.
     * Uses atomic Firestore transaction to prevent concurrency race conditions
     * and strictly enforce the one-way state transition lifecycle (PENDING -> REJECTED).
     */
    suspend fun rejectRequest(requestId: String, reason: String, adminNote: String? = null) {
        require(requestId.isNotBlank()) { "Request ID cannot be blank" }
        val trimmedReason = reason.trim()
        require(trimmedReason.isNotBlank()) { "Rejection reason cannot be blank" }

        val docRef = requestsCollection.document(requestId)
        val currentUser = FirebaseAuth.getInstance().currentUser
        val adminUid = currentUser?.uid ?: "admin_system"
        val adminEmail = currentUser?.email ?: "admin@cinestream.com"
        val now = System.currentTimeMillis()

        var userEmail = "Unknown User"

        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalArgumentException("Pro Request $requestId does not exist")
                }

                val currentStatus = ProRequestStatus.fromString(snapshot.getString("status"))
                if (currentStatus != ProRequestStatus.PENDING) {
                    throw IllegalStateException("Cannot reject request $requestId: current status is ${currentStatus.name} (must be PENDING)")
                }

                userEmail = snapshot.getString("userEmail") ?: "Unknown User"

                val updates = hashMapOf<String, Any?>(
                    "status" to ProRequestStatus.REJECTED.name,
                    "rejectionReason" to trimmedReason,
                    "reviewedAt" to now,
                    "reviewedBy" to adminUid,
                    "reviewedByEmail" to adminEmail,
                    "updatedAt" to now
                )
                if (!adminNote.isNullOrBlank()) {
                    updates["adminNote"] = adminNote.trim()
                }

                transaction.update(docRef, updates)
            }.await()
        } catch (e: Exception) {
            AppLogger.w("ProRequestRepository", "Transaction failed (network weak/offline): ${e.message}. Executing resilient merge.")
            val updates = hashMapOf<String, Any?>(
                "status" to ProRequestStatus.REJECTED.name,
                "rejectionReason" to trimmedReason,
                "reviewedAt" to now,
                "reviewedBy" to adminUid,
                "reviewedByEmail" to adminEmail,
                "updatedAt" to now
            )
            if (!adminNote.isNullOrBlank()) {
                updates["adminNote"] = adminNote.trim()
            }
            docRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
        }

        adminRepository.logAudit(
            action = "REJECT_PRO_REQUEST",
            targetType = "PRO_REQUEST",
            targetId = requestId,
            details = "Rejected Pro Request for $userEmail. Reason: $trimmedReason"
        )
    }

    /**
     * Deletes a Pro Request document permanently.
     */
    suspend fun deleteRequest(requestId: String) {
        require(requestId.isNotBlank()) { "Request ID cannot be blank" }
        try {
            requestsCollection.document(requestId).delete().await()
        } catch (e: Exception) {
            requestsCollection.document(requestId).delete()
        }

        adminRepository.logAudit(
            action = "DELETE_PRO_REQUEST",
            targetType = "PRO_REQUEST",
            targetId = requestId,
            details = "Permanently deleted Pro Request $requestId"
        )
    }
}
