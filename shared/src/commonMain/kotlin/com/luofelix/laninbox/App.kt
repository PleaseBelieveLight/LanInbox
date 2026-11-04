package com.luofelix.laninbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luofelix.laninbox.feature.inbox.InboxScreen
import com.luofelix.laninbox.feature.inbox.InboxMessage
import com.luofelix.laninbox.feature.inbox.ReceiverStatus
import com.luofelix.laninbox.feature.send.MessageSender
import com.luofelix.laninbox.feature.send.SendScreen
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.app_name
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun App(
    sender: MessageSender? = null,
    senderId: String = "",
    initialDraft: String = "",
    messages: List<InboxMessage> = emptyList(),
    receiverStatus: ReceiverStatus = ReceiverStatus.Disabled,
    inboxOnly: Boolean = false,
    inboxActions: @Composable () -> Unit = {},
) {
    val model = viewModel { AppViewModel(initialDraft) }
    val state by model.uiState.collectAsStateWithLifecycle()

    LanInboxTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.safeContentPadding()) {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp),
                )
                if (!inboxOnly) PrimaryTabRow(selectedTabIndex = state.destination.ordinal) {
                    AppDestination.entries.forEach { destination ->
                        Tab(
                            selected = state.destination == destination,
                            onClick = { model.select(destination) },
                            text = { Text(stringResource(destination.label)) },
                        )
                    }
                }
                if (inboxOnly || state.destination == AppDestination.Inbox) {
                    InboxScreen(messages, receiverStatus, inboxActions)
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SendScreen(
                            state = state,
                            onDraftChange = model::editDraft,
                            onAddressChange = model::editAddress,
                            onKindChange = model::selectKind,
                            onSend = sender?.let { { model.send(senderId, it) } },
                        )
                    }
                }
            }
        }
    }
}
