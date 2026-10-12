package com.tekinflow.demo.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.model.UrgencyLevel
import com.tekinflow.demo.presentation.theme.AppSpacing
import com.tekinflow.demo.presentation.theme.CardShape
import com.tekinflow.demo.presentation.theme.Error
import com.tekinflow.demo.presentation.theme.NewBadge
import com.tekinflow.demo.presentation.theme.SatinalmaTheme
import com.tekinflow.demo.util.toFormattedDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestCard(
    request: PurchaseRequest,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    showNewBadge: Boolean = false,
    showObjection: Boolean = true,
    footerContent: (@Composable () -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isCancelled = request.materialStatus == MaterialStatus.CANCELLED
    val isUrgent = request.urgencyLevel == UrgencyLevel.URGENT
    val cardAlpha = if (isCancelled) 0.5f else 1f

    Surface(
        modifier = modifier
            .scale(if (isPressed) 0.98f else 1f)
            .alpha(cardAlpha),
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isPressed) 2.dp else 0.dp,
        border = BorderStroke(
            1.dp,
            if (isUrgent) Error.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(modifier = Modifier.padding(horizontal = AppSpacing.s3, vertical = AppSpacing.s2)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(AppSpacing.s1)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UrgencyBadge(urgencyLevel = request.urgencyLevel)
                    StatusBadge(status = if (request.canBeTakenOverByDriver) MaterialStatus.PENDING else request.materialStatus)
                    if (showNewBadge) Text("Yeni", modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                    trailingAction?.invoke()
                }

                Text(
                    text = request.companyName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (isCancelled) TextDecoration.LineThrough else TextDecoration.None
                )

                Text(
                    text = request.itemsSummary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = if (isCancelled) TextDecoration.LineThrough else TextDecoration.None
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Talep No: ${request.requestNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = if (isCancelled) TextDecoration.LineThrough else TextDecoration.None
                    )
                    if (isCancelled) {
                        CancellationBadge()
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaChip(
                        icon = Icons.Default.CalendarMonth,
                        text = request.purchaseDate.ifBlank { request.createdAt.toFormattedDate() }
                    )
                    if (request.licensePlate.isNotBlank()) {
                        MetaChip(
                            icon = Icons.Default.LocalShipping,
                            text = request.licensePlate
                        )
                    }
                }

                if (showObjection && request.hasObjection && !request.objectionReason.isNullOrBlank()) {
                    WarningBanner(text = "İtiraz: ${request.objectionReason}")
                }

                footerContent?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    it()
                }
            }


        }
    }
}

private fun PurchaseRequest.itemsSummary(): String {
    val visibleItems = items
        .filter { it.itemName.isNotBlank() }
        .take(2)
        .joinToString(" / ") { item ->
            "${item.itemName} (${item.quantity} ${item.unit.displayName})"
        }
    val remaining = (items.size - 2).takeIf { it > 0 }?.let { " +$it daha" }.orEmpty()
    return (visibleItems.ifBlank { itemToPurchase }) + remaining
}

@Composable
private fun MetaChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WarningBanner(text: String) {
    Surface(
        color = Color(0xFFF59E0B).copy(alpha = 0.16f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CancellationBadge() {
    Surface(
        color = Error.copy(alpha = 0.12f),
        contentColor = Error,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "İPTAL",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PulsingNewDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(12.dp)
            .background(NewBadge, CircleShape)
    )
}

@Preview(showBackground = true)
@Composable
private fun RequestCardPreview() {
    SatinalmaTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RequestCard(
                request = PurchaseRequest(
                    requestNumber = "TLP-2026-001",
                    itemToPurchase = "Çelik Profil 100x50mm",
                    companyName = "Test Firma A.Ş.",
                    urgencyLevel = UrgencyLevel.URGENT,
                    materialStatus = MaterialStatus.IN_PROGRESS,
                    purchaseDate = "15.04.2026",
                    licensePlate = "34 ABC 123",
                    objectionReason = "Adres teyidi gerekli",
                    hasObjection = true
                ),
                onClick = {},
                showNewBadge = true
            )
        }
    }
}
