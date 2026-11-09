package com.luofelix.laninbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.close
import org.jetbrains.compose.resources.stringResource

internal val LocalDesktopDialogs = staticCompositionLocalOf { false }

/**
 * Uses desktop modal styling in [App] and standard Material dialogs in the Android UI.
 * Desktop content scrolls within the owning window while the title and actions remain visible.
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String,
    icon: ImageVector,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    destructive: Boolean = false,
    subtitle: String? = null,
    width: Dp = 480.dp,
    text: @Composable () -> Unit,
) {
    if (!LocalDesktopDialogs.current) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(title) },
            text = text,
            confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
            dismissButton = dismissLabel?.let { label ->
                { TextButton(onClick = onDismissRequest) { Text(label) } }
            },
        )
        return
    }

    // Keep the modal within its owner when the window size or display scaling changes.
    val ownerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val dialogWidth = minOf(width, with(density) { ownerSize.width.toDp() } - 48.dp).coerceAtLeast(240.dp)
    val maxHeight = (with(density) { ownerSize.height.toDp() } - 48.dp).coerceAtLeast(240.dp)
    val colors = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(dialogWidth).heightIn(max = maxHeight),
            shape = RoundedCornerShape(24.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant),
            shadowElevation = 16.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (destructive) colors.errorContainer else colors.primaryContainer,
                        contentColor = if (destructive) colors.onErrorContainer else colors.onPrimaryContainer,
                    ) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        subtitle?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismissRequest, modifier = Modifier.size(36.dp)) {
                        Icon(AppIcons.Close, stringResource(Res.string.close), Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
                Column(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(24.dp),
                ) {
                    CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium, content = text)
                    }
                }
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
                Surface(color = colors.surfaceVariant.copy(alpha = 0.3f)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dismissLabel?.let {
                            OutlinedButton(
                                onClick = onDismissRequest,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.heightIn(min = 40.dp).widthIn(min = 88.dp),
                                border = BorderStroke(1.dp, colors.outlineVariant),
                            ) { Text(it) }
                        }
                        Button(
                            onClick = onConfirm,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 40.dp).widthIn(min = 100.dp),
                            colors = if (destructive) {
                                ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                            } else ButtonDefaults.buttonColors(),
                        ) { Text(confirmLabel) }
                    }
                }
            }
        }
    }
}
