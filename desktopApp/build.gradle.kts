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
    implementation(libs.ktor.server.netty)
    implementation(libs.serialization.json)
    implementation(libs.sqlite.jdbc)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.ktor.server.test)
    testImplementation(libs.ktor.tls.certificates)
}

compose.desktop {
    application {
        mainClass = "com.luofelix.laninbox.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.luofelix.laninbox"
            packageVersion = "1.0.0"
        }
    }
}
