/*
 * Extensions.kt
 * Kotlin extension fonksiyonları — tarih biçimlendirme, string işlemleri ve UI yardımcıları.
 */
package com.tekinflow.demo.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unix zaman damgasını Türkçe tarih-saat formatına çevirir (gg.aa.yyyy ss:dd)
 */
fun Long.toFormattedDateTime(): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
    return sdf.format(Date(this))
}

/**
 * Unix zaman damgasını Türkçe tarih formatına çevirir (gg.aa.yyyy)
 */
fun Long.toFormattedDate(): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
    return sdf.format(Date(this))
}

fun Long.toFormattedTime(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
    return sdf.format(Date(this))
}

/**
 * Boş veya null string için varsayılan değer döner
 */
fun String?.orDefault(default: String = "-"): String {
    return if (this.isNullOrBlank()) default else this
}

/**
 * String'in ilk N karakterini büyük harfle başlatır
 */
fun String.capitalize(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("tr", "TR")) else it.toString() }
}
