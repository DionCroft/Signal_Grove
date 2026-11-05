# Signal Grove device-test diagnostics

The optional `DeviceTestDiagnosticsListener` records bounded, in-process test
metadata when a device run stalls. It belongs to the test APK and is inactive in
the normal app and default Android CI invocation. Existing assertions, deadlines,
case selection and outcome checks remain authoritative.

Use an owned debug target and verify the app/test APK hashes, packages, versions
and matching signers before testing. Preserve existing data and display settings.
The `NavigationTest` fixture clears the debug application's chat/task stores;
use isolated Room/HTTP fixtures on an owner's populated phone. Keep foreground,
background and idle-device acceptance as separate scopes.

## Capture

Example in PowerShell, with the existing SDK and all host artifacts on E:.
Choose the precise case or suite being investigated before execution. Retain the
original failed run and its APK identities separately.

```powershell
$auditAdb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$auditDevice = 'YOUR_DEVICE_SERIAL'
& $auditAdb -s $auditDevice shell am instrument -w -r `
  -e listener org.hermesmobile.app.DeviceTestDiagnosticsListener `
  -e newRunListenerMode true `
  -e signal_grove.snapshot_delay_seconds 1 `
  -e class 'org.hermesmobile.app.HostKanbanOverrideStorageTest#readbackOmissionCannotBecomeProofOfClearing' `
  org.hermesmobile.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Listener registration follows the official
[AndroidJUnitRunner contract](https://developer.android.com/reference/androidx/test/runner/AndroidJUnitRunner).
The default snapshot delay is ten seconds, bounded to 1–60 seconds. A second
snapshot is scheduled at three times that delay, capped at two minutes. Each run
allows at most 32 stack snapshots, at most 256 KiB each, and retains case/run
start/finish summaries in the debug app's private cache.

The explicit `-e signal_grove.keep_cpu_awake true` option acquires its own partial
CPU wake lease with a **120-second timeout**, released when the run finishes.
It is **off by default** and uses the app's existing normal wake-lock permission.
Record this precondition when used; it does not establish default background or
idle-device behavior. No global power setting or permission grant is changed.

Snapshots record test identifiers, elapsed/uptime/nanosecond deltas, interactive
state, lease state and bounded stack class/method/basename/line/thread-state
metadata. Thread names, local variables, request values, URLs, arguments, payloads
and raw exception messages are omitted. Parameter display names are stripped.
The [Android clock definitions](https://developer.android.com/reference/android/os/SystemClock)
explain why elapsed and uptime deltas are both recorded; their difference alone
does not diagnose a vendor restriction or application defect.

## Export and acceptance

List this namespace to obtain the actual `run-N` directory; do not invent a path.

```powershell
& $auditAdb -s $auditDevice shell run-as org.hermesmobile.app.debug find cache/device-test-diagnostics -type f
```

Export one run into a **new** E: directory, supplying the SHA-256 values of the
known app/test APK pair and the declared case count:

```powershell
python -B scripts/export_device_diagnostics.py `
  --adb $auditAdb --serial $auditDevice --run-id run-ACTUAL_NUMBER `
  --app-sha256 APP_SHA256 --test-sha256 TEST_SHA256 `
  --expected-tests 1 --output E:\Temp\signal-grove-qa\NEW_RUN
```

The exporter checks installed APK bytes before reading this private namespace,
preserves JSON bytes/checksums, refuses unexpected paths/fields and bounds each
file and total export. It installs nothing and changes no device state. Existing
exports are retained. Missing/failed/mismatched listener results return failure.

The collection manifest concerns listener metadata. Acceptance also requires the
same run's positive native case records, terminal OK, no failures/skips, frozen
source/APK reconciliation and appropriate device readback. A snapshot, foreground
launch, connection reset or interrupted process is not a passing test. Diagnostic
files stay in local evidence storage; review them before sharing.
