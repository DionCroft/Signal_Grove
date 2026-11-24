import java.io.File

buildscript {
    dependencies {
        constraints {
            add("classpath", "org.checkerframework:checker-qual:3.43.0") {
                because("Retain the existing qualifier version after the Guava update")
            }
            add("classpath", "com.google.guava:guava:33.7.2-jre") {
                because("GHSA-xxph-c9ww-hj94")
            }
            add("classpath", "org.apache.commons:commons-lang3:3.18.0") {
                because("GHSA-j288-q9x7-2f5v")
            }
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.6") {
                because("GHSA-3677-xxcr-wjqv")
            }
            add("classpath", "org.bouncycastle:bcpkix-jdk18on:1.85") {
                because("align the tool classpath with the fixed Bouncy Castle provider")
            }
            add("classpath", "org.bouncycastle:bcprov-jdk18on:1.85") {
                because("GHSA-9pwp-9qqc-pr26 and GHSA-qp49-qgx5-5m26")
            }
            add("classpath", "org.jdom:jdom2:2.0.6.1") {
                because("GHSA-2363-cqg2-863c")
            }
        }
    }
}

plugins {
    id("com.android.application") version "9.5.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.13" apply false
}

// The Windows wrapper keeps temporary outputs off the shared source drive.
// With no override, normal local/CI Gradle layout is unchanged.
providers.environmentVariable("SIGNAL_GROVE_BUILD_ROOT").orNull?.takeIf { it.isNotBlank() }?.let { requested ->
    require(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        "The E: build-output override is for the Windows wrapper."
    }
    require(File(requested).isAbsolute) { "Build output must be an absolute path on E:." }
    val output = File(requested).canonicalFile
    require(output.path.startsWith("E:\\", ignoreCase = true) && output.parentFile != null) {
        "Build output must use an ordinary directory on E:."
    }
    allprojects {
        val segment = if (path == ":") "_root" else path.removePrefix(":").replace(':', '/')
        layout.buildDirectory.set(File(output, segment))
    }
}
