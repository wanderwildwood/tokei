package com.wanderwildwood.tokei.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD

/*
 * The pieces of the stock Alarm app's look (KompaktAlarm 1.3.0), drawn again with MMD: a row with
 * a bold name over its value and a chevron, the dotted rule between rows, the black pill in the
 * top bar that saves. The icons the stock app puts at the
 * left of each row are left out, as everywhere in this shop.
 */

/** A row that opens a page: its name in bold, what it is set to under it, and a chevron. */
@Composable
fun NavRow(title: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TextMMD(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (value != null) TextMMD(text = value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal))
        }
        Icon(Icons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
    }
}

/** The dotted rule the stock app draws between rows. */
@Composable
fun Dotted(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())),
        )
    }
}

/** The black pill at the right of a top bar: Create, Save. */
@Composable
fun BarPill(text: String, onClick: () -> Unit) {
    ButtonMMD(onClick = onClick, modifier = Modifier.padding(end = 8.dp).height(44.dp)) {
        TextMMD(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** What a list says when there is nothing in it, and the one thing to do about it. */
@Composable
fun Empty(text: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TextMMD(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        com.mudita.mmd.components.buttons.OutlinedButtonMMD(onClick = onClick, modifier = Modifier.height(52.dp)) {
            TextMMD(text = button, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** A small square box with a word in it, for the buttons at the right of a timer's row. */
@Composable
fun SmallBox(text: String, filled: Boolean = false, onClick: () -> Unit) {
    val m = Modifier.height(44.dp).width(92.dp)
    if (filled) {
        ButtonMMD(onClick = onClick, modifier = m) { TextMMD(text = text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
    } else {
        com.mudita.mmd.components.buttons.OutlinedButtonMMD(onClick = onClick, modifier = m) {
            TextMMD(text = text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun Centered(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
