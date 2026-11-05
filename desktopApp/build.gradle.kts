import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.ktor.server.test)
}

compose.desktop {
    application {
        mainClass = "com.luofelix.laninbox.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            modules("java.naming", "java.prefs", "java.sql", "jdk.crypto.ec", "jdk.unsupported")
            packageName = "LanInbox"
            packageVersion = "1.0.0"
        }
    }
}
