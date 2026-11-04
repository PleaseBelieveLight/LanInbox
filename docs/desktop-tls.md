# Desktop TLS Identity Probe

The Phase C probe exposes `GET /v1/identity` on `127.0.0.1:8443`. Its response is
`sha256/<base64>`, where the digest is SHA-256 over the leaf certificate's DER-encoded
SubjectPublicKeyInfo. The response is diagnostic. Future pairing clients must verify the peer's
public key during the TLS handshake against the fingerprint obtained from the desktop pairing QR
code.

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

The loopback HTTPS listener also supports local pairing and cannot run alongside the cleartext
development receiver. It does not expose message submission.

## Exercise local pairing

Select **Create local pairing invitation** in the desktop window. Send the displayed code as
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
