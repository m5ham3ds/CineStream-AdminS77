package com.example.models

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Canonical lifecycle states for Pro Upgrade Requests (Phase C4).
 * Supported: PENDING, APPROVED, REJECTED.
 *
 * Security Invariant: Unknown/malformed status safely resolves to PENDING or REJECTED,
 * NEVER silently interpreted as APPROVED.
 */
enum class ProRequestStatus {
    PENDING,
    APPROVED,
    REJECTED;

    companion object {
        fun fromString(value: String?): ProRequestStatus {
            return when (value?.trim()?.uppercase()) {
                "PENDING" -> PENDING
                "APPROVED" -> APPROVED
                "REJECTED" -> REJECTED
                else -> PENDING // Safe fallback: unknown status is treated as PENDING/review, NEVER APPROVED
            }
        }
    }
}

/**
 * Pro Upgrade Request Model.
 * Canonical Document Path: /pro_requests/{requestId}
 */
@IgnoreExtraProperties
data class ProRequest(
    val requestId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val requestedPlan: String = "pro",
    val requestedDuration: String = "1 month",
    val paymentMethod: String? = null,
    val paymentProofUrl: String? = null,
    val userNote: String = "",
    val status: String = ProRequestStatus.PENDING.name,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val reviewedAt: Long? = null,
    val reviewedBy: String? = null,
    val reviewedByEmail: String? = null,
    val rejectionReason: String? = null,
    val adminNote: String? = null
) {
    val statusEnum: ProRequestStatus
        get() = ProRequestStatus.fromString(status)

    val isPending: Boolean
        get() = statusEnum == ProRequestStatus.PENDING

    val isApproved: Boolean
        get() = statusEnum == ProRequestStatus.APPROVED

    val isRejected: Boolean
        get() = statusEnum == ProRequestStatus.REJECTED

    val isReviewed: Boolean
        get() = isApproved || isRejected
}
