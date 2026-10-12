package com.tekinflow.demo.domain.model

enum class UrgencyLevel(val displayName: String) {
    LOW("Düşük"),
    NORMAL("Normal"),
    HIGH("Yüksek"),
    URGENT("Acil")
}

enum class MaterialStatus(val displayName: String) {
    PENDING("Beklemede"),
    IN_PROGRESS("Devam Ediyor"),
    PURCHASED("Devam Ediyor"),
    ON_THE_WAY("Yolda"),
    DELIVERED("Teslim Edildi"),
    CANCELLED("İptal Edildi")
}

enum class MaterialUnit(val displayName: String) {
    KG("KG"),
    CM("CM"),
    M("M"),
    MM("MM"),
    LITRE("Litre"),
    M3("M3"),
    ADET("Adet")
}

enum class UserRole(val displayName: String) {
    PURCHASING("Satın Alma"),
    PURCHASING_ADMIN("Satın Alma Yönetici"),
    LOGISTICS_OFFICE("Sevkiyat"),
    LOGISTICS_ADMIN("Sevkiyat Yönetici"),
    DRIVER("Şoför"),
    ADMIN("Yönetici")
}
