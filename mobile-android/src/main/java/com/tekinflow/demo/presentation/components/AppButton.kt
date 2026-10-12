package com.tekinflow.demo.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tekinflow.demo.presentation.theme.AppSpacing
import com.tekinflow.demo.presentation.theme.ButtonShape
import com.tekinflow.demo.presentation.theme.Error
import com.tekinflow.demo.presentation.theme.SatinalmaTheme
import com.tekinflow.demo.presentation.theme.Warning

enum class ButtonVariant {
    PRIMARY,
    SECONDARY,
    DANGER,
    WARNING
}

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val warningContent = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFFCF7A) else Color(0xFF7A4800)

    when (variant) {
        ButtonVariant.PRIMARY -> GradientButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            isLoading = isLoading,
            enabled = enabled
        )

        ButtonVariant.SECONDARY -> OutlinedButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            modifier = modifier.fillMaxWidth(),
            enabled = enabled && !isLoading,
            shape = ButtonShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            ButtonContent(
                text = text,
                isLoading = isLoading,
                textColor = MaterialTheme.colorScheme.onSurface
            )
        }

        ButtonVariant.DANGER -> Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            modifier = modifier.fillMaxWidth(),
            enabled = enabled && !isLoading,
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Error,
                contentColor = Color.White
            )
        ) {
            ButtonContent(text = text, isLoading = isLoading, textColor = Color.White)
        }

        ButtonVariant.WARNING -> Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            modifier = modifier.fillMaxWidth(),
            enabled = enabled && !isLoading,
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Warning.copy(alpha = 0.12f),
                contentColor = warningContent
            )
        ) {
            ButtonContent(text = text, isLoading = isLoading, textColor = warningContent)
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    isLoading: Boolean,
    textColor: Color
) {
    Row(horizontalArrangement = Arrangement.Center) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = textColor,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(AppSpacing.s2))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppButtonPreview() {
    SatinalmaTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s2)
        ) {
            AppButton(text = "Kaydet", onClick = {})
            AppButton(text = "Vazgeç", onClick = {}, variant = ButtonVariant.SECONDARY)
            AppButton(text = "Sil", onClick = {}, variant = ButtonVariant.DANGER)
        }
    }
}
