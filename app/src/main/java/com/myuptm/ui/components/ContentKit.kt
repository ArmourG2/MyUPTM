package com.myuptm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Content-accent kit (visual language, revised): the owner asked for SIMPLE
// accents instead of gradient fills — a solid BLUE line on the LEFT edge of
// content cards and a solid RED line across the TOP edge. The palette echoes
// CampusHero.kt (the red→blue stripes on the building) as two flat colours.
// Nothing here holds logic; every callback stays with the caller.

/** Solid brand blue — the LEFT edge accent of content cards. */
internal val AccentBlue = Color(0xFF1E88E5)

/** Solid brand red — the TOP edge accent of content cards. */
internal val AccentRed = Color(0xFFD22630)

/**
 * The owner-directed accent recipe for content cards: a solid BLUE bar on
 * the left edge and a solid RED bar across the top edge. Place inside a Box
 * that fills the card; the card's own shape clips both bars.
 */
@Composable
fun BoxScope.CardAccentEdges(
    leftWidth: Dp = 6.dp,
    topHeight: Dp = 4.dp
) {
    // RED line across the top edge.
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(topHeight)
            .background(AccentRed)
    )
    // BLUE line down the left edge.
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .width(leftWidth)
            .fillMaxHeight()
            .background(AccentBlue)
    )
}

/**
 * The small solid-BLUE accent tick for section headers (formerly the
 * red→blue gradient tick — simplified per owner direction).
 */
@Composable
fun BrandTick(
    modifier: Modifier = Modifier,
    tickWidth: Dp = 4.dp,
    tickHeight: Dp = 18.dp
) {
    Box(
        modifier = modifier
            .width(tickWidth)
            .height(tickHeight)
            .clip(RoundedCornerShape(2.dp))
            .background(AccentBlue)
    )
}
