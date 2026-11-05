# Release Verification

Daily verification builds an Android Debug APK and Windows MSI. The release workflow instead
builds a signed Android Release APK and Windows x64 MSI, runs Android host, shared JVM, and desktop
tests, and creates a draft with checksums, signer information, and build information. See the
[release guide](releasing.md) for signing, version counters, and tag-triggered drafts.

Use JDK 21 and set `WIX_PATH` to the WiX 3.14.1 directory containing `candle.exe` and `light.exe`
for a local Windows installer build. Run these checks manually when preparing a release:

```powershell
.\gradlew.bat :androidApp:assembleDebug :shared:testAndroidHostTest :shared:jvmTest :desktopApp:test
.\gradlew.bat :desktopApp:packageMsi
# Set the signing environment variables first; see the release guide.
.\gradlew.bat --no-configuration-cache :androidApp:assembleRelease
```

Download the exact draft assets and run the device checks on Windows and a physical Android
phone before publishing that draft. A successful CI run does not replace these device checks.

- Confirm the workflow succeeded and all five assets listed in the release guide are present.
  Check `BUILD-INFO.txt` against the intended tag and commit. Compare each downloaded file with
  its `SHA256SUMS.txt` entry, for example using `Get-FileHash -Algorithm SHA256` in PowerShell.
  Compare the APK signer certificate with the retained release certificate before distributing it.
- Install the signed APK and the MSI on clean devices. When a previous Release exists, install
  the new APK and MSI over it and confirm history, files, and pairing survive the upgrade.
  Confirm Android's version code and the MSI version are higher than the previous release.
  For the first release, check and document the one-time migration from existing Debug APKs
  or verification MSIs; do not use those builds as evidence that Release-to-Release upgrades work.

- Launch and exit the installed Windows app. Confirm the receiver starts and port 8443 is
  released on exit.
- Pair by scanning the QR with Android held sideways and verify the scanner stays in portrait
  on the phone. Test the toolbar and system back actions from a text and file draft. Deny camera
  or local-network access, then grant it and retry. Test discovery and manual entry on the
  Windows private firewall profile.
- On Android, check that the top bar and editor reach the screen edges in both themes and
  navigation modes. On a small screen with a large font, open the keyboard and verify the send
  button stays visible. Use the image, document, and file pickers, cancel each, and check that
  replacing or removing an attachment preserves the text draft.
- Restart both apps and reconnect. Change the Windows address and rediscover the same identity.
  Reject a wrong fingerprint, an expired invitation, and a revoked credential.
- Share a URL from Android and send multiline text in both directions. Copy it exactly. Lose
  the first acknowledgment and retry. Delete or clear history and verify replay stays hidden.
- Confirm a selected file before sending it. Test a file near the size limit, image preview,
  export, interruption and retry, and a receiver with insufficient disk space. Put Android in
  the background before sending from Windows and confirm the delivery waits until Android
  returns. Resize the Windows window during a transfer and check both theme modes.
