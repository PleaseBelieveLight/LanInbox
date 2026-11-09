package com.luofelix.laninbox

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

/** Reuses the modal header and bounds a sheet's scrolling body above the system bars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AndroidAppSheet(
    title: String,
    icon: ImageVector,
    onClose: () -> Unit,
    subtitle: String? = null,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = colors.surface,
        contentColor = colors.onSurface,
        dragHandle = null,
    ) {
        Surface(color = colors.headerContainerColor) {
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 32.dp, height = 4.dp).background(colors.outlineVariant, RoundedCornerShape(50)))
            }
        }
        AppDialogHeader(title, icon, onClose, subtitle)
        HorizontalDivider(color = colors.outlineVariant)
        val safeDrawing = WindowInsets.safeDrawing
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                .layout { measurable, constraints ->
                    // Reserve top clearance without changing the sheet's bottom anchor.
                    val clearance = safeDrawing.getTop(this) + 24.dp.roundToPx()
                    val maxHeight = minOf(520.dp.roundToPx(), constraints.maxHeight - clearance)
                        .coerceAtLeast(constraints.minHeight)
                    val body = measurable.measure(constraints.copy(maxHeight = maxHeight))
                    layout(body.width, body.height) { body.placeRelative(0, 0) }
                }
                .verticalScroll(scrollState).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}
