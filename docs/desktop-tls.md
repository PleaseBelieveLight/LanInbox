# Desktop TLS Identity Probe

The desktop HTTPS receiver defaults to `127.0.0.1:8443`. `GET /v1/identity` returns
`sha256/<base64>`, where the digest is SHA-256 over the leaf certificate's DER-encoded
SubjectPublicKeyInfo. Copy the fingerprint directly from the desktop app for pairing. Android
checks both the pinned public key and the address in the certificate during the TLS handshake.

## Run the probe

Generate a local PKCS#12 keystore. Keep the keystore and its password outside version control.

```powershell
$identity = Join-Path $env:LOCALAPPDATA 'LanInbox/identity.p12'
New-Item -ItemType Directory -Force (Split-Path $identity) | Out-Null
keytool -genkeypair -storetype PKCS12 -keystore $identity -alias laninbox -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 365 -dname 'CN=LanInbox' -ext 'SAN=IP:127.0.0.1'
keytool -exportcert -rfc -keystore $identity -alias laninbox -file "$identity.pem"
```

In PowerShell 7, start the desktop app with the provisioned identity.

```powershell
Remove-Item Env:LANINBOX_DEV_RECEIVER -ErrorAction SilentlyContinue
$env:LANINBOX_TLS_KEYSTORE = $identity
$env:LANINBOX_TLS_PASSWORD = Read-Host 'Keystore password' -MaskInput
try {
    .\gradlew.bat :desktopApp:run
} finally {
    Remove-Item Env:LANINBOX_TLS_PASSWORD, Env:LANINBOX_TLS_KEYSTORE
}
```

In a second terminal, pass the exported certificate to curl.

```powershell
$certificate = Join-Path $env:LOCALAPPDATA 'LanInbox/identity.p12.pem'
curl.exe --cacert $certificate https://127.0.0.1:8443/v1/identity
```

The HTTPS listener cannot run alongside the cleartext development receiver. It supports
authenticated text and attachment transfers in both directions.

## Connect Android over the LAN

1. Generate a separate keystore using the steps above. Replace the certificate's SAN argument
   with `'SAN=IP:127.0.0.1,IP:192.168.1.25'`, using the actual private IPv4 address of the PC.
2. Set `$env:LANINBOX_TLS_BIND = '192.168.1.25'` before running the desktop app with that
   keystore. Allow TCP 8443 on the Windows private network profile if needed.
3. Select **Create pairing invitation** on Windows. Enter the address, fingerprint, and
   invitation shown on the PC into Android. Grant local network access if prompted, then retry.
4. Send a text, link, code snippet, file, or image from either app. Keep Android in the foreground
   when receiving. Both inboxes support saving files and previewing supported images.

When the Windows IP changes, issue a certificate that covers the new address and pair again.
The development receiver keeps one pairing session in memory and one pending outgoing delivery.
Restarting Windows LanInbox discards pending deliveries and pairing credentials. Received
items are kept on disk.

## Exercise local pairing

Select **Create pairing invitation** in the desktop window. Send the displayed code as
`{"code":"<invitation>"}` in a JSON body to `POST /v1/pairing` over the trusted TLS connection.
The code expires after two minutes and can be redeemed once. Creating another invitation
invalidates the previous one. A failed or replayed redemption returns HTTP 400 with
`invalid_pairing_invitation`.

Successful redemption returns HTTP 200 and a JSON `credential`. Send it in the
`Authorization: Bearer <credential>` header to `GET /v1/pairing/session`. A valid credential
returns HTTP 204. A missing or invalid credential returns HTTP 401. The desktop app holds one
credential in memory. Successful re-pairing replaces it. **Clear invitation and revoke session**
or restarting the app invalidates it. Keep the invitation and credential out of URLs, shell
history, and logs.

Run `./gradlew :desktopApp:test --tests '*DesktopIdentityServerTest'` to check trusted TLS,
rejection of an untrusted certificate, the fingerprint of the handshake key, and route isolation.
Run `./gradlew :desktopApp:test --tests '*PairingRoutesTest'` to check expiration, concurrent
redemption, and revocation.
