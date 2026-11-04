package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.MessageAcknowledgment
import com.luofelix.laninbox.core.network.MessageJson
import com.luofelix.laninbox.core.network.MessageRequest
import com.luofelix.laninbox.feature.send.DeliveryResult
import com.luofelix.laninbox.feature.send.DevelopmentMessageSender
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.content.TextContent
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SharedLogicDesktopTest {

    @Test
    fun reusesMessageIdAfterTimeoutAndChangesItForANewDraft() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val requests = mutableListOf<MessageRequest>()
        val sender = DevelopmentMessageSender(HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    val message = MessageJson.decodeFromString<MessageRequest>((request.body as TextContent).text)
                    requests.add(message)
                    if (requests.size == 1) throw SocketTimeoutException("Simulated lost acknowledgment")
                    respond(MessageJson.encodeToString(MessageAcknowledgment(message.messageId)))
                }
            }
        })
        try {
            val model = AppViewModel("Original")
            model.editAddress("192.168.1.8")
            model.send("phone", sender)
            advanceUntilIdle()
            assertEquals(DeliveryResult.Timeout, model.uiState.value.result)
            model.send("phone", sender)
            advanceUntilIdle()
            assertEquals(DeliveryResult.Delivered, model.uiState.value.result)
            assertEquals(requests[0], requests[1])
            model.editDraft("New draft")
            model.send("phone", sender)
            advanceUntilIdle()
            assertNotEquals(requests[0].messageId, requests[2].messageId)
        } finally {
            sender.close()
            Dispatchers.resetMain()
        }
    }
}
