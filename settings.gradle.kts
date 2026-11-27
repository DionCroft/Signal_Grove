pluginManagement {
    repositories {
        google()
        val central = mavenCentral()
        gradlePluginPortal()
        // The strict verification pin is for the Compose marker published to Maven Central.
        exclusiveContent {
            forRepositories(central)
            filter {
                includeModule("org.jetbrains.kotlin.plugin.compose", "org.jetbrains.kotlin.plugin.compose.gradle.plugin")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven("https://jitpack.io") { name = "TesseractAndroidRelease" }
            }
            filter {
                includeModule("cz.adaptech.tesseract4android", "tesseract4android")
            }
        }
    }
}
rootProject.name = "SignalGrove"
include(":app")
