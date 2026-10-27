package com.wanderwildwood.tokei.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import kotlinx.coroutines.delay

/** The smallest text anywhere in the app, in sp: helper lines and notes are never smaller. */
private const val MIN_TEXT_SP = 14f

private fun TextStyle.atLeast14(): TextStyle =
    if (fontSize.value < MIN_TEXT_SP) copy(fontSize = MIN_TEXT_SP.sp, lineHeight = (MIN_TEXT_SP * 1.3f).sp) else this

/** Notes, values under a row, labels over a field: the theme's smallest text, never under 14 sp. */
@Composable
fun helperStyle(): TextStyle = MaterialTheme.typography.labelSmall.atLeast14()

/** Text on the small buttons, never under 14 sp. */
@Composable
fun smallBodyStyle(): TextStyle = MaterialTheme.typography.bodySmall.atLeast14()

/** How long a tap-again row stays armed before it puts itself back. */
const val ARM_MS = 6_000L

/** An icon in a top bar, with a target a thumb can find. */
@Composable
fun BarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A row that says what it is and what it is set to, and does something when pressed. */
@Composable
fun SettingRow(title: String, value: String?, bold: Boolean = false, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 14.dp),
    ) {
        TextMMD(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
        if (value != null) TextMMD(text = value, style = helperStyle())
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
            if (note != null) TextMMD(text = note, style = helperStyle())
        }
        Spacer(Modifier.width(12.dp))
        SwitchMMD(checked = checked, onCheckedChange = null)
    }
}

/** One of a few choices, pressed in place: MMD's radio button, the row taking the press. */
@Composable
fun ChoiceRow(title: String, chosen: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButtonMMD(selected = chosen, onClick = null)
        Spacer(Modifier.width(8.dp))
        TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A box ticked in place, the row taking the press. */
@Composable
fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckboxMMD(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(8.dp))
        TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * A row that asks once, in its own face: the first press arms it and changes what it says,
 * the second does it. It disarms itself after six seconds, so a stray press does not leave
 * a live trigger for whoever picks the phone up next.
 */
@Composable
fun ArmedRow(label: String, armedLabel: String, onConfirmed: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (!armed) return@LaunchedEffect
        delay(ARM_MS)
        armed = false
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (armed) {
                    armed = false
                    onConfirmed()
                } else {
                    armed = true
                }
            }
            .padding(vertical = 14.dp),
    ) {
        TextMMD(
            text = if (armed) armedLabel else label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** A small heading over a group of rows. */
@Composable
fun Heading(text: String, modifier: Modifier = Modifier) {
    TextMMD(
        text = text,
        style = helperStyle(),
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

/** A text field with its label over it, the way a form reads on paper. */
@Composable
fun Field(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    hint: String? = null,
    number: Boolean = false,
    phone: Boolean = false,
    singleLine: Boolean = true,
    words: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        TextMMD(text = label, style = helperStyle())
        TextFieldMMD(
            value = value,
            onValueChange = onValue,
            singleLine = singleLine,
            placeholder = hint?.let { { TextMMD(text = it, style = helperStyle()) } },
            keyboardOptions = KeyboardOptions(
                capitalization = if (words && !number && !phone) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                keyboardType = when {
                    phone -> KeyboardType.Phone
                    number -> KeyboardType.Number
                    else -> KeyboardType.Text
                },
            ),
            modifier = Modifier.fillMaxWidth().textActions(),
        )
    }
}
