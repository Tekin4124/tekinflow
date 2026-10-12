package com.tekinflow.backend.util

import com.tekinflow.backend.model.MaterialStatus

object StatusTransitionValidator {
    private val order = listOf(
        MaterialStatus.PENDING,
        MaterialStatus.IN_PROGRESS,
        MaterialStatus.ON_THE_WAY,
        MaterialStatus.DELIVERED
    )

    fun isForwardTransition(currentStatus: MaterialStatus, newStatus: MaterialStatus): Boolean {
        if (currentStatus == newStatus) return true
        if (newStatus == MaterialStatus.CANCELLED) return currentStatus != MaterialStatus.DELIVERED
        if (currentStatus == MaterialStatus.PURCHASED && newStatus == MaterialStatus.ON_THE_WAY) return true
        val currentIndex = order.indexOf(currentStatus)
        val newIndex = order.indexOf(newStatus)
        return currentIndex >= 0 && newIndex == currentIndex + 1
    }
}
