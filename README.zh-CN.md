# LanInbox · 局域网快传

[English](README.md)

[桌面 TLS 开发联调](docs/desktop-tls.md)

LanInbox 是一款 Kotlin Multiplatform 工具，可以在 Android 和 Windows 之间通过局域网互传文字、链接、代码、
文件和图片。共享 Compose 界面和协议代码位于 `shared`，两端应用入口分别位于 `androidApp` 和 `desktopApp`。

## 双向 HTTPS 快传

1. 按[桌面 TLS 配置](docs/desktop-tls.md#connect-android-over-the-lan)生成包含 Windows 局域网 IPv4 地址的
   证书，并设置 `LANINBOX_TLS_BIND` 启动桌面端。
2. 在 Windows 生成配对邀请。把桌面窗口显示的地址、证书公钥指纹和邀请填入 Android 发送页。系统要求局域网
   权限时，授权后重试。
3. 两端都可以发送文字或选择文件、图片。Windows 向手机发送时，保持 Android 应用在前台。
4. 在收件箱预览支持的图片，或用**另存为**导出文件。传输中断后重试会从文件开头重新传输。

## 联调文本快传

1. 在 Android 真机运行 Debug 版，让手机和 Windows 连接到同一局域网。
2. 在 Windows PowerShell 启用开发接收端。

   ```powershell
   $env:LANINBOX_DEV_RECEIVER = '1'
   .\gradlew.bat :desktopApp:run
   ```

3. 在手机输入 Windows 的局域网 IPv4 地址，默认端口为 `8765`。粘贴文本或通过其他应用分享文本后，点击
   **发送**。如遇局域网权限提示，授权后再次点击发送。
4. 在 Windows 收件箱复制收到的内容。超时时保留原草稿和地址直接重试。重启接收端可以检查历史是否保留。

开发传输未经认证，也未加密。仅在可信局域网启用接收端。Windows 防火墙可能需要在专用网络配置下允许 TCP
`8765` 入站。收件箱显示最新 100 条，开发历史存放在 `%LOCALAPPDATA%/LanInbox/development-inbox.sqlite`。

## 构建和测试

- Android Debug APK 使用 `.\gradlew.bat :androidApp:assembleDebug` 构建。
- 桌面应用使用 `.\gradlew.bat :desktopApp:run` 启动，也可运行
  `.\gradlew.bat :desktopApp:hotRun --auto` 使用 Compose 热重载。
- 使用 `.\gradlew.bat :shared:testAndroidHostTest :shared:jvmTest :desktopApp:test` 运行主机单测和桌面接收端测试。
