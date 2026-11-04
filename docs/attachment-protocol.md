# Development Attachment Upload

The desktop HTTPS receiver accepts `PUT /v1/attachments/{id}` after
[pairing](desktop-tls.md#exercise-local-pairing). The client chooses a lowercase UUID as the
transfer ID and sends the raw file bytes.

| Header | Value |
| --- | --- |
| `Authorization` | `Bearer <credential>` from pairing |
| `Content-Type` | `application/octet-stream` |
| `X-LanInbox-Size` | File length in bytes, up to 104,857,600 bytes |
| `X-LanInbox-SHA256` | Lowercase hexadecimal SHA-256 of the raw body |
| `X-LanInbox-Name` | Optional Base64URL-encoded UTF-8 display filename |
| `X-LanInbox-Type` | Optional media type, such as `image/png` |

The receiver streams the body to staging storage, checks the length and digest, then publishes
the payload and its receipt together. HTTP 201 confirms a new attachment. HTTP 200 confirms a
verified retry with the same ID, length, and digest. Both return JSON with `id`, `size`, and
`sha256`. After a lost response or interrupted request, retry from the beginning with the same
ID and body. A different body requires a new ID.

| Status | Meaning |
| --- | --- |
| 400 | Invalid metadata, truncated body, extra bytes, or checksum mismatch |
| 401 | Missing, invalid, or revoked pairing credential |
| 408 | Upload exceeded the five-minute request budget |
| 409 | This ID already identifies a different verified attachment |
| 413 | Attachment exceeds the receiver's size limit |
| 415 | The request body uses an unsupported content type |
| 500 | Storage or body I/O failed |
| 503 | Both upload slots are occupied |

The receiver checks credentials when a request begins. Upload cancellation cleans up staged
files. Completed uploads live under `%LOCALAPPDATA%/LanInbox/development-attachments/<id>/payload`
on Windows. Storage paths are derived from the validated ID. The inbox uses the name and media
type for display, previews, and export.

## Windows to Android

The foreground Android client polls `GET /v1/outbox` with the pairing credential. It receives
HTTP 204 if nothing is queued or a JSON object with an `id` and either a `message` or
`attachment`. Text uses the [message protocol](message-protocol.md). Attachment metadata
includes `id`, `size`, `sha256`, `name`, and `mediaType`.

For attachments, the phone streams `GET /v1/outbox/{id}/payload` to temporary storage and
verifies its length and SHA-256. After saving a text or verified file, it calls
`POST /v1/outbox/{id}/ack`. Until acknowledgment, polling returns the same delivery and a
retry starts from byte zero. The phone retains received items by delivery ID, so a retry after
an acknowledgment loss does not create another inbox entry.

Both apps show byte progress for file transfers. Canceling a download on Android pauses polling
until the user taps **Retry**. Canceling a queued transfer on Windows removes that delivery.
