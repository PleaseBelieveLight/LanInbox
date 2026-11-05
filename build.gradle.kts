plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

val releaseVersion = providers.fileContents(layout.projectDirectory.file("VERSION")).asText.get().trim()
val versionMatch = Regex(
    "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-(alpha|beta|rc)\\.([1-9][0-9]*))?"
).matchEntire(releaseVersion)
    ?: error("VERSION must use MAJOR.MINOR.PATCH with an optional -alpha.N, -beta.N, or -rc.N suffix")

val androidVersionCode = providers.gradleProperty("laninbox.androidVersionCode").get().toInt()
val windowsBuild = providers.gradleProperty("laninbox.windowsBuild").get().toInt()
val windowsMajor = versionMatch.groupValues[1].toInt()
val windowsMinor = versionMatch.groupValues[2].toInt()
require(androidVersionCode in 1..2_100_000_000) { "laninbox.androidVersionCode is out of range" }
require(windowsMajor in 0..255 && windowsMinor in 0..255 && windowsBuild in 1..65_535) {
    "Windows installer versions require major/minor in 0..255 and laninbox.windowsBuild in 1..65535"
}

// Installer build numbers increase across prereleases and stable releases of the same version.
extra["laninbox.androidVersionCode"] = androidVersionCode
extra["laninbox.baseVersion"] = releaseVersion.substringBefore('-')
extra["laninbox.windowsVersion"] = "$windowsMajor.$windowsMinor.$windowsBuild"
allprojects {
    version = releaseVersion
}
