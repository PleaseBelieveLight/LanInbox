package com.luofelix.laninbox.feature.inbox

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.pairing_invitation_hint
import laninbox.shared.generated.resources.pairing_issue_invitation
import laninbox.shared.generated.resources.pairing_revoke
import org.jetbrains.compose.resources.stringResource

@Composable
fun PairingPanel(invitation: String?, onIssue: () -> Unit, onRevoke: () -> Unit) {
    OutlinedButton(onClick = onIssue) {
        Text(stringResource(Res.string.pairing_issue_invitation))
    }
    invitation?.let {
        Text(stringResource(Res.string.pairing_invitation_hint))
        SelectionContainer { Text(it) }
    }
    OutlinedButton(onClick = onRevoke) {
        Text(stringResource(Res.string.pairing_revoke))
    }
}
