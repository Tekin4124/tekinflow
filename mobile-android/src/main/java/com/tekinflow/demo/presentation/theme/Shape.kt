package com.tekinflow.demo.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(16.dp)
val ButtonShape = RoundedCornerShape(12.dp)
val BadgeShape = RoundedCornerShape(8.dp)
val InputShape = RoundedCornerShape(12.dp)
val DialogShape = RoundedCornerShape(24.dp)
val BottomSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

val AppShapes = Shapes(
    small = BadgeShape,
    medium = InputShape,
    large = CardShape,
    extraLarge = DialogShape
)

object AppSpacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s7 = 32.dp
}
