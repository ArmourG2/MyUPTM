package com.myuptm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Placeholder camera viewfinder: four rounded corner brackets.
 * Pure Canvas drawing, no camera. Real scanning is backlog B-003.
 */
@Composable
fun QrViewfinder(
    modifier: Modifier = Modifier,
    cornerLength: Dp = 40.dp,
    cornerRadius: Dp = 16.dp,
    strokeWidth: Dp = 4.dp
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val len = cornerLength.toPx()
        val r = cornerRadius.toPx()
        val w = size.width
        val h = size.height

        // Each corner: arm in -> 90° arc -> arm out
        val topLeft = Path().apply {
            moveTo(len, 0f)
            lineTo(r, 0f)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 270f, -90f, false)
            lineTo(0f, len)
        }
        val topRight = Path().apply {
            moveTo(w - len, 0f)
            lineTo(w - r, 0f)
            arcTo(Rect(w - 2 * r, 0f, w, 2 * r), 270f, 90f, false)
            lineTo(w, len)
        }
        val bottomRight = Path().apply {
            moveTo(w, h - len)
            lineTo(w, h - r)
            arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
            lineTo(w - len, h)
        }
        val bottomLeft = Path().apply {
            moveTo(0f, h - len)
            lineTo(0f, h - r)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 180f, -90f, false)
            lineTo(len, h)
        }

        listOf(topLeft, topRight, bottomRight, bottomLeft).forEach {
            drawPath(it, color, style = stroke)
        }
    }
}