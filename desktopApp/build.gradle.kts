import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val nativeAccessArgument = "--enable-native-access=ALL-UNNAMED"

// IDE-injected Hot Reload tasks launch a separate application JVM.
tasks.withType<JavaExec>().configureEach {
    jvmArgs(nativeAccessArgument)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.material3)
    implementation(libs.compose.components.resources)
    implementation(libs.ktor.server.netty)
    implementation(libs.serialization.json)
    implementation(libs.sqlite.jdbc)
    implementation(libs.ktor.tls.certificates)
    implementation(libs.jmdns)
    implementation(libs.zxing.core)
    implementation(libs.jna.platform)
    // Application diagnostics are emitted through AppLogger.
    runtimeOnly(libs.slf4j.nop)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.ktor.server.test)
}

compose.desktop {
    application {
        mainClass = "com.luofelix.laninbox.MainKt"
        jvmArgs += nativeAccessArgument

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            modules("java.naming", "java.prefs", "java.sql", "jdk.crypto.ec", "jdk.unsupported")
            packageName = "LanInbox"
            packageVersion = rootProject.extra["laninbox.baseVersion"] as String
            windows {
                msiPackageVersion = rootProject.extra["laninbox.windowsVersion"] as String
                // Keep this identity constant so later installers can upgrade an existing installation.
                upgradeUuid = "4c367b38-a4c2-4eaf-8a9a-65a0d83a8532"
                iconFile.set(project.file("src/main/packaging/laninbox.ico"))
            }
        }
    }
}
