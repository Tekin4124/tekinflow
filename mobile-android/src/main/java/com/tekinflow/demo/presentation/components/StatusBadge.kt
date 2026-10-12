package com.tekinflow.demo.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.UrgencyLevel
import com.tekinflow.demo.presentation.theme.BadgeShape
import com.tekinflow.demo.presentation.theme.SatinalmaTheme
import com.tekinflow.demo.presentation.theme.StatusCancelled
import com.tekinflow.demo.presentation.theme.StatusCancelledContainer
import com.tekinflow.demo.presentation.theme.StatusDelivered
import com.tekinflow.demo.presentation.theme.StatusDeliveredContainer
import com.tekinflow.demo.presentation.theme.StatusInProgress
import com.tekinflow.demo.presentation.theme.StatusInProgressContainer
import com.tekinflow.demo.presentation.theme.StatusOnTheWay
import com.tekinflow.demo.presentation.theme.StatusOnTheWayContainer
import com.tekinflow.demo.presentation.theme.StatusPending
import com.tekinflow.demo.presentation.theme.StatusPendingContainer
import com.tekinflow.demo.presentation.theme.UrgencyHigh
import com.tekinflow.demo.presentation.theme.UrgencyLow
import com.tekinflow.demo.presentation.theme.UrgencyNormal
import com.tekinflow.demo.presentation.theme.UrgencyUrgent

@Composable
fun StatusBadge(
    status: MaterialStatus,
    modifier: Modifier = Modifier
) {
    val target = statusMeta(status)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val containerColor by animateColorAsState(if (isDark) target.content.copy(alpha = 0.14f) else target.container, label = "status_container")
    val contentColor by animateColorAsState(if (isDark) lerp(target.content, MaterialTheme.colorScheme.onSurface, 0.25f) else target.content, label = "status_content")

    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = target.icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.displayName,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun UrgencyBadge(
    urgencyLevel: UrgencyLevel,
    modifier: Modifier = Modifier
) {
    val color = when (urgencyLevel) {
        UrgencyLevel.LOW -> UrgencyLow
        UrgencyLevel.NORMAL -> UrgencyNormal
        UrgencyLevel.HIGH -> UrgencyHigh
        UrgencyLevel.URGENT -> UrgencyUrgent
    }


    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = BadgeShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = color, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = urgencyLevel.displayName,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private data class StatusMeta(
    val container: Color,
    val content: Color,
    val icon: ImageVector
)

private fun statusMeta(status: MaterialStatus): StatusMeta {
    return when (status) {
        MaterialStatus.PENDING -> StatusMeta(StatusPendingContainer, StatusPending, Icons.Default.HourglassEmpty)
        MaterialStatus.IN_PROGRESS -> StatusMeta(StatusInProgressContainer, StatusInProgress, Icons.Default.LocalShipping)
        MaterialStatus.PURCHASED -> StatusMeta(StatusInProgressContainer, StatusInProgress, Icons.Default.LocalShipping)
        MaterialStatus.ON_THE_WAY -> StatusMeta(StatusOnTheWayContainer, StatusOnTheWay, Icons.Default.LocalShipping)
        MaterialStatus.DELIVERED -> StatusMeta(StatusDeliveredContainer, StatusDelivered, Icons.Default.Inventory2)
        MaterialStatus.CANCELLED -> StatusMeta(StatusCancelledContainer, StatusCancelled, Icons.Default.Cancel)
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusBadgePreview() {
    SatinalmaTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(status = MaterialStatus.PENDING)
            StatusBadge(status = MaterialStatus.IN_PROGRESS)
            UrgencyBadge(urgencyLevel = UrgencyLevel.URGENT)
        }
    }
}
