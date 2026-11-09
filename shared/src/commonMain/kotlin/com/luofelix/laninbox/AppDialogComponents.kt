package com.luofelix.laninbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.close
import org.jetbrains.compose.resources.stringResource

/** Shared title treatment for centered dialogs and Android bottom sheets. */
@Composable
internal fun AppDialogHeader(
    title: String,
    icon: ImageVector,
    onClose: () -> Unit,
    subtitle: String? = null,
    destructive: Boolean = false,
) {
    val desktop = LocalDesktopDialogs.current
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (desktop) colors.surface else colors.headerContainerColor,
        contentColor = colors.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = if (desktop) 24.dp else 20.dp,
                end = if (desktop) 16.dp else 8.dp,
                top = if (desktop) 20.dp else 16.dp,
                bottom = if (desktop) 20.dp else 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (desktop) 14.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(if (desktop) 14.dp else 12.dp),
                color = if (destructive) colors.errorContainer else colors.primaryContainer,
                contentColor = if (destructive) colors.onErrorContainer else colors.onPrimaryContainer,
            ) {
                Box(Modifier.size(if (desktop) 48.dp else 40.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(if (desktop) 24.dp else 22.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = if (desktop) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(if (desktop) 36.dp else 48.dp)) {
                Icon(AppIcons.Close, stringResource(Res.string.close), Modifier.size(if (desktop) 18.dp else 20.dp))
            }
        }
    }
}

/** Applies consistent touch targets and action emphasis without owning business state. */
@Composable
internal fun AppDialogButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    secondary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val desktop = LocalDesktopDialogs.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (desktop) 12.dp else 14.dp)
    val buttonModifier = modifier.heightIn(min = if (desktop) 40.dp else 48.dp)
    val content: @Composable RowScope.() -> Unit = {
        icon?.let {
            Icon(it, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, textAlign = TextAlign.Center)
    }
    when {
        secondary && desktop -> OutlinedButton(
            onClick = onClick, modifier = buttonModifier, enabled = enabled, shape = shape,
            border = BorderStroke(1.dp, colors.outlineVariant), content = content,
        )
        secondary -> FilledTonalButton(
            onClick = onClick, modifier = buttonModifier, enabled = enabled, shape = shape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (destructive) colors.errorContainer else colors.primaryContainer,
                contentColor = if (destructive) colors.onErrorContainer else colors.onPrimaryContainer,
            ),
            content = content,
        )
        else -> Button(
            onClick = onClick, modifier = buttonModifier, enabled = enabled, shape = shape,
            colors = if (destructive) {
                ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
            } else ButtonDefaults.buttonColors(),
            content = content,
        )
    }
}

@Composable
internal fun AppDialogPanel(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (emphasized) colors.primaryContainer.copy(alpha = 0.45f).compositeOver(colors.surface) else colors.background,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.75f)),
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Composes form fields only while expanded, leaving input state with the caller. */
@Composable
internal fun AppDialogDisclosure(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle)
                    .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = colors.primary)
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Icon(if (expanded) AppIcons.ChevronUp else AppIcons.ChevronDown, null, Modifier.size(18.dp))
            }
            if (expanded) {
                HorizontalDivider(color = colors.outlineVariant)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
    }
}

@Composable
internal fun AppPopupMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(if (LocalDesktopDialogs.current) 14.dp else 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}
