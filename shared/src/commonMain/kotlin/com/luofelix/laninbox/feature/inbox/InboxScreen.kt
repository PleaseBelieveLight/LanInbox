package com.luofelix.laninbox.feature.inbox

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.inbox_empty
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun InboxScreen() {
    Text(stringResource(Res.string.inbox_empty))
}
