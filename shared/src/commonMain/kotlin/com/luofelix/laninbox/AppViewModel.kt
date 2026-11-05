package com.luofelix.laninbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luofelix.laninbox.core.network.MessageRequest
import com.luofelix.laninbox.feature.send.DeliveryResult
import com.luofelix.laninbox.feature.send.MessageSender
import com.luofelix.laninbox.feature.send.developmentEndpoint
import com.luofelix.laninbox.feature.send.draftKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

internal data class AppUiState(
    val draft: String = "",
    val sending: Boolean = false,
    val result: DeliveryResult? = null,
)

/** Keeps the text draft while the user connects and until the receiver confirms delivery. */
internal class AppViewModel(initialDraft: String) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AppUiState(draft = initialDraft))
    val uiState = mutableUiState.asStateFlow()

    // A lost acknowledgment can follow a successful save. Match a paired peer by identity across address changes.
    private var pending: Pair<String, MessageRequest>? = null

    fun editDraft(draft: String) {
        val state = uiState.value
        if (state.sending || state.draft == draft) return
        // Editing the draft starts a new logical delivery.
        pending = null
        mutableUiState.update { it.copy(draft = draft, result = null) }
    }

    fun clearFeedback() {
        mutableUiState.update { it.copy(result = null) }
    }

    fun send(senderId: String, sender: MessageSender, address: String, peerIdentity: String?) {
        val state = uiState.value
        if (state.sending) return
        val destination = peerIdentity ?: address
        val request = pending?.takeIf { it.first == destination }?.second ?: MessageRequest(
            senderId = senderId,
            messageId = Uuid.random().toString(),
            kind = draftKind(state.draft, asCode = false),
            content = state.draft,
        )
        if (!request.isValid() || (peerIdentity == null && developmentEndpoint(address) == null)) {
            mutableUiState.update { it.copy(result = DeliveryResult.Rejected) }
            return
        }
        pending = destination to request
        mutableUiState.update { it.copy(sending = true, result = null) }
        viewModelScope.launch {
            try {
                val result = sender.send(address, request)
                if (result == DeliveryResult.Delivered) pending = null
                mutableUiState.update {
                    if (result == DeliveryResult.Delivered) AppUiState(result = result)
                    else it.copy(result = result)
                }
            } finally {
                mutableUiState.update { it.copy(sending = false) }
            }
        }
    }
}
