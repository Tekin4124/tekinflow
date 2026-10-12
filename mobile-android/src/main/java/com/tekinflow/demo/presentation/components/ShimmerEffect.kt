package com.tekinflow.demo.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tekinflow.demo.presentation.theme.AppSpacing
import com.tekinflow.demo.presentation.theme.CardShape
import com.tekinflow.demo.presentation.theme.SatinalmaTheme

@Composable
fun ShimmerRequestList(
    count: Int = 3,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.s3)
    ) {
        repeat(count) {
            ShimmerRequestCardPlaceholder()
        }
    }
}

@Composable
fun ShimmerRequestCardPlaceholder(modifier: Modifier = Modifier) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val surfaceMuted = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val translate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = listOf(surfaceVariant, surfaceMuted, surfaceVariant),
        start = Offset(translate - 300f, 0f),
        end = Offset(translate, 300f)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(brush, CardShape)
            .padding(AppSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.s3)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2)) {
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 28.dp)
                    .background(surfaceMuted, RoundedCornerShape(20.dp))
            )
            Box(
                modifier = Modifier
                    .size(width = 90.dp, height = 28.dp)
                    .background(surfaceMuted, RoundedCornerShape(20.dp))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(18.dp)
                .background(surfaceMuted, RoundedCornerShape(10.dp))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(14.dp)
                .background(surfaceMuted, RoundedCornerShape(10.dp))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2)) {
            Box(
                modifier = Modifier
                    .size(width = 82.dp, height = 32.dp)
                    .background(surfaceMuted, RoundedCornerShape(12.dp))
            )
            Box(
                modifier = Modifier
                    .size(width = 104.dp, height = 32.dp)
                    .background(surfaceMuted, RoundedCornerShape(12.dp))
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun ShimmerRequestCardPreview() {
    SatinalmaTheme {
        ShimmerRequestList(modifier = Modifier.padding(16.dp))
    }
}
