package com.luofelix.laninbox

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val draft: String = "",
)

internal class AppViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(AppUiState())
    val uiState = mutableUiState.asStateFlow()

    fun select(destination: AppDestination) {
        mutableUiState.update { it.copy(destination = destination) }
    }

    fun editDraft(draft: String) {
        mutableUiState.update { it.copy(draft = draft) }
    }
}
