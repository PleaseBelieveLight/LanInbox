package com.luofelix.laninbox

import com.luofelix.laninbox.core.logging.AppLogger
import com.luofelix.laninbox.core.network.MAX_REQUEST_BYTES
import com.luofelix.laninbox.core.network.MESSAGE_ROUTE
import com.luofelix.laninbox.core.network.MessageAcknowledgment
import com.luofelix.laninbox.core.network.MessageJson
import com.luofelix.laninbox.core.network.MessageKind
import com.luofelix.laninbox.core.network.MessageRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class MessageRoutesTest {
    @Test
    fun concurrentRetriesAcknowledgeOnePersistedMessage() {
        val directory = Files.createTempDirectory("laninbox-test")
        val file = directory.resolve("inbox.sqlite")
        try {
            testApplication {
                val inbox = DesktopInbox(file)
                inbox.initialize()
                application { messageRoutes(inbox, AppLogger { }) }
                val message = MessageRequest(
                    "sender",
                    "message",
                    MessageKind.Code,
                    "中文 https://example.org\n    println(\"Hello\")",
                )

                suspend fun submit(body: String) = client.post(MESSAGE_ROUTE) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

                val encoded = MessageJson.encodeToString(message)
                val responses = coroutineScope {
                    List(3) { async { submit(encoded) } }.awaitAll()
                }
                for (response in responses) {
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(
                        MessageAcknowledgment(message.messageId),
                        MessageJson.decodeFromString<MessageAcknowledgment>(response.bodyAsText()),
                    )
                }
                assertEquals(HttpStatusCode.Conflict, submit(
                    MessageJson.encodeToString(message.copy(content = "Different content")),
                ).status)
                assertEquals(HttpStatusCode.BadRequest, submit("{}").status)
                assertEquals(HttpStatusCode.PayloadTooLarge, submit("x".repeat(MAX_REQUEST_BYTES + 1)).status)

                val reopened = DesktopInbox(file)
                reopened.initialize()
                assertEquals(listOf(message), reopened.messages.value.map { it.request })
            }
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
