# Audit reproduction probes

These four diagnostic assertions confirm the observed 1.0.0 defects. They are **not** desired-behavior regression tests and must not be adopted as release gates unchanged. Convert them to expect correct behavior when implementing 1.0.1.

To reproduce on the owner's QA emulator, temporarily copy `AuditProbeTest.kt` to the existing `app/src/androidTest/java/org/hermesmobile/app/` package, run `assembleDebugAndroidTest`, then run that class through AndroidJUnitRunner. Use only a synthetic test install and loopback MockWebServer; the probes configure fixture credentials. Remove the temporary copy afterward. The source is retained here outside normal build inputs. Use E: for SDK/cache/Gradle/temp and keep the project on the requested drive.

`dependency-inventory.gradle` is a read-only init script that resolves `releaseRuntimeClasspath` and writes coordinates to `HERMES_AUDIT_DIR`. It does not import or execute upstream Hermes code. Runtime graph components can include BOM/metadata modules; this is not a complete APK/native SBOM.
