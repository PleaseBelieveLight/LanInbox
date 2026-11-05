package com.luofelix.laninbox.feature.send

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.core.network.MAX_MESSAGE_BYTES
import com.luofelix.laninbox.core.network.MessageKind
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

data class SelectedFile(val name: String, val size: Long? = null)

private val webLink = Regex("""https?://[^\s/]+\S*""", RegexOption.IGNORE_CASE)

fun draftKind(draft: String, asCode: Boolean): MessageKind = when {
    asCode -> MessageKind.Code
    webLink.matches(draft.trim()) -> MessageKind.Link
    else -> MessageKind.Text
}

@Composable
fun SendComposer(
    title: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    asCode: Boolean,
    onCodeChange: (Boolean) -> Unit,
    file: SelectedFile?,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    onSubmit: () -> Unit,
    connectRequired: Boolean,
    enabled: Boolean = true,
    failed: Boolean = false,
    status: @Composable () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    val tooLong = remember(draft) { draft.encodeToByteArray().size > MAX_MESSAGE_BYTES }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (file == null) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    placeholder = { Text(stringResource(Res.string.send_draft)) },
                    minLines = 2,
                    maxLines = 4,
                    isError = tooLong,
                    supportingText = if (tooLong) {
                        { Text(stringResource(Res.string.draft_too_long)) }
                    } else null,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        file.size?.let {
                            Text(fileSizeLabel(it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (enabled) {
                        TextButton(onClick = onRemove) { Text(stringResource(Res.string.attachment_remove)) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onChoose, enabled = enabled) {
                    Text(stringResource(Res.string.attachment_choose))
                }
                if (file == null) {
                    Column(Modifier.weight(1f)) {
                        TextButton(onClick = { menuOpen = true }, enabled = enabled) {
                            Text(stringResource(if (asCode) Res.string.kind_code else Res.string.send_options), maxLines = 1)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (asCode) Res.string.send_as_text else Res.string.send_as_code)) },
                                onClick = { onCodeChange(!asCode); menuOpen = false },
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = onSubmit,
                    enabled = enabled && (file != null || (draft.isNotBlank() && !tooLong)),
                ) {
                    Text(
                        stringResource(
                            when {
                                connectRequired -> Res.string.connect_to_send
                                failed -> Res.string.attachment_retry
                                else -> Res.string.tab_send
                            },
                        ),
                    )
                }
            }
            status()
        }
    }
}

@Composable
internal fun fileSizeLabel(bytes: Long): String {
    val unit = when {
        bytes >= 1_048_576L -> 1_048_576L
        bytes >= 1_024L -> 1_024L
        else -> return stringResource(Res.string.attachment_bytes, bytes)
    }
    val tenths = (bytes * 10 / unit)
    return stringResource(
        if (unit == 1_048_576L) Res.string.attachment_megabytes else Res.string.attachment_kilobytes,
        tenths / 10,
        tenths % 10,
    )
}
