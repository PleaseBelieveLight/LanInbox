package com.luofelix.laninbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.feature.inbox.InboxMessage
import com.luofelix.laninbox.feature.inbox.InboxScreen
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.app_name
import org.jetbrains.compose.resources.stringResource

/**
 * Displays the desktop inbox beside a send workspace, stacking them in narrow windows.
 * @param composer Applies the supplied layout modifier to its root to share the available window space.
 */
@Composable
fun App(
    messages: List<InboxMessage> = emptyList(),
    attachments: List<AttachmentInfo> = emptyList(),
    previewAttachment: (suspend (AttachmentInfo) -> ImageBitmap?)? = null,
    exportAttachment: ((AttachmentInfo) -> Unit)? = null,
    deleteMessage: ((InboxMessage) -> Unit)? = null,
    deleteAttachment: ((AttachmentInfo) -> Unit)? = null,
    clearHistory: (() -> Unit)? = null,
    openAttachment: (suspend (AttachmentInfo) -> Boolean)? = null,
    localAttachmentPreview: (@Composable (AttachmentInfo, () -> Unit) -> Unit)? = null,
    attachmentReceivedDates: Map<String, String> = emptyMap(),
    device: @Composable () -> Unit = {},
    inboxStatus: @Composable () -> Unit = {},
    composer: @Composable (Modifier) -> Unit = {},
) {
    LanInboxTheme(desktopDialogs = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.safeContentPadding().imePadding()) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Res.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        device()
                    }
                }
                HorizontalDivider()
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                ) {
                    val inbox: @Composable (Modifier) -> Unit = { modifier ->
                        InboxScreen(
                            messages = messages,
                            attachments = attachments,
                            previewAttachment = previewAttachment,
                            exportAttachment = exportAttachment,
                            deleteMessage = deleteMessage,
                            deleteAttachment = deleteAttachment,
                            clearHistory = clearHistory,
                            openAttachment = openAttachment,
                            localAttachmentPreview = localAttachmentPreview,
                            attachmentReceivedDates = attachmentReceivedDates,
                            status = inboxStatus,
                            modifier = modifier,
                        )
                    }
                    if (maxWidth >= 700.dp) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            inbox(Modifier.weight(0.42f).fillMaxHeight())
                            composer(Modifier.weight(0.58f).fillMaxHeight())
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            inbox(Modifier.weight(1f))
                            composer(Modifier.weight(1.5f).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
