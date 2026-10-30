package com.wanderwildwood.tokei.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import java.util.Locale

/*
 * The stock Alarm app's way of setting a time: each number in a rounded box with an arrow above
 * and below it. A press on an arrow moves the number by one; a press on the box brings up the
 * number keys, and two figures typed set it outright, so 6:47 is typed, not stepped to.
 */

private fun two(n: Int) = String.format(Locale.ROOT, "%02d", n)

/** One number in its box, with its arrows. [show] writes it; typing takes the last [figures] figures. */
@Composable
fun NumberBox(
    value: Int,
    min: Int,
    max: Int,
    description: String,
    onValue: (Int) -> Unit,
    width: Int = 96,
    size: Int = 46,
    show: (Int) -> String = ::two,
    figures: Int = 2,
) {
    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(show(value))) }
    LaunchedEffect(value, focused) {
        if (!focused) field = TextFieldValue(show(value))
    }
    fun step(by: Int) {
        val span = max - min + 1
        val next = ((value - min + by) % span + span) % span + min
        field = TextFieldValue(show(next), TextRange(show(next).length))
        onValue(next)
    }
    val ink = MaterialTheme.colorScheme.onSurface
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width.dp, 48.dp).clickable { step(1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Up, contentDescription = null, tint = ink, modifier = Modifier.size(48.dp))
        }
        Box(
            Modifier
                .size(width.dp, 80.dp)
                .border(BorderStroke(2.dp, ink), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = field,
                onValueChange = { v ->
                    val digits = v.text.filter(Char::isDigit).takeLast(figures)
                    field = TextFieldValue(digits, TextRange(digits.length))
                    digits.toIntOrNull()?.let { if (it in min..max) onValue(it) }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = size.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    color = ink,
                ),
                cursorBrush = SolidColor(ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) field = field.copy(selection = TextRange(0, field.text.length))
                    }
                    .semantics { contentDescription = description },
            )
        }
        Box(Modifier.size(width.dp, 48.dp).clickable { step(-1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Down, contentDescription = null, tint = ink, modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun Colon() {
    TextMMD(text = ":", fontSize = 40.sp, modifier = Modifier.padding(horizontal = 3.dp))
}

/** An alarm's time, on the phone's 12- or 24-hour clock. [hour] is always 0 to 23. */
@Composable
fun TimeStepper(hour: Int, minute: Int, onChange: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val is24 = remember { Times.is24(context) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (is24) {
            NumberBox(hour, 0, 23, context.getString(R.string.cd_hour), { onChange(it, minute) })
        } else {
            val pm = hour >= 12
            NumberBox((hour + 11) % 12 + 1, 1, 12, context.getString(R.string.cd_hour), { onChange(it % 12 + if (pm) 12 else 0, minute) }, show = { it.toString() })
        }
        Colon()
        NumberBox(minute, 0, 59, context.getString(R.string.cd_minute), { onChange(hour, it) })
        if (!is24) {
            Spacer(Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AmPm(context.getString(R.string.am), hour < 12) { if (hour >= 12) onChange(hour - 12, minute) }
                AmPm(context.getString(R.string.pm), hour >= 12) { if (hour < 12) onChange(hour + 12, minute) }
            }
        }
    }
}

@Composable
private fun AmPm(text: String, chosen: Boolean, onClick: () -> Unit) {
    HouseButton(text, onClick, Modifier.size(50.dp, 48.dp), on = chosen, compact = true)
}

/** A timer's length: hours, minutes and seconds, each in its box. */
@Composable
fun LengthStepper(seconds: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        NumberBox(h, 0, 23, context.getString(R.string.cd_hours), { onChange(it * 3600 + m * 60 + s) }, width = 84, size = 40)
        Colon()
        NumberBox(m, 0, 59, context.getString(R.string.cd_minutes), { onChange(h * 3600 + it * 60 + s) }, width = 84, size = 40)
        Colon()
        NumberBox(s, 0, 59, context.getString(R.string.cd_seconds), { onChange(h * 3600 + m * 60 + it) }, width = 84, size = 40)
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center) {
        listOf(R.string.unit_hours, R.string.unit_minutes, R.string.unit_seconds).forEachIndexed { i, r ->
            if (i > 0) Spacer(Modifier.width(20.dp))
            Box(Modifier.width(84.dp), contentAlignment = Alignment.Center) {
                TextMMD(text = context.getString(r), style = helperStyle())
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
