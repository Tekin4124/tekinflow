package com.tekinflow.demo.data.demo

import com.tekinflow.demo.data.remote.api.*
import com.tekinflow.demo.data.remote.dto.*
import com.tekinflow.demo.domain.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Call
import retrofit2.Response
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Implements the existing contracts entirely in memory. No sockets or cloud SDKs. */
@Singleton
class LocalDemoApi @Inject constructor() : AuthApi, RequestApi, UserApi, NotificationApi {
    private val mutex = Mutex()
    private val users = mutableListOf(
        UserDto("11111111-1111-4111-8111-111111111111", "purchasing", "Satın Alma Kullanıcısı", UserRole.PURCHASING),
        UserDto("22222222-2222-4222-8222-222222222222", "logistics_office", "Sevkiyat Kullanıcısı", UserRole.LOGISTICS_OFFICE),
        UserDto("33333333-3333-4333-8333-333333333333", "driver", "Şoför Kullanıcısı", UserRole.DRIVER, "34 TF 101"),
        UserDto("44444444-4444-4444-8444-444444444444", "admin", "Yönetici", UserRole.ADMIN, isSuperAdmin=true)
    )
    private val requests = mutableListOf<PurchaseRequestDto>()
    private val notifications = mutableListOf<NotificationDto>()
    private data class Replay(val operation:String,val body:String,val response:Any)
    private val replay = mutableMapOf<String, Replay>()
    private var sequence = 100

