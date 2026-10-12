package com.tekinflow.demo.data.local

import com.tekinflow.demo.data.local.mapper.toEntity
import com.tekinflow.demo.testutil.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SnapshotWriteTest {
    @Test fun unchangedRequestsDoNotWriteEvenIfServerOrderDiffers() = runTest {
        val first = sampleRequest(id = "one").toEntity()
        val second = sampleRequest(id = "two").toEntity()
        val dao = FakePurchaseRequestDao(listOf(first, second))
        repeat(10) { dao.replaceAll(listOf(second, first)) }
        assertEquals(0, dao.clearCalls)
        assertEquals(2, dao.getAllRequestsNow().size)
    }
    @Test fun changedAndDeletedRequestsAreStillPersisted() = runTest {
        val request = sampleRequest(id = "one").toEntity()
        val dao = FakePurchaseRequestDao(listOf(request))
        dao.replaceAll(listOf(request.copy(notes = "Güncel not")))
        assertEquals("Güncel not", dao.getAllRequestsNow().single().notes)
        assertEquals(1, dao.clearCalls)
        dao.replaceAll(emptyList())
        assertTrue(dao.getAllRequestsNow().isEmpty())
        assertEquals(2, dao.clearCalls)
    }
    @Test fun notificationReadChangesAreNotLostByTheWriteGuard() = runTest {
        val original = sampleNotification().toEntity()
        val dao = FakeNotificationDao(listOf(original))
        dao.replaceAll(listOf(original))
        assertEquals(0, dao.clearCalls)
        dao.replaceAll(listOf(original.copy(isRead = true)))
        assertTrue(dao.getAllNow().single().isRead)
        assertEquals(1, dao.clearCalls)
    }
    @Test fun driverChangesPersistButRepeatedSnapshotsDoNotWrite() = runTest {
        val user = sampleUser().toEntity()
        val dao = FakeUserDao(listOf(user))
        repeat(10) { dao.replaceAll(listOf(user)) }
        assertEquals(0, dao.clearCalls)
        dao.replaceAll(listOf(user.copy(fullName = "Yeni ad")))
        assertEquals("Yeni ad", dao.getAllNow().single().fullName)
        assertEquals(1, dao.clearCalls)
    }
}
