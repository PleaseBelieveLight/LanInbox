# Desktop HTTPS Pairing

On Windows, the app starts the HTTPS receiver on port `8443` and advertises
`_laninbox._tcp.local.` on private IPv4 interfaces. Windows DPAPI protects the certificate key
and the saved pairing verifier. Android Keystore protects its paired device record.

`GET /v1/identity` returns
`sha256/<base64>`, where the digest is SHA-256 over the leaf certificate's DER-encoded
SubjectPublicKeyInfo. Android uses the desktop public key from the QR code or manually entered
fingerprint as its trust anchor during the handshake. Discovery only supplies a candidate
address. A device advertising a copied fingerprint still needs the corresponding private key.

## Run the probe

To run an isolated loopback probe, generate a local PKCS#12 keystore. Keep the keystore and
its password outside version control.

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

1. Launch the Windows app in its default mode. Use the same LAN on both devices. Windows may
   prompt for access on the private network profile. TCP 8443 carries transfers and UDP 5353
   carries discovery.
2. Select **Create pairing invitation** on Windows. Scan the QR in LanInbox on Android or enter
   the displayed address, fingerprint, and invitation. Grant local network access if prompted.
3. Send a text, link, code snippet, file, or image from either app. Keep Android in the foreground
   when receiving. Both inboxes support saving files and previewing supported images.

When the Windows address changes, Android uses discovery to find the saved fingerprint at the
new address. If multicast is filtered, enter the new Windows address and select
**Reconnect to paired computer**. A network that isolates clients can also block direct TCP
connections. The Windows app remembers one paired phone and keeps one outgoing delivery queued
until acknowledgment. Received items are kept on disk. Pending outgoing deliveries are discarded
on desktop shutdown.

## Exercise local pairing

Select **Create pairing invitation** in the desktop window. Send the displayed code as
`{"code":"<invitation>"}` in a JSON body to `POST /v1/pairing` over the trusted TLS connection.
The code expires after two minutes and can be redeemed once. Creating another invitation
invalidates the previous one. A failed or replayed redemption returns HTTP 400 with
`invalid_pairing_invitation`.

Successful redemption returns HTTP 200 and a JSON `credential`. Send it in the
`Authorization: Bearer <credential>` header to `GET /v1/pairing/session`. A valid credential
returns HTTP 204. A missing or invalid credential returns HTTP 401. Successful re-pairing
replaces the previous credential. **Clear invitation and revoke session** deletes its verifier.
Keep the invitation and credential out of URLs, shell history, and logs.

Run `./gradlew :desktopApp:test --tests '*DesktopIdentityServerTest'` to check trusted TLS,
rejection of an untrusted certificate, the fingerprint of the handshake key, and route isolation.
Run `./gradlew :desktopApp:test --tests '*PairingRoutesTest'` to check expiration, concurrent
redemption, and revocation.
