package com.tekinflow.demo.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tekinflow.demo.presentation.theme.AppSpacing
import com.tekinflow.demo.presentation.theme.InputShape
import com.tekinflow.demo.presentation.theme.SatinalmaTheme

@Composable
fun AppTextField(
    label: String? = null,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable (() -> Unit))? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onFieldClick: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val fieldContent: @Composable () -> Unit = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                enabled = enabled,
                isError = isError,
                singleLine = singleLine,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                label = label?.takeIf { it.isNotBlank() }?.let { { Text(text = it) } },
                placeholder = placeholder?.let { { Text(text = it, maxLines = if (singleLine) 1 else maxLines, overflow = TextOverflow.Ellipsis) } },
                leadingIcon = leadingIcon?.let {
                    {
                        Icon(
                            imageVector = it,
                            modifier = Modifier.size(20.dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                trailingIcon = trailingIcon,
                shape = InputShape,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = if (readOnly) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = if (readOnly) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                supportingText = if (isError && !errorMessage.isNullOrBlank()) {
                    {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    null
                }
            )
        }

        if (onFieldClick != null) {
            val clickInteractionSource = remember { MutableInteractionSource() }
            Box(modifier = Modifier.fillMaxWidth()) {
                fieldContent()
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = clickInteractionSource,
                            indication = null,
                            onClick = onFieldClick
                        )
                )
            }
        } else {
            fieldContent()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppTextFieldPreview() {
    SatinalmaTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s2)
        ) {
            AppTextField(
                label = "Firma Adı",
                value = "TekinFlow",
                onValueChange = {}
            )
            AppTextField(
                label = "Talep Numarası",
                value = "TLP-2026-001",
                onValueChange = {},
                readOnly = true
            )
        }
    }
}
