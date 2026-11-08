import java.io.File

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
