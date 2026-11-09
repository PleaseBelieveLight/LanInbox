package com.luofelix.laninbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.feature.inbox.InboxMessage
import com.luofelix.laninbox.feature.inbox.InboxScreen
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.app_name
import org.jetbrains.compose.resources.stringResource

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
    device: @Composable () -> Unit = {},
    inboxStatus: @Composable () -> Unit = {},
    composer: @Composable () -> Unit = {},
) {
    LanInboxTheme(desktopDialogs = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.safeContentPadding().imePadding()) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
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
                    modifier = Modifier.fillMaxSize().padding(16.dp),
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
                            status = inboxStatus,
                            modifier = modifier,
                        )
                    }
                    if (maxWidth >= 700.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            inbox(Modifier.weight(1.3f))
                            Column(
                                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            ) { composer() }
                        }
                    } else {
                        val composerHeight = maxHeight * 0.68f
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            inbox(Modifier.weight(1f))
                            CompactComposer(composerHeight, composer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactComposer(
    maxHeight: Dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState()),
    ) { content() }
}
