package com.tekinflow.backend.util

import com.tekinflow.backend.model.MaterialStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusTransitionValidatorTest {

    @Test
    fun `allows same status for idempotent retry`() {
        assertTrue(StatusTransitionValidator.isForwardTransition(MaterialStatus.IN_PROGRESS, MaterialStatus.IN_PROGRESS))
    }

    @Test
    fun `allows next forward status only`() {
        assertTrue(StatusTransitionValidator.isForwardTransition(MaterialStatus.PENDING, MaterialStatus.IN_PROGRESS))
        assertTrue(StatusTransitionValidator.isForwardTransition(MaterialStatus.IN_PROGRESS, MaterialStatus.ON_THE_WAY))
        assertTrue(StatusTransitionValidator.isForwardTransition(MaterialStatus.PURCHASED, MaterialStatus.ON_THE_WAY))
        assertTrue(StatusTransitionValidator.isForwardTransition(MaterialStatus.ON_THE_WAY, MaterialStatus.DELIVERED))
    }

    @Test
    fun `rejects skipped forward transitions`() {
        assertFalse(StatusTransitionValidator.isForwardTransition(MaterialStatus.PENDING, MaterialStatus.PURCHASED))
        assertFalse(StatusTransitionValidator.isForwardTransition(MaterialStatus.IN_PROGRESS, MaterialStatus.PURCHASED))
        assertFalse(StatusTransitionValidator.isForwardTransition(MaterialStatus.IN_PROGRESS, MaterialStatus.DELIVERED))
        assertFalse(StatusTransitionValidator.isForwardTransition(MaterialStatus.PURCHASED, MaterialStatus.DELIVERED))
    }

    @Test
    fun `rejects backward transitions`() {
        assertFalse(StatusTransitionValidator.isForwardTransition(MaterialStatus.DELIVERED, MaterialStatus.ON_THE_WAY))
        assertFalse(StatusTransitionValidator.isForwardTransition(MaterialStatus.ON_THE_WAY, MaterialStatus.PURCHASED))
        assertFalse(StatusTransitionValidator.isForwardTransition(MaterialStatus.PURCHASED, MaterialStatus.PENDING))
    }

    @Test
    fun `rejects transitions from cancelled`() {
        assertFalse(StatusTransitionValidator.isForwardTransition(MaterialStatus.CANCELLED, MaterialStatus.PENDING))
        assertFalse(StatusTransitionValidator.isForwardTransition(MaterialStatus.CANCELLED, MaterialStatus.DELIVERED))
    }

    @Test
    fun `allows active statuses to transition to cancelled`() {
        assertTrue(StatusTransitionValidator.isForwardTransition(MaterialStatus.PENDING, MaterialStatus.CANCELLED))
        assertTrue(StatusTransitionValidator.isForwardTransition(MaterialStatus.IN_PROGRESS, MaterialStatus.CANCELLED))
        assertTrue(StatusTransitionValidator.isForwardTransition(MaterialStatus.PURCHASED, MaterialStatus.CANCELLED))
        assertTrue(StatusTransitionValidator.isForwardTransition(MaterialStatus.ON_THE_WAY, MaterialStatus.CANCELLED))
    }

    @Test
    fun `rejects delivered to cancelled`() {
        assertFalse(StatusTransitionValidator.isForwardTransition(MaterialStatus.DELIVERED, MaterialStatus.CANCELLED))
    }
}
