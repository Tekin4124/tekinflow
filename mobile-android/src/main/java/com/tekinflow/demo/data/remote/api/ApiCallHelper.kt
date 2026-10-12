package com.tekinflow.demo.data.remote.api

import org.json.JSONObject
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class RemoteDataException(
    override val message: String
) : IOException(message)

sealed interface ApiCallResult<out T> {
    data class Success<T>(val body: T) : ApiCallResult<T>
    data class HttpError(val code: Int, val message: String) : ApiCallResult<Nothing>
    data class NetworkError(val message: String) : ApiCallResult<Nothing>
}

suspend fun <T> executeApi(
    unauthorizedMessage: String = "Oturum dogrulanamadi",
    defaultMessage: String = "Islem su anda tamamlanamiyor",
    block: suspend () -> Response<T>
): T {
    return when (
        val result = executeApiResult(
            unauthorizedMessage = unauthorizedMessage,
            defaultMessage = defaultMessage,
            block = block
        )
    ) {
        is ApiCallResult.Success -> result.body
        is ApiCallResult.HttpError -> throw RemoteDataException(result.message)
        is ApiCallResult.NetworkError -> throw RemoteDataException(result.message)
    }
}

suspend fun <T> executeApiResult(
    unauthorizedMessage: String = "Oturum dogrulanamadi",
    defaultMessage: String = "Islem su anda tamamlanamiyor",
    block: suspend () -> Response<T>
): ApiCallResult<T> {
    return try {
        val response = block()
        if (response.isSuccessful) {
            response.body()?.let { ApiCallResult.Success(it) }
                ?: ApiCallResult.NetworkError(defaultMessage)
        } else {
            ApiCallResult.HttpError(
                code = response.code(),
                message = response.toUserMessage(
                    unauthorizedMessage = unauthorizedMessage,
                    defaultMessage = defaultMessage
                )
            )
        }
    } catch (exception: UnknownHostException) {
        ApiCallResult.NetworkError("Sunucu adresi çözümlenemiyor")
    } catch (exception: ConnectException) {
        ApiCallResult.NetworkError("Sunucuya bağlanılamıyor")
    } catch (exception: SocketTimeoutException) {
        ApiCallResult.NetworkError("Sunucu yanıt vermedi")
    } catch (exception: SSLHandshakeException) {
        ApiCallResult.NetworkError("Sunucu sertifikası doğrulanamadı")
    } catch (exception: SSLPeerUnverifiedException) {
        ApiCallResult.NetworkError("Sunucu kimliği doğrulanamadı")
    } catch (exception: SSLException) {
        ApiCallResult.NetworkError("Güvenli bağlantı kurulamıyor")
    } catch (exception: RemoteDataException) {
        ApiCallResult.NetworkError(exception.message)
    } catch (exception: IOException) {
        ApiCallResult.NetworkError("Ağ bağlantısında sorun oluştu")
    }
}

private fun <T> Response<T>.toUserMessage(
    unauthorizedMessage: String,
    defaultMessage: String
): String {
    val backendError = errorBody()?.string().orEmpty().extractBackendError()
    backendError?.code?.toUserMessage()?.let { return it }
    return when (code()) {
        400 -> backendError?.message ?: "Istek dogrulanamadi"
        401 -> unauthorizedMessage
        403 -> "Yetkisiz islem"
        404 -> backendError?.message ?: "Kayıt bulunamadı"
        409 -> backendError?.message ?: "Islem mevcut durumla cakisiyor"
        500, 502, 503, 504 -> "Sunucu şu anda yanıt vermiyor"
        else -> backendError?.message ?: defaultMessage
    }
}

private data class BackendError(
    val message: String?,
    val code: String?
)

private fun String.extractBackendError(): BackendError? {
    if (isBlank()) return null
    return runCatching {
        val json = JSONObject(this)
        BackendError(
            message = json.optString("error").takeIf { it.isNotBlank() },
            code = json.optString("code").takeIf { it.isNotBlank() }
        )
    }.getOrNull()
}

private fun String.toUserMessage(): String? {
    return when (this) {
        "AUTHENTICATION_FAILED" -> "Oturum dogrulanamadi"
        "FORBIDDEN" -> "Yetkisiz islem"
        "NOT_FOUND" -> "Kayıt bulunamadı"
        "CONFLICT" -> "Islem mevcut durumla cakisiyor"
        "IDEMPOTENCY_REPLAY" -> "Bu islem zaten gonderildi"
        "RATE_LIMITED" -> "Çok fazla istek gönderildi. Lütfen biraz bekleyin"
        "PAYLOAD_TOO_LARGE" -> "Istek boyutu cok buyuk"
        "SERVER_ERROR" -> "Sunucu şu anda yanıt vermiyor"
        else -> null
    }
}
