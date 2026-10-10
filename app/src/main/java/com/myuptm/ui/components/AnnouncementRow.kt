package com.myuptm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myuptm.domain.model.Post

/**
 * Owner accent recipe: solid BLUE line on the left edge, solid RED line on
 * the top edge, on the DEFAULT surface tier (correct contrast in both
 * themes). Date/faculty meta stays as tonal pills. Callback unchanged.
 */
@Composable
fun AnnouncementRow(post: Post, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            CardAccentEdges(leftWidth = 6.dp, topHeight = 3.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetaPill(
                        text = post.date,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    MetaPill(
                        text = post.facultyTag,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// Small tonal pill for meta text (date / faculty tag).
@Composable
private fun MetaPill(text: String, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
