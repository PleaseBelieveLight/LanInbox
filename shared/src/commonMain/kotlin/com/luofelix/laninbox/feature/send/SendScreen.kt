package com.luofelix.laninbox.feature.send

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.AppIcons
import com.luofelix.laninbox.AttachmentAction
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

/** Applies the same content and busy-state checks to the send button and keyboard shortcut. */
internal fun canSubmitDraft(draft: String, file: SelectedFile?, enabled: Boolean): Boolean =
    enabled && (file != null || (draft.isNotBlank() && draft.encodeToByteArray().size <= MAX_MESSAGE_BYTES))

/** When [fillAvailableHeight] is true, the editor grows within bounded parent constraints. */
@Composable
fun SendComposer(
    title: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    file: SelectedFile?,
    onChooseFile: () -> Unit,
    onChooseImage: () -> Unit,
    onRemove: () -> Unit,
    onSubmit: () -> Unit,
    connectRequired: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    failed: Boolean = false,
    highlighted: Boolean = false,
    fillAvailableHeight: Boolean = false,
    hint: @Composable () -> Unit = {},
    status: @Composable () -> Unit = {},
) {
    val tooLong = remember(draft) { draft.encodeToByteArray().size > MAX_MESSAGE_BYTES }
    val submitEnabled = remember(draft, file, enabled) { canSubmitDraft(draft, file, enabled) }
    Surface(
        modifier = modifier.fillMaxWidth().onPreviewKeyEvent { event ->
            if (event.isCtrlPressed && event.key == Key.Enter) {
                if (event.type == KeyEventType.KeyDown && submitEnabled) onSubmit()
                true
            } else false
        },
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (highlighted) 2.dp else 1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        BoxWithConstraints {
            val expanded = fillAvailableHeight && constraints.hasBoundedHeight
            val compact = maxHeight < 360.dp
            val labeledActions = maxWidth >= 400.dp || (!connectRequired && maxWidth >= 320.dp)
            val showShortcut = maxWidth >= 420.dp
            Column(
                modifier = (if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .padding(if (compact) 12.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (!compact) {
                        Text(
                            stringResource(Res.string.send_content_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Keep only the editor flexible so attachment actions and transfer feedback stay visible.
                val editorModifier = Modifier.fillMaxWidth().then(if (expanded) Modifier.weight(1f) else Modifier)
                if (file == null) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        modifier = editorModifier,
                        enabled = enabled,
                        placeholder = { Text(stringResource(Res.string.send_draft)) },
                        minLines = if (expanded) 1 else 2,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            disabledContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        isError = tooLong,
                        supportingText = if (tooLong) {
                            { Text(stringResource(Res.string.draft_too_long)) }
                        } else null,
                    )
                } else {
                    Surface(
                        modifier = editorModifier,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Box(
                            modifier = (if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    AppIcons.File,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (compact) 24.dp else 40.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                file.size?.let {
                                    Text(
                                        fileSizeLabel(it),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = onRemove, enabled = enabled) {
                                    Text(stringResource(Res.string.attachment_remove))
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hint()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (labeledActions) {
                            TextButton(onClick = onChooseFile, enabled = enabled) {
                                Icon(AppIcons.Folder, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(Res.string.attachment_pick_file))
                            }
                            TextButton(onClick = onChooseImage, enabled = enabled) {
                                Icon(AppIcons.Image, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(Res.string.attachment_pick_image))
                            }
                        } else {
                            AttachmentAction(AppIcons.Folder, stringResource(Res.string.attachment_pick_file), enabled, onChooseFile)
                            AttachmentAction(AppIcons.Image, stringResource(Res.string.attachment_pick_image), enabled, onChooseImage)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = onSubmit, enabled = submitEnabled) {
                            Icon(if (failed && !connectRequired) AppIcons.Retry else AppIcons.Send, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) { status() }
                        if (showShortcut) {
                            Text(
                                stringResource(Res.string.send_shortcut),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
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
