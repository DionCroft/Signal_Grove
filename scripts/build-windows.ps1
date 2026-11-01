param(
    [string]$WorkRoot='E:\Temp\hermes-mobile',
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
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
$env:HERMES_BUILD_HOME=Join-Path $WorkRoot 'signing'
$env:JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$WorkRoot"
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME,$env:ANDROID_AVD_HOME,$env:HERMES_BUILD_HOME | Out-Null
Push-Location (Split-Path -Parent $PSScriptRoot)
try { & .\gradlew.bat --no-daemon --console=plain @Tasks; if($LASTEXITCODE) {throw "Gradle failed with exit code $LASTEXITCODE"} }
finally {Pop-Location}
