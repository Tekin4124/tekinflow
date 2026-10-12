/*
 * Constants.kt
 * Uygulama genelinde kullanılan sabit değerleri içerir.
 * Navigation route'ları ve UI sabitleri tanımlanır.
 */
package com.tekinflow.demo.util

/**
 * Uygulama sabitleri
 */
object Constants {

    /** Navigasyon rotaları */
    object Routes {
        const val LOGIN = "login"
        const val ADMIN = "admin"
        const val PURCHASING = "purchasing"
        const val PURCHASING_DETAIL = "purchasing_detail/{requestId}"
        const val LOGISTICS = "logistics"
        const val LOGISTICS_DETAIL = "logistics_detail/{requestId}"
        const val DRIVER = "driver"
        const val DRIVER_DETAIL = "driver_detail/{requestId}"

        fun purchasingDetail(requestId: String) = "purchasing_detail/$requestId"
        fun logisticsDetail(requestId: String) = "logistics_detail/$requestId"
        fun driverDetail(requestId: String) = "driver_detail/$requestId"
    }

    /** Navigation argüman anahtarları */
    object NavArgs {
        const val REQUEST_ID = "requestId"
    }

    /** Yeni talep oluşturulduğunda kullanılan yer tutucu ID */
    const val NEW_REQUEST_ID = "new"

    /** Talep numarası öneki */
    const val REQUEST_NUMBER_PREFIX = "TLP-"
}
