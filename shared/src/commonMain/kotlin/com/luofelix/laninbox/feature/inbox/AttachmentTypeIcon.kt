package com.luofelix.laninbox.feature.inbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.AppIcons
import com.luofelix.laninbox.core.network.AttachmentInfo
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal fun AttachmentInfo.typeIcon(): ImageVector = when (fileKind()) {
    AttachmentFileKind.Image -> AppIcons.Image
    AttachmentFileKind.Video -> AppIcons.Video
    AttachmentFileKind.Audio -> AppIcons.Music
    AttachmentFileKind.Text -> AppIcons.Document
    AttachmentFileKind.Code -> AppIcons.Code
    AttachmentFileKind.Pdf -> AppIcons.Pdf
    AttachmentFileKind.Document -> AppIcons.Word
    AttachmentFileKind.Spreadsheet -> AppIcons.Spreadsheet
    AttachmentFileKind.Presentation -> AppIcons.Presentation
    AttachmentFileKind.Archive -> AppIcons.Archive
    AttachmentFileKind.File -> AppIcons.File
}

@Composable
internal fun AttachmentInfo.typeLabel(): String = stringResource(
    when (fileKind()) {
        AttachmentFileKind.Image -> Res.string.attachment_pick_image
        AttachmentFileKind.Video -> Res.string.attachment_type_video
        AttachmentFileKind.Audio -> Res.string.attachment_type_audio
        AttachmentFileKind.Text -> Res.string.kind_text
        AttachmentFileKind.Code -> Res.string.kind_code
        AttachmentFileKind.Pdf -> Res.string.attachment_type_pdf
        AttachmentFileKind.Document -> Res.string.attachment_pick_document
        AttachmentFileKind.Spreadsheet -> Res.string.attachment_type_spreadsheet
        AttachmentFileKind.Presentation -> Res.string.attachment_type_presentation
        AttachmentFileKind.Archive -> Res.string.attachment_type_archive
        AttachmentFileKind.File -> Res.string.attachment_pick_file
    },
)

@Composable
internal fun AttachmentTypeIcon(attachment: AttachmentInfo) {
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (attachment.fileKind()) {
        AttachmentFileKind.Pdf -> colors.errorContainer to colors.onErrorContainer
        AttachmentFileKind.Audio, AttachmentFileKind.Presentation -> colors.tertiaryContainer to colors.onTertiaryContainer
        AttachmentFileKind.Spreadsheet, AttachmentFileKind.Archive -> colors.secondaryContainer to colors.onSecondaryContainer
        AttachmentFileKind.Text, AttachmentFileKind.Code, AttachmentFileKind.File -> colors.surfaceVariant to colors.onSurfaceVariant
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
    Surface(shape = RoundedCornerShape(12.dp), color = background, contentColor = foreground) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Icon(attachment.typeIcon(), null, Modifier.size(20.dp))
        }
    }
}