    init {
        fun sample(label: String, company: String, status: MaterialStatus, assigned: Boolean = false): PurchaseRequestDto {
            val now = System.currentTimeMillis()
            return PurchaseRequestDto(UUID.randomUUID().toString(), "TLP-2026-${sequence++}", label,
                items=listOf(PurchaseRequestItemDto(itemName=label,quantity="4",unit=MaterialUnit.ADET)),
                companyName=company,companyAddress="Örnek adres",contactNumber="",contactPersonName="Örnek İletişim",
                productDimensions="Örnek paket",productWeight="10 kg",urgencyLevel=UrgencyLevel.NORMAL,purchaseDate="2026-10-12",notes="Örnek kayıt",
                licensePlate=if(assigned) "34 TF 101" else "",materialStatus=status,
                assignedDriverId=if(assigned) users[2].id else null,assignedPurchasingStaffId=users[0].id,assignedPurchasingStaffName=users[0].fullName,
                createdAt=now,updatedAt=now)
        }
        requests += sample("Bağlantı elemanı", "Örnek Tedarikçi A", MaterialStatus.IN_PROGRESS)
        requests += sample("Paketleme malzemesi", "Örnek Tedarikçi B", MaterialStatus.IN_PROGRESS, true)
        requests += sample("Elektrik ekipmanı", "Örnek Tedarikçi C", MaterialStatus.ON_THE_WAY, true)
        requests += sample("Ofis sarf malzemesi", "Örnek Tedarikçi D", MaterialStatus.DELIVERED, true).copy(deliveredTo="Örnek Alıcı",actualPurchaseDate=System.currentTimeMillis())
    }
    private class Failure(val code:Int, override val message:String):RuntimeException(message)
    private fun fail(code:Int,message:String):Nothing = throw Failure(code,message)
    private suspend fun <T> local(block:()->T):Response<T> = mutex.withLock {
        try { Response.success(block()) } catch(e:Failure) {
            val message = e.message.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ")
            Response.error(e.code,"{\"error\":\"$message\"}".toResponseBody("application/json".toMediaType()))
        }
    }
    private fun actor(auth:String?):UserDto {
        val username=auth?.removePrefix("Bearer ")?.removePrefix("local-demo-")
        return users.find { it.username==username && it.isActive } ?: fail(401,"Oturumunu yeniden aç")
    }
    private fun allow(user:UserDto,vararg roles:UserRole) { if(!user.isSuperAdmin && user.role !in roles) fail(403,"Bu rol için işlem yetkisi yok") }
    private fun index(id:String)=requests.indexOfFirst { it.id==id }.also { if(it<0) fail(404,"Talep bulunamadı") }
    private fun update(id:String,operation:(PurchaseRequestDto)->PurchaseRequestDto):PurchaseRequestDto {
        val i=index(id);val updated=operation(requests[i]).copy(updatedAt=System.currentTimeMillis());requests[i]=updated
        users.forEach { notifications += NotificationDto(UUID.randomUUID().toString(),"${updated.requestNumber} güncellendi",System.currentTimeMillis(),false,it.id,id) }
        return updated
    }
    private fun editable(user:UserDto,request:PurchaseRequestDto) { allow(user,UserRole.PURCHASING,UserRole.PURCHASING_ADMIN);if(!user.isSuperAdmin && user.role==UserRole.PURCHASING && request.assignedPurchasingStaffId!=user.id) fail(403,"Yalnız kendi talebini düzenleyebilirsin") }
    private fun view(user:UserDto,r:PurchaseRequestDto):PurchaseRequestDto {
        if(user.role!=UserRole.DRIVER) return r
        return r.copy(companyAddress=if(r.assignedDriverId==user.id) r.companyAddress else "",contactNumber=if(r.assignedDriverId==user.id) r.contactNumber else "",contactPersonName=if(r.assignedDriverId==user.id) r.contactPersonName else null,spotDriverIdentityNumber=null,spotDriverPhone=null,assignedPurchasingStaffId="",assignedPurchasingStaffName=null,seenByUserIds=emptySet())
    }
    private fun visible(user:UserDto,r:PurchaseRequestDto)=user.role!=UserRole.DRIVER || r.assignedDriverId==user.id || (r.assignedDriverId==null && r.licensePlate.isBlank() && r.materialStatus in listOf(MaterialStatus.PENDING,MaterialStatus.IN_PROGRESS,MaterialStatus.PURCHASED))
    @Suppress("UNCHECKED_CAST")
    private fun <T:Any> once(user:UserDto,key:String?,operation:String,body:Any?,block:()->T):T {
        if(key.isNullOrBlank()) return block()
        val scope="${user.id}|$key"
        replay[scope]?.let {
            if(it.operation!=operation||it.body!=body.toString())fail(409,"Anahtar farklı bir işleme ait")
            return it.response as T
        }
        return block().also { replay[scope]=Replay(operation,body.toString(),it) }
    }
    override suspend fun login(request:LoginRequestDto)=local {
        val user=users.find { it.username==request.username.lowercase() && it.isActive } ?: fail(401,"Örnek rol bulunamadı")
        LoginResponseDto("local-demo-${user.username}","local-refresh-${user.username}",user)
    }
    override suspend fun refresh(request:RefreshRequestDto)=local {
        val username=request.refreshToken.removePrefix("local-refresh-");if(users.none {it.username==username&&it.isActive})fail(401,"Oturum yok")
        TokenResponseDto("local-demo-$username","local-refresh-$username")
    }
    override fun refreshBlocking(request:RefreshRequestDto):Call<TokenResponseDto> = throw UnsupportedOperationException("Local demo never opens HTTP connections")
    override suspend fun logout(request:LogoutRequestDto)=local { SuccessResponseDto(true) }
    override suspend fun getRequests(status:String?,assignedDriverId:String?,staffId:String?,authorization:String?)=local {
        val user=actor(authorization);requests.filter { visible(user,it) && (status==null||it.materialStatus.name==status) && (assignedDriverId==null||it.assignedDriverId==assignedDriverId) && (staffId==null||it.assignedPurchasingStaffId==staffId) }.map { view(user,it) }
    }
    override suspend fun getRequestById(requestId:String,authorization:String?)=local {
        val user=actor(authorization);val r=requests[index(requestId)];if(!visible(user,r))fail(403,"Talep bu role açık değil");view(user,r)
    }
    override suspend fun getDeliveredRecipients(authorization:String?)=local { actor(authorization);requests.mapNotNull {it.deliveredTo}.distinct() }
    override suspend fun createRequest(request:CreatePurchaseRequestDto,authorization:String?,idempotencyKey:String?)=local {
        val user=actor(authorization);allow(user,UserRole.PURCHASING,UserRole.PURCHASING_ADMIN)
        once(user,idempotencyKey,"create",request) {
            if(request.companyName.isBlank()||request.items.any {it.itemName.isBlank()|| (it.quantity.replace(',','.').toDoubleOrNull()?:0.0)<=0})fail(400,"Firma, malzeme ve miktarı kontrol et")
            val now=System.currentTimeMillis()
            PurchaseRequestDto(UUID.randomUUID().toString(),"TLP-2026-${sequence++}",request.items.firstOrNull()?.itemName ?: request.itemToPurchase,request.items,request.companyName,request.companyAddress.orEmpty(),request.contactNumber.orEmpty(),request.contactPersonName,request.productDimensions.orEmpty(),request.productWeight.orEmpty(),request.packageCount,request.urgencyLevel,request.purchaseDate.orEmpty(),notes=request.notes.orEmpty(),licensePlate="",materialStatus=MaterialStatus.IN_PROGRESS,assignedPurchasingStaffId=user.id,assignedPurchasingStaffName=user.fullName,createdAt=now,updatedAt=now).also { requests.add(0,it) }
        }
    }
    override suspend fun updateRequest(requestId:String,request:UpdatePurchaseRequestDto,authorization:String?,idempotencyKey:String?)=local {
        val user=actor(authorization);editable(user,requests[index(requestId)])
        once(user,idempotencyKey,"edit/$requestId",request){update(requestId){it.copy(itemToPurchase=request.items.firstOrNull()?.itemName?:request.itemToPurchase,items=request.items,companyName=request.companyName,companyAddress=request.companyAddress.orEmpty(),contactNumber=request.contactNumber.orEmpty(),contactPersonName=request.contactPersonName,productDimensions=request.productDimensions.orEmpty(),productWeight=request.productWeight.orEmpty(),packageCount=request.packageCount,urgencyLevel=request.urgencyLevel,purchaseDate=request.purchaseDate.orEmpty(),notes=request.notes.orEmpty())}}
    }
    override suspend fun assignDriver(requestId:String,request:AssignDriverRequestDto,authorization:String?,idempotencyKey:String?)=local {
        val user=actor(authorization);allow(user,UserRole.LOGISTICS_OFFICE,UserRole.LOGISTICS_ADMIN)
        once(user,idempotencyKey,"assign/$requestId",request){update(requestId){r->
            if(r.materialStatus in listOf(MaterialStatus.DELIVERED,MaterialStatus.CANCELLED))fail(409,"Tamamlanan talebe atama yapılamaz")
            val driver=users.find {it.id==request.driverId&&it.role==UserRole.DRIVER&&it.isActive}
            if(request.driverId!=null&&driver==null)fail(400,"Şoför bulunamadı")
            val plate=request.licensePlate?.ifBlank {null}?:driver?.licensePlate
            if(plate.isNullOrBlank())fail(400,"Plaka gerekli")
            if(driver==null&&(request.spotDriverName.isNullOrBlank()||request.spotDriverPhone.isNullOrBlank()||request.spotDriverIdentityNumber.isNullOrBlank()))fail(400,"Spot araç bilgilerini tamamla")
            r.copy(assignedDriverId=driver?.id,licensePlate=plate,spotDriverName=if(driver==null)request.spotDriverName else null,spotDriverCompanyName=if(driver==null)request.spotDriverCompanyName else null,spotDriverIdentityNumber=if(driver==null)request.spotDriverIdentityNumber else null,spotDriverPhone=if(driver==null)request.spotDriverPhone else null)
        }}
    }
    override suspend fun unassignDriver(requestId:String,authorization:String?,idempotencyKey:String?)=local {
        val user=actor(authorization);allow(user,UserRole.LOGISTICS_OFFICE,UserRole.LOGISTICS_ADMIN)
        once(user,idempotencyKey,"unassign/$requestId",null){update(requestId){it.copy(assignedDriverId=null,licensePlate="",spotDriverName=null,spotDriverPhone=null,spotDriverIdentityNumber=null,spotDriverCompanyName=null,materialStatus=MaterialStatus.IN_PROGRESS)}}
    }
    override suspend fun takeOverRequest(requestId:String,authorization:String?,idempotencyKey:String?)=local {
        val user=actor(authorization);allow(user,UserRole.DRIVER)
        once(user,idempotencyKey,"take/$requestId",null){update(requestId){
            if(!visible(user,it)||it.materialStatus in listOf(MaterialStatus.DELIVERED,MaterialStatus.CANCELLED))fail(409,"Talep üstlenilemez")
            it.copy(assignedDriverId=user.id,licensePlate=user.licensePlate.orEmpty(),materialStatus=MaterialStatus.IN_PROGRESS,hasObjection=false)
        }}
    }
    override suspend fun updateStatus(requestId:String,request:UpdateStatusRequestDto,authorization:String?,idempotencyKey:String?)=local {
        val user=actor(authorization);allow(user,UserRole.DRIVER,UserRole.LOGISTICS_OFFICE,UserRole.LOGISTICS_ADMIN)
        once(user,idempotencyKey,"status/$requestId",request){update(requestId){
            if(user.role==UserRole.DRIVER&&it.assignedDriverId!=user.id)fail(403,"Bu talep sana atanmadı")
            val next=when(it.materialStatus){MaterialStatus.IN_PROGRESS,MaterialStatus.PURCHASED->MaterialStatus.ON_THE_WAY;MaterialStatus.ON_THE_WAY->MaterialStatus.DELIVERED;else->it.materialStatus}
            if(request.status!=it.materialStatus&&request.status!=next)fail(409,"Durum sırası geçersiz")
            if(it.licensePlate.isBlank())fail(409,"Önce şoför veya spot araç ata")
            if(request.status==MaterialStatus.DELIVERED&&request.deliveredTo.isNullOrBlank())fail(400,"Teslim alan kişi gerekli")
            it.copy(materialStatus=request.status,deliveredTo=request.deliveredTo?:it.deliveredTo,actualPurchaseDate=if(request.status==MaterialStatus.DELIVERED)System.currentTimeMillis() else it.actualPurchaseDate)
        }}
    }
    override suspend fun cancelRequest(requestId:String,request:CancelRequestDto,authorization:String?,idempotencyKey:String?)=local {
        val user=actor(authorization);editable(user,requests[index(requestId)]);once(user,idempotencyKey,"cancel/$requestId",request){update(requestId){it.copy(materialStatus=MaterialStatus.CANCELLED,cancellationReason=request.reason,cancelledBy=user.id)}}
    }
    override suspend fun submitObjection(requestId:String,request:ObjectionRequestDto,authorization:String?,idempotencyKey:String?)=local {
        val user=actor(authorization);allow(user,UserRole.DRIVER,UserRole.LOGISTICS_OFFICE,UserRole.LOGISTICS_ADMIN)
        once(user,idempotencyKey,"object/$requestId",request){update(requestId){if(user.role==UserRole.DRIVER&&it.assignedDriverId!=user.id)fail(403,"Bu talep sana atanmadı");if(request.reason.isBlank())fail(400,"İtiraz nedeni gerekli");it.copy(hasObjection=true,objectionReason=request.reason,objectionBy=user.id)}}
    }
    override suspend fun markSeen(requestId:String,authorization:String?,idempotencyKey:String?)=local {val user=actor(authorization);val i=index(requestId);requests[i]=requests[i].copy(seenByUserIds=requests[i].seenByUserIds+user.id);SuccessResponseDto(true)}
    override suspend fun getUsers(authorization:String?)=local {allow(actor(authorization),UserRole.ADMIN);users.toList()}
    override suspend fun getDrivers(authorization:String?)=local {actor(authorization);users.filter {it.role==UserRole.DRIVER&&it.isActive}}
    override suspend fun updateFcmToken(request:FcmTokenRequestDto,authorization:String?)=local {SuccessResponseDto(true)}
    override suspend fun changePassword(request:ChangePasswordRequestDto,authorization:String?):Response<SuccessResponseDto> = local {actor(authorization);fail(400,"Bu sürüm yerel örnek hesaplarla çalışır")}
    override suspend fun createUser(request:CreateUserRequestDto,authorization:String?)=local {
        allow(actor(authorization),UserRole.ADMIN);if(users.any {it.username==request.username})fail(409,"Kullanıcı adı mevcut")
        UserDto(UUID.randomUUID().toString(),request.username,request.fullName,request.role,request.licensePlate).also {users.add(it)}
    }
    override suspend fun updateUser(userId:String,request:UpdateUserRequestDto,authorization:String?)=local {
        allow(actor(authorization),UserRole.ADMIN);val i=users.indexOfFirst {it.id==userId};if(i<0)fail(404,"Kullanıcı bulunamadı")
        users[i].copy(username=request.username?:users[i].username,fullName=request.fullName?:users[i].fullName,role=request.role?:users[i].role,licensePlate=request.licensePlate?:users[i].licensePlate).also {users[i]=it}
    }
    override suspend fun deleteUser(userId:String,authorization:String?)=local {allow(actor(authorization),UserRole.ADMIN);val i=users.indexOfFirst {it.id==userId};if(i<0)fail(404,"Kullanıcı yok");if(users[i].isSuperAdmin)fail(409,"Yönetici hesabı korunur");users[i]=users[i].copy(isActive=false);SuccessResponseDto(true)}
    override suspend fun activateUser(userId:String,authorization:String?)=local {allow(actor(authorization),UserRole.ADMIN);val i=users.indexOfFirst {it.id==userId};if(i<0)fail(404,"Kullanıcı yok");users[i].copy(isActive=true).also {users[i]=it}}
    override suspend fun deleteUserPermanently(userId:String,authorization:String?)=local {allow(actor(authorization),UserRole.ADMIN);if(users.find{it.id==userId}?.isSuperAdmin==true)fail(409,"Yönetici hesabı korunur");users.removeAll{it.id==userId};SuccessResponseDto(true)}
    override suspend fun getNotifications(authorization:String?)=local {val user=actor(authorization);notifications.filter {it.targetUserId==user.id}}
    override suspend fun markAsRead(notificationId:String,authorization:String?)=local {val user=actor(authorization);val i=notifications.indexOfFirst{it.id==notificationId&&it.targetUserId==user.id};if(i>=0)notifications[i]=notifications[i].copy(isRead=true);SuccessResponseDto(true)}
}
