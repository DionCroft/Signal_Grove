param(
    [string]$WorkRoot='E:\Temp\hermes-mobile',
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [string]$SigningDirectory=(Join-Path (Split-Path -Parent $PSScriptRoot) '.local\release-signing')
)
$ErrorActionPreference='Stop'
if (![IO.Path]::GetFullPath($WorkRoot).StartsWith('E:\',[StringComparison]::OrdinalIgnoreCase)) {throw 'Temporary work must use E:.'}
$env:TEMP=$WorkRoot;$env:TMP=$WorkRoot
$signingRoot=[IO.Path]::GetFullPath($SigningDirectory)
New-Item -ItemType Directory -Force -Path $signingRoot | Out-Null
$keyPath=Join-Path $signingRoot 'community-release.jks'
$passwordPath=Join-Path $signingRoot 'password.dpapi'
if((Test-Path -LiteralPath $keyPath) -ne (Test-Path -LiteralPath $passwordPath)) {throw 'Signing files are incomplete. Restore the original pair; do not replace an existing key.'}
$keytool=Join-Path $(if($env:JAVA_HOME){$env:JAVA_HOME}else{Split-Path -Parent (Split-Path -Parent (Get-Command java).Source)}) 'bin\keytool.exe'
try {
    if(!(Test-Path -LiteralPath $keyPath)) {
        $randomBytes=New-Object byte[] 32
        [Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
        $passwordText=[Convert]::ToHexString($randomBytes)
        $secure=ConvertTo-SecureString $passwordText -AsPlainText -Force
        $secure | ConvertFrom-SecureString | Set-Content -LiteralPath $passwordPath -NoNewline
        $env:HERMES_RELEASE_STORE_PASSWORD=$passwordText
        & $keytool -genkeypair -keystore $keyPath -storetype JKS -storepass:env HERMES_RELEASE_STORE_PASSWORD -keypass:env HERMES_RELEASE_STORE_PASSWORD -alias hermesmobile -dname 'CN=Signal Grove Community,O=Signal Grove Community' -keyalg RSA -keysize 4096 -validity 10000
        if($LASTEXITCODE){throw 'Production key generation failed. Inspect the signing files before retrying.'}
        $passwordText=$null
    } else {
        $secure=Get-Content -LiteralPath $passwordPath -Raw | ConvertTo-SecureString
        $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try {$env:HERMES_RELEASE_STORE_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)} finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)}
    }
    $env:HERMES_RELEASE_KEYSTORE=$keyPath
    $env:HERMES_RELEASE_KEY_ALIAS='hermesmobile'
    $env:HERMES_RELEASE_KEY_PASSWORD=$env:HERMES_RELEASE_STORE_PASSWORD
    & (Join-Path $PSScriptRoot 'build-windows.ps1') -WorkRoot $WorkRoot -AndroidSdk $AndroidSdk -Tasks @('assembleRelease','lintRelease')
    if($LASTEXITCODE){throw 'Release build failed'}
    & $keytool -exportcert -rfc -keystore $keyPath -storepass:env HERMES_RELEASE_STORE_PASSWORD -alias hermesmobile -file (Join-Path $signingRoot 'signing-certificate.pem')
    if($LASTEXITCODE){throw 'Certificate export failed'}
} finally {
    Remove-Item Env:HERMES_RELEASE_STORE_PASSWORD,Env:HERMES_RELEASE_KEY_PASSWORD,Env:HERMES_RELEASE_KEYSTORE,Env:HERMES_RELEASE_KEY_ALIAS -ErrorAction SilentlyContinue
    $secure=$null;$passwordText=$null
}
