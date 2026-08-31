package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_dismiss
import org.jetbrains.compose.resources.stringResource

/**
 * The pieces every management screen is built out of.
 *
 * Here rather than in whichever screen needed them first, because Kotlin's
 * `private` is file-private: the cities screen having them meant the vehicles
 * screen could only copy them. Two catalogs that look subtly different is the
 * cheapest kind of inconsistency to acquire and the most tedious to remove.
 */

/** A titled band with a count beside it, over a rule. */
@Composable
internal fun SectionHeading(title: String, count: String) {
    Column(Modifier.padding(top = 24.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                count,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** One line of a table: content on the left, actions on the right. */
@Composable
internal fun RowCard(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** An empty state, or anything else that is context rather than content. */
@Composable
internal fun Muted(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

/**
 * The last thing that happened, at the bottom of the page.
 *
 * Not a transient toast. Half of these messages report a write somebody may need
 * to tell a colleague about, and one that vanishes on a timer is one nobody can
 * re-read.
 */
@Composable
internal fun BoxScope.Banner(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.ad_dismiss)) }
    }
}
