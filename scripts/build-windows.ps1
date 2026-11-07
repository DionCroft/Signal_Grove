param(
    [string]$WorkRoot='E:\Temp\hermes-mobile',
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [string]$DebugSigningHome='',
    [string[]]$Tasks=@('assembleDebug','testDebugUnitTest','lint','assembleDebugAndroidTest')
)
$ErrorActionPreference='Stop'
if (![IO.Path]::GetFullPath($WorkRoot).StartsWith('E:\',[StringComparison]::OrdinalIgnoreCase)) {throw 'Temporary work must use E:.'}
if (![IO.Path]::GetFullPath($AndroidSdk).StartsWith('E:\',[StringComparison]::OrdinalIgnoreCase)) {throw 'Use an Android SDK on E:.'}
New-Item -ItemType Directory -Force -Path $WorkRoot | Out-Null
$env:TEMP=$WorkRoot
$env:TMP=$WorkRoot
$env:GRADLE_USER_HOME=Join-Path $WorkRoot 'gradle-home'
$env:ANDROID_HOME=$AndroidSdk
$env:ANDROID_USER_HOME=Join-Path $WorkRoot 'android-user'
$env:ANDROID_AVD_HOME=Join-Path $WorkRoot 'avd'
# Reuse the historical WorkRoot key before selecting a new signing directory.
# An explicit path supports established layouts without replacing their key.
if (!$DebugSigningHome) {
    $DebugSigningHome=if (Test-Path -LiteralPath (Join-Path $WorkRoot 'debug.keystore')) {$WorkRoot} else {Join-Path $WorkRoot 'signing'}
}
if (![IO.Path]::GetFullPath($DebugSigningHome).StartsWith('E:\',[StringComparison]::OrdinalIgnoreCase)) {throw 'Debug signing material must use E:.'}
$env:HERMES_BUILD_HOME=$DebugSigningHome
# Kotlin's daemon rendezvous files otherwise default to LocalAppData on Windows.
$kotlinDaemonRoot=Join-Path $WorkRoot 'kotlin-daemon'
New-Item -ItemType Directory -Force -Path $kotlinDaemonRoot | Out-Null
$env:JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=`"$WorkRoot`" -Dkotlin.daemon.options=`"runFilesPath=$kotlinDaemonRoot`""
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME,$env:ANDROID_AVD_HOME,$env:HERMES_BUILD_HOME | Out-Null
Push-Location (Split-Path -Parent $PSScriptRoot)
try { & .\gradlew.bat --no-daemon --console=plain --dependency-verification strict @Tasks; if($LASTEXITCODE) {throw "Gradle failed with exit code $LASTEXITCODE"} }
finally {Pop-Location}
