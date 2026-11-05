# Development Message Protocol

Android Debug sends `POST /v1/messages` with a UTF-8 JSON body to a private IPv4 address.
Windows listens on port `8765` when `LANINBOX_DEV_RECEIVER=1`. The development connection
uses unauthenticated HTTP and should be used on a trusted LAN.

## Request and acknowledgment

```json
{
  "senderId": "android-1",
  "messageId": "message-1",
  "kind": "Code",
  "content": "val port = 8765"
}
```

- Use `Content-Type: application/json`. Each ID contains 1–64 ASCII letters, digits,
  underscores, or hyphens. Supported kinds are `Text`, `Link`, and `Code`.
- Content must contain a non-whitespace character and fit within 64 KiB of UTF-8.
  The full request may contain up to 394,240 bytes to allow for escaping and JSON fields.
- The receiver returns HTTP 200 with `{"messageId":"message-1"}` after saving the message
  or finding an identical saved message. The sender reports delivery only when the
  acknowledgment contains the original message ID.

## Retries and errors

Retry an unconfirmed delivery with the same destination, IDs, kind, and content.
The receiver keys records by `(senderId, messageId)`, so an identical retry after a lost
acknowledgment returns success without adding another row. Use a new message ID if the
payload changes. The Android app also starts a new delivery when the destination changes.

Application errors use a JSON body with a `code` field.

| HTTP status | Code                       | Meaning                                                             |
|-------------|----------------------------|---------------------------------------------------------------------|
| 400         | `invalid_message`          | The JSON, UTF-8 encoding, or message fields are invalid.            |
| 409         | `message_id_conflict`      | The saved kind or content differs. The original record is retained. |
| 413         | `request_too_large`        | The JSON body exceeds the request limit.                            |
| 415         | `unsupported_content_type` | The content type is incompatible with JSON.                         |
| 500         | `storage_failure`          | The receiver could not complete the storage operation.              |
