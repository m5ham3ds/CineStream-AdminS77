package com.example.repository

import com.example.contract.FirebaseCollections
import com.example.contract.FirebaseSubcollections
import com.example.diagnostics.AppLogger
import com.example.models.SupportConversation
import com.example.models.SupportConversationStatus
import com.example.models.SupportMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

/**
 * Authoritative Repository for Support Conversations & Chat (Phase C3).
 *
 * Canonical Paths:
 * - Thread: /support_conversations/{conversationId}
 * - Messages: /support_conversations/{conversationId}/messages/{messageId}
 */
class SupportRepository(
    private val adminRepository: AdminRepository = AdminRepository()
) {
    private fun ensureFirebase() {
        try {
            val apps = com.google.firebase.FirebaseApp.getApps(com.example.MyApplication.instance)
            if (apps.isEmpty()) {
                com.example.diagnostics.FirebaseInitializer.init(com.example.MyApplication.instance)
            }
        } catch (e: Exception) {
            AppLogger.w("SupportRepository", "ensureFirebase check notice: ${e.message}")
        }
    }

    private val firestore: FirebaseFirestore by lazy {
        ensureFirebase()
        FirebaseFirestore.getInstance()
    }

    private val conversationsCollection get() = firestore.collection(FirebaseCollections.SUPPORT_CONVERSATIONS)

    /**
     * Realtime snapshot listener for all support conversations.
     * Ordered by last message timestamp descending.
     * Uses lifecycle-safe callbackFlow with guaranteed listener removal upon close.
     */
    fun getAllConversations(): Flow<List<SupportConversation>> = callbackFlow {
        AppLogger.d("SupportRepository", "Listening to /support_conversations")
        val listener = conversationsCollection
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    AppLogger.w("SupportRepository", "Non-fatal error listening to conversations: ${error.message}")
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val conv = doc.toObject(SupportConversation::class.java)
                        val id = if (!conv?.conversationId.isNullOrBlank()) conv!!.conversationId else doc.id
                        val rawStatus = doc.getString("status")
                        val safeStatus = SupportConversationStatus.fromString(rawStatus).name
                        conv?.copy(
                            conversationId = id,
                            status = safeStatus
                        )
                    } catch (e: Exception) {
                        AppLogger.w("SupportRepository", "Failed to deserialize conversation ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose {
            AppLogger.d("SupportRepository", "Removing conversations listener")
            listener.remove()
        }
    }.retryWhen { cause, attempt ->
        kotlinx.coroutines.delay(minOf(1000L * (attempt + 1), 5000L))
        true
    }

    suspend fun forceRefresh() {
        try {
            conversationsCollection.orderBy("lastMessageAt", Query.Direction.DESCENDING)
                .get(com.google.firebase.firestore.Source.SERVER).await()
        } catch (e: Exception) {
            AppLogger.w("SupportRepository", "forceRefresh server fetch notice: ${e.message}")
        }
    }

    /**
     * Realtime snapshot listener for messages in a given conversation.
     * Bounded query (default 100 messages) ordered chronologically.
     */
    fun getMessages(conversationId: String, limit: Long = 100): Flow<List<SupportMessage>> = callbackFlow {
        if (conversationId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        AppLogger.d("SupportRepository", "Listening to messages for conversation $conversationId")
        val listener = conversationsCollection.document(conversationId)
            .collection(FirebaseSubcollections.MESSAGES)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(limit)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    AppLogger.w("SupportRepository", "Non-fatal error listening to messages ($conversationId): ${error.message}")
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val msg = doc.toObject(SupportMessage::class.java)
                        val id = if (!msg?.messageId.isNullOrBlank()) msg!!.messageId else doc.id
                        msg?.copy(
                            messageId = id,
                            conversationId = conversationId
                        )
                    } catch (e: Exception) {
                        AppLogger.w("SupportRepository", "Failed to deserialize message ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose {
            AppLogger.d("SupportRepository", "Removing messages listener for $conversationId")
            listener.remove()
        }
    }.retryWhen { cause, attempt ->
        kotlinx.coroutines.delay(minOf(1000L * (attempt + 1), 5000L))
        true
    }

    /**
     * Sends an authoritative Admin reply.
     * Atomically creates message in subcollection and updates parent thread metadata.
     */
    suspend fun sendAdminReply(conversationId: String, text: String) {
        val trimmedText = text.trim()
        require(trimmedText.isNotBlank()) { "Message content cannot be blank" }
        require(conversationId.isNotBlank()) { "Conversation ID cannot be blank" }

        val currentUser = FirebaseAuth.getInstance().currentUser
        val adminUid = currentUser?.uid ?: "admin_system"
        val adminEmail = currentUser?.email ?: "admin@cinestream.com"
        val now = System.currentTimeMillis()

        val convDocRef = conversationsCollection.document(conversationId)
        val messagesSubcollection = convDocRef.collection(FirebaseSubcollections.MESSAGES)
        val newMessageDocRef = messagesSubcollection.document()

        val messageData = hashMapOf<String, Any?>(
            "messageId" to newMessageDocRef.id,
            "conversationId" to conversationId,
            "senderId" to adminUid,
            "senderRole" to "admin",
            "senderEmail" to adminEmail,
            "text" to trimmedText,
            "mediaUrl" to null,
            "timestamp" to now,
            "read" to false
        )

        val conversationUpdate = hashMapOf<String, Any?>(
            "lastMessage" to trimmedText,
            "lastMessageAt" to now,
            "lastSenderRole" to "admin",
            "unreadByAdmin" to false,
            "unreadByUser" to true,
            "updatedAt" to now
        )

        // Write message document and update conversation metadata atomically using Firestore batch
        val batch = firestore.batch()
        batch.set(newMessageDocRef, messageData)
        batch.set(convDocRef, conversationUpdate, SetOptions.merge())
        batch.commit().await()

        // Audit log (metadata only to avoid logging private user sensitive content)
        adminRepository.logAudit(
            action = "SEND_SUPPORT_MESSAGE",
            targetType = "SUPPORT",
            targetId = conversationId,
            details = "Admin replied in support conversation $conversationId (chars: ${trimmedText.length})"
        )
    }

    /**
     * Updates conversation lifecycle status (OPEN, PENDING, RESOLVED, CLOSED).
     */
    suspend fun updateStatus(conversationId: String, newStatus: SupportConversationStatus) {
        require(conversationId.isNotBlank()) { "Conversation ID cannot be blank" }
        val now = System.currentTimeMillis()

        conversationsCollection.document(conversationId).set(
            mapOf(
                "status" to newStatus.name,
                "updatedAt" to now
            ),
            SetOptions.merge()
        )

        adminRepository.logAudit(
            action = "UPDATE_SUPPORT_STATUS",
            targetType = "SUPPORT",
            targetId = conversationId,
            details = "Support conversation status changed to ${newStatus.name}"
        )
    }

    /**
     * Marks thread as read by Admin.
     */
    suspend fun markAsReadByAdmin(conversationId: String) {
        if (conversationId.isBlank()) return
        try {
            conversationsCollection.document(conversationId).set(
                mapOf("unreadByAdmin" to false),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            AppLogger.w("SupportRepository", "Could not mark unreadByAdmin false: ${e.message}")
        }
    }

    /**
     * Permanently deletes a support conversation thread.
     */
    suspend fun deleteConversation(conversationId: String) {
        require(conversationId.isNotBlank()) { "Conversation ID cannot be blank" }
        conversationsCollection.document(conversationId).delete()

        adminRepository.logAudit(
            action = "DELETE_SUPPORT_CONVERSATION",
            targetType = "SUPPORT",
            targetId = conversationId,
            details = "Permanently deleted support conversation $conversationId"
        )
    }
}
