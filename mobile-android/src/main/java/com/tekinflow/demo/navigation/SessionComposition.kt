package com.tekinflow.demo.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import com.tekinflow.demo.data.session.SessionManager

val LocalSessionManager = staticCompositionLocalOf<SessionManager> {
    error("SessionManager sağlanmadı")
}
