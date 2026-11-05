package com.luofelix.laninbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.core.network.MessageKind
import com.luofelix.laninbox.feature.inbox.InboxMessage
import com.luofelix.laninbox.feature.inbox.InboxScreen
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.app_name
import laninbox.shared.generated.resources.back
import org.jetbrains.compose.resources.stringResource

@Composable
fun AndroidHomeScreen(
    messages: List<InboxMessage> = emptyList(),
    attachments: List<AttachmentInfo> = emptyList(),
    previewAttachment: (suspend (AttachmentInfo) -> ImageBitmap?)? = null,
    exportAttachment: ((AttachmentInfo) -> Unit)? = null,
    deleteMessage: ((InboxMessage) -> Unit)? = null,
    deleteAttachment: ((AttachmentInfo) -> Unit)? = null,
    clearHistory: (() -> Unit)? = null,
    device: @Composable () -> Unit = {},
    inboxStatus: @Composable () -> Unit = {},
    composer: @Composable () -> Unit = {},
) {
    LanInboxTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            topBar = { AndroidTopBar(stringResource(Res.string.app_name), actions = { device() }) },
            bottomBar = {
                BoxWithConstraints(Modifier.fillMaxWidth().imePadding()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.72f),
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                    ) {
                        Column(
                            Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                            ),
                        ) { composer() }
                    }
                }
            },
        ) { padding ->
            InboxScreen(
                messages = messages,
                attachments = attachments,
                previewAttachment = previewAttachment,
                exportAttachment = exportAttachment,
                deleteMessage = deleteMessage,
                deleteAttachment = deleteAttachment,
                clearHistory = clearHistory,
                status = inboxStatus,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                messageIcon = { kind ->
                    InboxTypeIcon(
                        when (kind) {
                            MessageKind.Text -> AndroidIcons.Document
                            MessageKind.Link -> AndroidIcons.Link
                            MessageKind.Code -> AndroidIcons.Code
                        },
                    )
                },
                attachmentIcon = { attachment ->
                    InboxTypeIcon(if (attachment.mediaType.startsWith("image/")) AndroidIcons.Image else AndroidIcons.Document)
                },
            )
        }
    }
}

@Composable
internal fun AndroidTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.primaryContainer.copy(alpha = 0.55f).compositeOver(colors.surface),
        contentColor = colors.onSurface,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .heightIn(min = 64.dp)
                    .padding(start = if (onBack != null) 4.dp else 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(AndroidIcons.Back, contentDescription = stringResource(Res.string.back))
                    }
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                actions()
            }
            HorizontalDivider(color = colors.outlineVariant)
        }
    }
}

@Composable
fun AndroidDeviceButton(label: String, scan: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.widthIn(max = 164.dp).heightIn(min = 48.dp),
    ) {
        Icon(
            if (scan) AndroidIcons.Scan else AndroidIcons.Computer,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InboxTypeIcon(icon: ImageVector) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
