package com.luofelix.laninbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal val LocalDesktopDialogs = staticCompositionLocalOf { false }

/**
 * Provides the shared modal shell, adapting its header, spacing, and actions to the platform.
 * Content scrolls within the owning window while the title and actions remain visible.
 * Dismissing the dialog never invokes [onConfirm].
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
    confirmEnabled: Boolean = true,
    text: @Composable () -> Unit,
) {
    val desktop = LocalDesktopDialogs.current
    val ownerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val margin = if (desktop) 48.dp else 32.dp
    val preferredWidth = if (desktop) width else minOf(width, 560.dp)
    val dialogWidth = minOf(preferredWidth, with(density) { ownerSize.width.toDp() } - margin).coerceAtLeast(240.dp)
    // Leave room for mobile system bars; the dialog's constraints also shrink when the IME opens.
    val ownerHeight = with(density) { ownerSize.height.toDp() }
    val maxHeight = (if (desktop) ownerHeight - margin else ownerHeight * 0.85f).coerceAtLeast(240.dp)
    val padding = if (desktop) 24.dp else 20.dp
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(dialogWidth).heightIn(max = maxHeight).semantics { paneTitle = title },
            shape = RoundedCornerShape(24.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant),
            shadowElevation = if (desktop) 16.dp else 8.dp,
        ) {
            Column {
                AppDialogHeader(title, icon, onDismissRequest, subtitle, destructive)
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
                Column(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(padding),
                ) {
                    CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium, content = text)
                    }
                }
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
                Surface(color = if (desktop) colors.surfaceVariant.copy(alpha = 0.3f) else colors.surface) {
                    if (desktop) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = padding, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            dismissLabel?.let {
                                AppDialogButton(it, onDismissRequest, Modifier.widthIn(min = 88.dp), secondary = true)
                            }
                            AppDialogButton(
                                confirmLabel, onConfirm, Modifier.widthIn(min = 100.dp),
                                destructive = destructive, enabled = confirmEnabled,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = padding, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            dismissLabel?.let {
                                AppDialogButton(it, onDismissRequest, Modifier.weight(1f), secondary = true)
                            }
                            AppDialogButton(
                                confirmLabel, onConfirm, Modifier.weight(1f),
                                destructive = destructive, enabled = confirmEnabled,
                            )
                        }
                    }
                }
            }
        }
    }
}
