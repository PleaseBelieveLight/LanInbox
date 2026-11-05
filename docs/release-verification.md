# Release Verification

GitHub Actions builds the Android Debug APK and Windows MSI, runs Android host, shared JVM, and
desktop tests, and uploads the builds and test reports. Use JDK 21 and set `WIX_PATH` to the
WiX 3.14.1 directory containing `candle.exe` and `light.exe` for a local Windows installer build.

```powershell
.\gradlew.bat :androidApp:assembleDebug :shared:testAndroidHostTest :shared:jvmTest :desktopApp:test
.\gradlew.bat :desktopApp:packageMsi
```

Run the device checks on Windows and a physical Android phone before publishing.

- Launch and exit the installed Windows app. Confirm the receiver starts and port 8443 is
  released on exit.
- Pair by scanning the QR with Android held sideways and verify the scanner stays in portrait
  on the phone. Test the toolbar and system back actions from a text and file draft. Deny camera
  or local-network access, then grant it and retry. Test discovery and manual entry on the
  Windows private firewall profile.
- Restart both apps and reconnect. Change the Windows address and rediscover the same identity.
  Reject a wrong fingerprint, an expired invitation, and a revoked credential.
- Share a URL from Android and send multiline text in both directions. Copy it exactly. Lose
  the first acknowledgment and retry. Delete or clear history and verify replay stays hidden.
- Confirm a selected file before sending it. Test a file near the size limit, image preview,
  export, interruption and retry, and a receiver with insufficient disk space. Put Android in
  the background before sending from Windows and confirm the delivery waits until Android
  returns. Resize the Windows window during a transfer and check both theme modes.
