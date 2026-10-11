package com.securebank.mobile.ui.components

import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.securebank.mobile.ui.theme.Sb

/**
 * Controles de entrada com cores do tema do app (não as padrão do Material, cujo contorno é o token `outline`,
 * pensado para divisórias e quase invisível no escuro). A borda em repouso tem contraste de pelo menos 3:1 nos dois temas.
 */
@Composable
fun sbFieldColors(): TextFieldColors {
    val sb = Sb.colors
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = sb.field,
        unfocusedContainerColor = sb.field,
        errorContainerColor = sb.field,
        disabledContainerColor = sb.field.copy(alpha = sb.field.alpha * 0.5f),
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = sb.fieldBorder,
        errorBorderColor = scheme.error,
        disabledBorderColor = sb.fieldBorder.copy(alpha = 0.4f),
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        errorTextColor = scheme.onSurface,
        disabledTextColor = scheme.onSurface.copy(alpha = 0.5f),
        cursorColor = scheme.primary,
        errorCursorColor = scheme.error,
        selectionColors = TextSelectionColors(handleColor = scheme.primary, backgroundColor = scheme.primary.copy(alpha = 0.32f)),
        focusedPlaceholderColor = sb.fieldPlaceholder,
        unfocusedPlaceholderColor = sb.fieldPlaceholder,
        errorPlaceholderColor = sb.fieldPlaceholder,
        disabledPlaceholderColor = sb.fieldPlaceholder.copy(alpha = 0.5f),
    )
}

/** Interruptor legível desligado: contorno e trilho visíveis (o padrão some no fundo escuro). */
@Composable
fun sbSwitchColors(): SwitchColors {
    val sb = Sb.colors
    val scheme = MaterialTheme.colorScheme
    return SwitchDefaults.colors(
        checkedThumbColor = scheme.onPrimary,
        checkedTrackColor = scheme.primary,
        checkedBorderColor = scheme.primary,
        uncheckedThumbColor = sb.fieldBorder,
        uncheckedTrackColor = sb.field,
        uncheckedBorderColor = sb.fieldBorder,
    )
}

@Composable
fun sbRadioColors(): RadioButtonColors =
    RadioButtonDefaults.colors(
        selectedColor = MaterialTheme.colorScheme.primary,
        unselectedColor = Sb.colors.fieldBorder,
    )

/** Chip de filtro com contorno legível e o acento do app quando selecionado. */
@Composable
fun SbFilterChip(selected: Boolean, onClick: () -> Unit, label: String, enabled: Boolean = true) {
    val sb = Sb.colors
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = scheme.onSurface,
            selectedContainerColor = scheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = scheme.onSurface,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = sb.fieldBorder,
            selectedBorderColor = scheme.primary,
            selectedBorderWidth = 1.5.dp,
        ),
    )
}
