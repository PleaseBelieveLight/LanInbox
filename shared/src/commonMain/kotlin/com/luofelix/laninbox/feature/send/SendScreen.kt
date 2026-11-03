package com.luofelix.laninbox.feature.send

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.send_draft
import laninbox.shared.generated.resources.send_unavailable
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SendScreen(draft: String, onDraftChange: (String) -> Unit) {
    OutlinedTextField(
        value = draft,
        onValueChange = onDraftChange,
        label = { Text(stringResource(Res.string.send_draft)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 4,
    )
    Text(stringResource(Res.string.send_unavailable))
}
