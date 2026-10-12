package com.tekinflow.demo.domain.model

data class User(
    val id: String,
    val username: String,
    val fullName: String,
    val role: UserRole,
    val licensePlate: String? = null,
    val isActive: Boolean = true,
    val isSuperAdmin: Boolean = false
) {
    fun initials(): String {
        val parts = fullName.trim().split(" ")
        return when {
            parts.size >= 2 -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
            parts.size == 1 && parts[0].isNotEmpty() -> parts[0].first().uppercaseChar().toString()
            else -> "?"
        }
    }
}
