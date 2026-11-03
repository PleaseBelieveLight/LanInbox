package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.MessageKind
import com.luofelix.laninbox.core.network.MessageRequest
import com.luofelix.laninbox.feature.inbox.InboxMessage
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class DesktopInbox(private val file: Path) {
    // Serialize writes and snapshot publication so concurrent requests cannot publish stale history.
    private val mutex = Mutex()
    private val currentMessages = MutableStateFlow<List<InboxMessage>>(emptyList())
    val messages = currentMessages.asStateFlow()

    suspend fun initialize() = access { connection ->
        connection.createStatement().use { statement ->
            val version = statement.executeQuery("PRAGMA user_version").use {
                it.next()
                it.getInt(1)
            }
            check(version in 0..1) { "Unsupported inbox schema version" }
            if (version == 0) {
                // Commit the schema and its version together so initialization can be retried.
                connection.autoCommit = false
                statement.executeUpdate(
                    """
                    CREATE TABLE inbox (
                        id INTEGER PRIMARY KEY,
                        sender_id TEXT NOT NULL,
                        message_id TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        content TEXT NOT NULL,
                        UNIQUE(sender_id, message_id)
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate("PRAGMA user_version = 1")
                connection.commit()
                connection.autoCommit = true
            }
        }
        refresh(connection)
    }

    /**
     * Returns true after inserting the message or finding an identical saved message.
     * Returns false if the same sender and message ID already identify different content or kind.
     * Storage failures propagate to the caller.
     */
    suspend fun save(message: MessageRequest): Boolean = access { connection ->
        val inserted = connection.prepareStatement(
            "INSERT INTO inbox(sender_id, message_id, kind, content) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(sender_id, message_id) DO NOTHING",
        ).use { statement ->
            statement.setString(1, message.senderId)
            statement.setString(2, message.messageId)
            statement.setString(3, message.kind.name)
            statement.setString(4, message.content)
            statement.executeUpdate() == 1
        }
        val matches = inserted || connection.prepareStatement(
            "SELECT kind, content FROM inbox WHERE sender_id = ? AND message_id = ?",
        ).use { statement ->
            statement.setString(1, message.senderId)
            statement.setString(2, message.messageId)
            statement.executeQuery().use { rows ->
                rows.next() && rows.getString(1) == message.kind.name &&
                    rows.getString(2) == message.content
            }
        }
        if (matches) refresh(connection)
        matches
    }

    private suspend fun <T> access(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            Files.createDirectories(file.toAbsolutePath().parent)
            DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use(block)
        }
    }

    private fun refresh(connection: Connection) {
        currentMessages.value = connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT sender_id, message_id, kind, content FROM inbox ORDER BY id DESC LIMIT 100",
            ).use { rows ->
                buildList {
                    while (rows.next()) {
                        add(InboxMessage(MessageRequest(
                            senderId = rows.getString(1),
                            messageId = rows.getString(2),
                            kind = MessageKind.valueOf(rows.getString(3)),
                            content = rows.getString(4),
                        )))
                    }
                }
            }
        }
    }
}
