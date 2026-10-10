package com.myuptm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myuptm.domain.model.SupportLink

/**
 * Owner accent recipe: solid BLUE line on the left edge, solid RED line on
 * the top edge, DEFAULT surface tier, and the icon back to a plain
 * `primary` tint (no filled tile). Callback and tile width unchanged.
 */
@Composable
fun SupportLinkTile(link: SupportLink, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.width(112.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            CardAccentEdges(leftWidth = 6.dp, topHeight = 3.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 14.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Icon(
                    imageVector = iconFor(link.id),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = link.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun iconFor(id: String): ImageVector = when (id) {
    "lms" -> Icons.AutoMirrored.Filled.List
    "cms" -> Icons.Filled.AccountBox
    "calendar" -> Icons.Filled.DateRange
    "epay" -> Icons.Filled.ShoppingCart
    "id" -> Icons.Filled.AccountCircle
    else -> Icons.Filled.Info
}
