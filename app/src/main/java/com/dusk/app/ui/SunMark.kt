package com.dusk.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/** The app mark: a sun half set on the horizon. */
@Composable
fun SunMark(size: Int, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.onBackground
    Canvas(modifier.size(size.dp)) {
        val w = this.size.width
        val r = w * 0.3f
        val cy = w * 0.58f
        drawArc(Color(0xFFFFB86B), 180f, 180f, true, Offset(w / 2 - r, cy - r), Size(r * 2, r * 2))
        drawLine(line, Offset(w * 0.12f, cy + w * 0.05f), Offset(w * 0.88f, cy + w * 0.05f), strokeWidth = w * 0.045f, cap = StrokeCap.Round)
        drawLine(line.copy(alpha = 0.45f), Offset(w * 0.3f, cy + w * 0.17f), Offset(w * 0.7f, cy + w * 0.17f), strokeWidth = w * 0.045f, cap = StrokeCap.Round)
    }
}
