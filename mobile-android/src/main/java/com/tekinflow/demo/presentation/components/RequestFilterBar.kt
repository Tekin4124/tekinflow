package com.tekinflow.demo.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun RequestFilterBar(
    primaryOptions: List<String>, primarySelected: String, onPrimarySelect: (String) -> Unit,
    secondaryLabel: String, secondaryOptions: List<String>, secondarySelected: String, onSecondarySelect: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterMenu("Durum", primaryOptions, primarySelected, onPrimarySelect, Modifier.weight(1f))
        FilterMenu(secondaryLabel, secondaryOptions, secondarySelected, onSecondarySelect, Modifier.weight(1f))
    }
}

@Composable
private fun FilterMenu(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Text("$label: $selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = MaterialTheme.colorScheme.surface) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false },
                    trailingIcon = if (option == selected) { { Text("✓", color = MaterialTheme.colorScheme.primary) } } else null)
            }
        }
    }
}
