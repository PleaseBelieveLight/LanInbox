package com.luofelix.laninbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luofelix.laninbox.core.network.MessageKind
import com.luofelix.laninbox.core.network.MessageRequest
import com.luofelix.laninbox.feature.send.DeliveryResult
import com.luofelix.laninbox.feature.send.MessageSender
import com.luofelix.laninbox.feature.send.developmentEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.tab_inbox
import laninbox.shared.generated.resources.tab_send
import org.jetbrains.compose.resources.StringResource

internal enum class AppDestination(val label: StringResource) {
    Send(Res.string.tab_send),
    Inbox(Res.string.tab_inbox),
}

internal data class AppUiState(
    val destination: AppDestination = AppDestination.Send,
    val address: String = "",
    val draft: String = "",
    val kind: MessageKind = MessageKind.Text,
    val sending: Boolean = false,
    val result: DeliveryResult? = null,
)

internal class AppViewModel(initialDraft: String) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AppUiState(draft = initialDraft))
    val uiState = mutableUiState.asStateFlow()
    private var pending: MessageRequest? = null

    fun select(destination: AppDestination) {
        mutableUiState.update { it.copy(destination = destination) }
    }

    fun editDraft(draft: String) {
        edit { it.copy(draft = draft) }
    }

    fun editAddress(address: String) {
        edit { it.copy(address = address) }
    }

    fun selectKind(kind: MessageKind) {
        edit { it.copy(kind = kind) }
    }

    private fun edit(transform: (AppUiState) -> AppUiState) {
        if (uiState.value.sending) return
        pending = null
        mutableUiState.update { transform(it).copy(result = null) }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun send(senderId: String, sender: MessageSender) {
        val state = uiState.value
        if (state.sending) return
        val request = pending ?: MessageRequest(
            senderId = senderId,
            messageId = Uuid.random().toString(),
            kind = state.kind,
            content = state.draft,
        )
        if (!request.isValid() || developmentEndpoint(state.address) == null) {
            mutableUiState.update { it.copy(result = DeliveryResult.Rejected) }
            return
        }
        pending = request
        mutableUiState.update { it.copy(sending = true, result = null) }
        viewModelScope.launch {
            try {
                val result = sender.send(state.address, request)
                if (result == DeliveryResult.Delivered) pending = null
                mutableUiState.update {
                    it.copy(
                        result = result,
                        draft = if (result == DeliveryResult.Delivered) "" else it.draft,
                    )
                }
            } finally {
                mutableUiState.update { it.copy(sending = false) }
            }
        }
    }
}
