/*
 * UiState.kt
 * Genel amaçlı UI durum sealed class'ı — ViewModel'lerde tutarlı durum yönetimi sağlar.
 * Idle, Loading, Success ve Error olmak üzere 4 durum içerir.
 */
package com.tekinflow.demo.util

/**
 * ViewModel → UI arası durum aktarımı için kullanılan sealed class.
 *
 * Kullanım örneği:
 * ```kotlin
 * val saveState: StateFlow<UiState<PurchaseRequest>> = _saveState.asStateFlow()
 *
 * when (val state = saveState.value) {
 *     is UiState.Idle -> { /* bekleme */ }
 *     is UiState.Loading -> { /* yükleniyor göster */ }
 *     is UiState.Success -> { /* state.data ile çalış */ }
 *     is UiState.Error -> { /* state.message göster */ }
 * }
 * ```
 */
sealed class UiState<out T> {
    /** Henüz işlem başlatılmadı */
    data object Idle : UiState<Nothing>()

    /** İşlem devam ediyor */
    data object Loading : UiState<Nothing>()

    /** İşlem başarılı — sonuç verisi içerir */
    data class Success<T>(val data: T) : UiState<T>()

    /** İşlem başarısız — hata mesajı içerir */
    data class Error(val message: String) : UiState<Nothing>()
}
