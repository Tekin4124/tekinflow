package com.tekinflow.demo.data.demo
import com.tekinflow.demo.data.remote.dto.*
import com.tekinflow.demo.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LocalDemoApiTest {
    private suspend fun auth(api:LocalDemoApi,role:String)= "Bearer " + api.login(LoginRequestDto(role,"demo")).body()!!.accessToken
    private fun payload(company:String="Örnek Firma")=CreatePurchaseRequestDto("Test ürün",listOf(PurchaseRequestItemDto(itemName="Test ürün",quantity="2",unit=MaterialUnit.ADET)),company)
    @Test fun purchaseAssignmentAndDeliveryAcrossRoles()=runTest {
        val api=LocalDemoApi();val purchasing=auth(api,"purchasing")
        val r=api.createRequest(payload(),purchasing,"create").body()!!
        val logistics=auth(api,"logistics_office");val driver=api.getDrivers(logistics).body()!!.first()
        assertTrue(api.assignDriver(r.id,AssignDriverRequestDto(driver.id,driver.licensePlate),logistics,"assign").isSuccessful)
        val driverAuth=auth(api,"driver")
        assertTrue(api.updateStatus(r.id,UpdateStatusRequestDto(MaterialStatus.ON_THE_WAY),driverAuth,"road").isSuccessful)
        val delivered=api.updateStatus(r.id,UpdateStatusRequestDto(MaterialStatus.DELIVERED,"Demo Alıcı"),driverAuth,"delivery").body()!!
        assertEquals(MaterialStatus.DELIVERED,delivered.materialStatus);assertEquals("Demo Alıcı",delivered.deliveredTo)
    }
    @Test fun retryReturnsTheSameSuccessfulMutation()=runTest {
        val api=LocalDemoApi();val auth=auth(api,"purchasing")
        val first=api.createRequest(payload(),auth,"same-key").body()!!
        val second=api.createRequest(payload(),auth,"same-key").body()!!
        assertEquals(first.id,second.id);assertEquals(5,api.getRequests(null,null,null,auth).body()!!.size)
    }
    @Test fun aSuccessfulKeyCannotBeReusedWithDifferentData()=runTest {
        val api=LocalDemoApi();val auth=auth(api,"purchasing")
        assertTrue(api.createRequest(payload(),auth,"key").isSuccessful)
        assertEquals(409,api.createRequest(payload("Başka Firma"),auth,"key").code())
    }
    @Test fun failureDoesNotConsumeRetryKey()=runTest {
        val api=LocalDemoApi();val auth=auth(api,"purchasing")
        assertEquals(400,api.createRequest(payload(""),auth,"retry").code())
        assertTrue(api.createRequest(payload(),auth,"retry").isSuccessful)
    }
    @Test fun driverCannotCreateOrEditAnothersRequest()=runTest {
        val api=LocalDemoApi();val auth=auth(api,"driver")
        assertEquals(403,api.createRequest(payload(),auth,"create").code())
        val pool=api.getRequests(null,null,null,auth).body()!!.first{it.assignedDriverId==null}
        assertEquals("",pool.companyAddress)
        assertTrue(api.takeOverRequest(pool.id,auth,"take").isSuccessful)
        assertEquals("Şoför Kullanıcısı",api.getDrivers(auth).body()!!.first().fullName)
    }
    @Test fun deliveryRequiresRecipientAndRoleChangesKeepData()=runTest {
        val api=LocalDemoApi();val driver=auth(api,"driver");val job=api.getRequests(null,null,null,driver).body()!!.first{it.materialStatus==MaterialStatus.ON_THE_WAY}
        assertEquals(400,api.updateStatus(job.id,UpdateStatusRequestDto(MaterialStatus.DELIVERED),driver,"delivery").code())
        assertTrue(api.updateStatus(job.id,UpdateStatusRequestDto(MaterialStatus.DELIVERED,"Demo Alıcı"),driver,"delivery").isSuccessful)
        val logistics=auth(api,"logistics_office");assertEquals(MaterialStatus.DELIVERED,api.getRequestById(job.id,logistics).body()!!.materialStatus)
    }
}
