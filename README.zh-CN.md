# LanInbox · 局域网快传

[English](README.md)

[桌面 TLS 开发联调](docs/desktop-tls.md)

LanInbox 是一款 Kotlin Multiplatform 工具，可以在 Android 和 Windows 之间通过局域网互传文字、链接、代码、
文件和图片。共享 Compose 界面和协议代码位于 `shared`，两端应用入口分别位于 `androidApp` 和 `desktopApp`。

## 双向 HTTPS 快传

1. 启动 Windows 版局域网快传。应用会创建本机身份并启动 HTTPS 接收端。
2. 让 Android 和电脑连入同一局域网。在 Windows 生成配对邀请，在 Android 扫描二维码。也可以手动输入
   Windows 地址、公钥指纹和邀请。系统要求局域网权限时，授权后重试。
3. 两端都可以发送文字或选择文件、图片。Windows 向手机发送时，保持 Android 应用在前台。
4. 在收件箱复制文字、预览图片、另存文件或清理历史。传输中断后重试会从文件开头重新传输。

配对在两端重启后保留。电脑换 IP 后，Android 会通过局域网发现重新定位原设备。具体见[连接与故障排查](docs/desktop-tls.md)。

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
- 安装 JDK 21 和 WiX 3.14.1 后，使用 `.\gradlew.bat :desktopApp:packageMsi` 构建 Windows 安装包。
  见[发布验收清单](docs/release-verification.md)。
