package com.luofelix.laninbox.feature.inbox

import com.luofelix.laninbox.core.network.MessageRequest

data class InboxMessage(val request: MessageRequest)

enum class ReceiverStatus {
    Disabled, Starting, Listening, IdentityListening, SecureListening, Foreground, Failed,
}
