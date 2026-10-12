# ============================================================
# ProGuard / R8 kuralları — Satınalma Yönetim Uygulaması
# ============================================================

# ─── Dagger Hilt ─────────────────────────────────────────────
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclassmembers class * {
    @dagger.hilt.* <fields>;
    @dagger.hilt.* <methods>;
    @javax.inject.* <fields>;
    @javax.inject.* <methods>;
}

# ─── Jetpack Compose ─────────────────────────────────────────
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ─── Jetpack Navigation Compose ──────────────────────────────
-keep class androidx.navigation.** { *; }
-keepnames class * extends androidx.navigation.Navigator

# ─── Kotlin Coroutines & StateFlow ───────────────────────────
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.flow.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ─── Domain Modelleri (JSON serileştirme için alan adlarını koru) ──
-keep class com.tekinflow.demo.domain.model.** { *; }
-keepclassmembers class com.tekinflow.demo.domain.model.** {
    <fields>;
    <init>(...);
}

# ─── Data class'ları (copy, toString, hashCode, equals) ──────
-keepclassmembers class * {
    public <methods>;
}

# ─── Enum sınıflarını koru ───────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── R8 tam mod uyarı bastırma ───────────────────────────────
-dontwarn java.lang.invoke.StringConcatFactory

# ─── AndroidX Security Crypto ────────────────────────────────
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ─── SQLCipher (net.zetetic) ─────────────────────────────────
# Native kod, JNI ile isimden erisilen alan/metotlara (orn. mNativeHandle,
# native metotlar) bagimli. Legacy artefakt kendi keep kurallarini getirmez;
# bu kural olmadan R8 release derlemesinde alanlari yeniden adlandirir ve
# uygulama acilista "NoSuchFieldError: mNativeHandle" ile cokerek baslar.
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keepclassmembers class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# ─── Genel Android kuralları ─────────────────────────────────
-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ─── Kotlin metadata (reflection) ───────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
