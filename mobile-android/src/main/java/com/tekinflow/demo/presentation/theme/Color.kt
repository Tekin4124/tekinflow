package com.tekinflow.demo.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val PrimaryStart = Color(0xFF0B8B83)
val PrimaryEnd = Color(0xFF145E65)
val Primary = PrimaryStart
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFDDF5EF)
val OnPrimaryContainer = Color(0xFF134F48)

val Secondary = Color(0xFF06D6A0)
val SecondaryContainer = Color(0xFFD7FBF1)
val OnSecondary = Color(0xFFFFFFFF)
val OnSecondaryContainer = Color(0xFF064E3B)

val Background = Color(0xFFF8F9FE)
val Surface = Color(0xFFFFFFFF)
val SurfaceVariant = Color(0xFFF0F1F8)
val SurfaceMuted = Color(0xFFF6F7FC)
val OnBackground = Color(0xFF1A1D2E)
val OnSurface = Color(0xFF1A1D2E)
val OnSurfaceVariant = Color(0xFF6B7280)

val Error = Color(0xFFEF4444)
val ErrorContainer = Color(0xFFFEE2E2)
val OnError = Color(0xFFFFFFFF)

val Warning = Color(0xFFF59E0B)
val WarningContainer = Color(0xFFFEF3C7)
val Info = Color(0xFF3B82F6)
val InfoContainer = Color(0xFFDBEAFE)
val Success = Color(0xFF10B981)
val SuccessContainer = Color(0xFFD1FAE5)

val TextPrimary = Color(0xFF1A1D2E)
val TextSecondary = Color(0xFF6B7280)
val DividerColor = Color(0x0D000000)
val Outline = Color(0x1A1A1D2E)
val OutlineVariant = DividerColor

val DarkPrimary = Color(0xFF35D5C1)
val DarkOnPrimary = Color(0xFF052B27)
val DarkPrimaryContainer = Color(0xFF173D3C)
val DarkOnPrimaryContainer = Color(0xFFD4F7F1)
val DarkSecondary = Color(0xFF4DE0B7)
val DarkSecondaryContainer = Color(0xFF0F3A31)
val DarkOnSecondary = Color(0xFF06231B)
val DarkOnSecondaryContainer = Color(0xFFD8FFF2)
val DarkBackground = Color(0xFF0E1116)
val DarkSurface = Color(0xFF171C24)
val DarkSurfaceVariant = Color(0xFF202733)
val DarkSurfaceMuted = Color(0xFF202531)
val DarkOnBackground = Color(0xFFF3F4F8)
val DarkOnSurface = Color(0xFFF3F4F8)
val DarkOnSurfaceVariant = Color(0xFFA5ADBD)
val DarkError = Color(0xFFFF7B7B)
val DarkErrorContainer = Color(0xFF4A1F24)
val DarkOnError = Color(0xFF2B0A0E)
val DarkOutline = Color(0x33FFFFFF)
val DarkOutlineVariant = Color(0x1FFFFFFF)

val StatusPending = Color(0xFFF59E0B)
val StatusPendingContainer = Color(0xFFFEF3C7)
val StatusInProgress = Color(0xFF3B82F6)
val StatusInProgressContainer = Color(0xFFDBEAFE)
val StatusPurchased = Color(0xFF10B981)
val StatusPurchasedContainer = Color(0xFFD1FAE5)
val StatusOnTheWay = Color(0xFFF97316)
val StatusOnTheWayContainer = Color(0xFFFFF7ED)
val StatusDelivered = Color(0xFF8B5CF6)
val StatusDeliveredContainer = Color(0xFFEDE9FE)
val StatusCancelled = Color(0xFFEF4444)
val StatusCancelledContainer = Color(0xFFFEE2E2)

val UrgencyLow = Color(0xFF10B981)
val UrgencyNormal = Color(0xFF3B82F6)
val UrgencyHigh = Color(0xFFF59E0B)
val UrgencyUrgent = Color(0xFFEF4444)

val NewBadge = Color(0xFFEF4444)

fun primaryGradientBrush(): Brush = Brush.linearGradient(
    colors = listOf(PrimaryStart, PrimaryEnd)
)
