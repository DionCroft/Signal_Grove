# Contributing

Open an issue for substantial changes and keep patches focused. Never include tokens, personal endpoints, logs or chat exports. Use fictional fixtures and the FakeHermesApiClient.

1. Fork and create a branch.
2. Build with the wrapper; run `testDebugUnitTest lint assembleDebug assembleDebugAndroidTest`.
For release validation, also run `lintRelease` and retain its XML report; `lint` alone selects the default debug variant.

3. Run connected tests on an emulator when available; check all four themes, a narrow display and large font scaling.
4. Describe the user-visible change, validation and remaining limitations in your pull request.

Keep translations in `Words`; each entry has English, Chinese and accented pinyin. Review Chinese/pinyin with a fluent speaker. Never translate agent content implicitly. Preserve HTTPS validation, prefix routing, explicit approvals and terminal-status semantics.

All contributions must be compatible with MIT. Do not copy upstream code or branding without attribution and licence review. No fonts are bundled.

Dependency changes must include reviewed additions to `gradle/verification-metadata.xml`. Keep strict verification enabled and run `python -B scripts/check_verification_policy.py` plus the offline script tests. Do not regenerate hashes in CI or disable verification to clear a failure. See [integrity guidance](docs/MAINTENANCE.md#dependency-integrity) for trust-on-first-use limits and platform artifacts.

Record the exact source revision and APK hashes with test evidence. Keep raw
JUnit XML and debug/release lint XML alongside their build logs. A report left
by another source or variant is not a fresh check; a missing required report
stops validation. Explicitly request the variant being claimed, including when
Gradle restores valid task output from its cache. CI now requests release lint
and retains both variants plus raw JVM reports. Report validation is separate
from signed-artifact provenance, complete native coverage and retained-data
upgrade acceptance. Preserve original signing keys and installed device data.
