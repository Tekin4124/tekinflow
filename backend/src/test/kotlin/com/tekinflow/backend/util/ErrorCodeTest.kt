package com.tekinflow.backend.util

import com.tekinflow.backend.model.ErrorResponse
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorCodeTest {

    @Test
    fun `maps common http statuses to stable error codes`() {
        assertEquals("VALIDATION_ERROR", HttpStatusCode.BadRequest.defaultErrorCode())
        assertEquals("AUTHENTICATION_FAILED", HttpStatusCode.Unauthorized.defaultErrorCode())
        assertEquals("FORBIDDEN", HttpStatusCode.Forbidden.defaultErrorCode())
        assertEquals("NOT_FOUND", HttpStatusCode.NotFound.defaultErrorCode())
        assertEquals("CONFLICT", HttpStatusCode.Conflict.defaultErrorCode())
        assertEquals("RATE_LIMITED", HttpStatusCode.TooManyRequests.defaultErrorCode())
        assertEquals("PAYLOAD_TOO_LARGE", HttpStatusCode.PayloadTooLarge.defaultErrorCode())
    }

    @Test
    fun `api exceptions expose ui safe error codes`() {
        assertEquals("VALIDATION_ERROR", ValidationException().errorCode)
        assertEquals("AUTHENTICATION_FAILED", AuthenticationFailureException().errorCode)
        assertEquals("FORBIDDEN", AuthorizationFailureException().errorCode)
        assertEquals("NOT_FOUND", NotFoundException().errorCode)
        assertEquals("CONFLICT", ConflictException().errorCode)
    }

    @Test
    fun `error response defaults to generic code`() {
        assertEquals("GENERIC_ERROR", ErrorResponse("Sunucu hatası").code)
    }
}
