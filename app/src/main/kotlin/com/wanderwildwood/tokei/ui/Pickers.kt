package com.wanderwildwood.tokei.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tokei.R

@Composable
fun DialogButtons(onCancel: () -> Unit, onOk: () -> Unit, okLabel: String = stringResource(R.string.ok)) {
    Row(Modifier.fillMaxWidth()) {
        OutlinedButtonMMD(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp)) {
            TextMMD(text = stringResource(R.string.cancel), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        ButtonMMD(onClick = onOk, modifier = Modifier.weight(1f).height(48.dp)) {
            TextMMD(text = okLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A short list to choose one from, as Medicine's settings do. */
@Composable
fun <T> OptionsDialog(title: String, options: List<Pair<T, String>>, chosen: T, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        for ((value, label) in options) {
            ChoiceRow(label, value == chosen) {
                onPick(value)
                onDismiss()
            }
        }
    }
}

/** A full-width button inside a dialog. */
@Composable
fun DialogButton(text: String, filled: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(48.dp)
    if (filled) {
        ButtonMMD(onClick = onClick, modifier = modifier) { TextMMD(text = text, style = MaterialTheme.typography.bodyMedium) }
    } else {
        OutlinedButtonMMD(onClick = onClick, modifier = modifier) { TextMMD(text = text, style = MaterialTheme.typography.bodyMedium) }
    }
}
