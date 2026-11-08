param(
    [string]$WorkRoot='E:\Temp\hermes-mobile',
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [string]$DebugSigningHome='',
    [string[]]$Tasks=@('assembleDebug','testDebugUnitTest','lint','lintRelease','assembleDebugAndroidTest')
)
$ErrorActionPreference='Stop'
function Assert-EDirectory([string]$Path,[string]$Label) {
    $full=[IO.Path]::GetFullPath($Path)
    if (!$full.StartsWith('E:\',[StringComparison]::OrdinalIgnoreCase)) {throw "$Label must use E:."}
    $cursor=$full
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            $item=Get-Item -LiteralPath $cursor -Force
            if (!$item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {throw "$Label must use ordinary directories on E:."}
        }
        $parent=[IO.Directory]::GetParent($cursor)
        $cursor=if ($parent) {$parent.FullName} else {$null}
    }
    return $full
}
$WorkRoot=Assert-EDirectory $WorkRoot 'Temporary work'
$AndroidSdk=Assert-EDirectory $AndroidSdk 'Android SDK'
if (!$DebugSigningHome) {
    $DebugSigningHome=if (Test-Path -LiteralPath (Join-Path $WorkRoot 'debug.keystore')) {$WorkRoot} else {Join-Path $WorkRoot 'signing'}
}
$DebugSigningHome=Assert-EDirectory $DebugSigningHome 'Debug signing material'
$sourceRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\')
$hasher=[Security.Cryptography.SHA256]::Create()
try {$workspaceId=-join ($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($sourceRoot.ToLowerInvariant())) | ForEach-Object {$_.ToString('x2')})}
finally {$hasher.Dispose()}
$workspaceRoot=Assert-EDirectory (Join-Path $WorkRoot ('w\'+$workspaceId.Substring(0,16))) 'Workspace cache'
$canonicalSource=$sourceRoot
if ($sourceRoot -match '^[A-Za-z]:') {
    $drive=Get-PSDrive -Name $sourceRoot.Substring(0,1)
    if ($drive.DisplayRoot) {$canonicalSource=Join-Path $drive.DisplayRoot $sourceRoot.Substring(3)}
}
function Read-SourceGit([string[]]$Arguments) {
    $oldDir=$env:GIT_DIR;$oldTree=$env:GIT_WORK_TREE;$oldLocks=$env:GIT_OPTIONAL_LOCKS
    try {
        $env:GIT_DIR=$null;$env:GIT_WORK_TREE=$null;$env:GIT_OPTIONAL_LOCKS='0'
        $argsForGit=@('-c',('safe.directory='+$sourceRoot.Replace('\','/')),'-c',('safe.directory='+$canonicalSource.Replace('\','/')),'-c','core.fsmonitor=false','-C',$sourceRoot)+$Arguments
        $value=& git @argsForGit
        if ($LASTEXITCODE) {throw 'Could not read the canonical Git checkout for a build snapshot.'}
        return ($value -join "`n")
    } finally {$env:GIT_DIR=$oldDir;$env:GIT_WORK_TREE=$oldTree;$env:GIT_OPTIONAL_LOCKS=$oldLocks}
}
function Get-SourceSnapshot {
    $names=(Read-SourceGit -Arguments @('ls-files','--cached','--others','--exclude-standard','-z')).Split([char]0,[StringSplitOptions]::RemoveEmptyEntries) | Sort-Object -Unique
    $deleted=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($name in (Read-SourceGit -Arguments @('ls-files','--deleted','-z')).Split([char]0,[StringSplitOptions]::RemoveEmptyEntries)) {[void]$deleted.Add($name)}
    $files=[ordered]@{}
    $checked=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($name in $names) {
        if ($name -match '[\x00-\x1f]' -or $name -match '(^|/)(\.git|\.gradle|\.kotlin|\.local|build)(/|$)' -or $name -match '(^|/)\.env($|\.)' -or $name -match '\.(keystore|jks)$') {throw 'A private or generated path is present in the proposed source snapshot.'}
        $path=[IO.Path]::GetFullPath((Join-Path $sourceRoot $name))
        if (!$path.StartsWith(($sourceRoot+'\'),[StringComparison]::OrdinalIgnoreCase)) {throw 'A source path escaped the checkout.'}
        if (!(Test-Path -LiteralPath $path)) {
            if ($deleted.Contains($name)) {continue}
            throw 'A source input disappeared; inspect the checkout before building.'
        }
        $item=Get-Item -LiteralPath $path -Force
        if ($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {throw 'Source snapshots require regular checked-out files.'}
        $parent=$item.DirectoryName
        while ($parent.StartsWith(($sourceRoot+'\'),[StringComparison]::OrdinalIgnoreCase) -and $checked.Add($parent)) {
            if ((Get-Item -LiteralPath $parent -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {throw 'A source directory is a reparse point.'}
            $parent=[IO.Path]::GetDirectoryName($parent)
        }
        $files[$name]=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    if (!$files.Contains('settings.gradle.kts') -or !$files.Contains('app/build.gradle.kts')) {throw 'The source snapshot is missing its Android build configuration.'}
    $lines=@($files.Keys | ForEach-Object {$_+[char]0+$files[$_]})+@($deleted | Sort-Object | ForEach-Object {'deleted'+[char]0+$_})
    $hash=[Security.Cryptography.SHA256]::Create()
    try {$digest=-join ($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines -join "`n"))) | ForEach-Object {$_.ToString('x2')})}
    finally {$hash.Dispose()}
    return [pscustomobject]@{
        digest=$digest;files=$files
        revision=(Read-SourceGit -Arguments @('rev-parse','HEAD')).Trim()
        gitDirectory=(Read-SourceGit -Arguments @('rev-parse','--absolute-git-dir')).Trim()
        dirty=![string]::IsNullOrEmpty((Read-SourceGit -Arguments @('status','--porcelain','--untracked-files=all')))
    }
}
function Assert-ContextInputs($Snapshot,[string]$Directory) {
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($file in (Get-ChildItem -LiteralPath $Directory -File -Force -Recurse)) {
        if ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) {throw 'The temporary build context contains a reparse point.'}
        $relative=$file.FullName.Substring($Directory.TrimEnd('\').Length+1).Replace('\','/')
        if (!$Snapshot.files.Contains($relative) -or (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Snapshot.files[$relative]) {throw 'Build-context source changed; inspect generated source differences before publishing.'}
        [void]$seen.Add($relative)
    }
    if ($seen.Count -ne $Snapshot.files.Count) {throw 'The temporary build context is incomplete.'}
}

$snapshot=Get-SourceSnapshot
$contextRoot=Assert-EDirectory (Join-Path $workspaceRoot ('c\'+$snapshot.digest.Substring(0,16))) 'Build snapshot'
$layout=[ordered]@{
    version=2;workspaceId=$workspaceId;sourceRoot=$sourceRoot;sourceDigest=$snapshot.digest;sourceRevision=$snapshot.revision;sourceDirty=$snapshot.dirty
    sourceContext=(Join-Path $contextRoot 's');contextManifest=(Join-Path $contextRoot 'inputs.json')
    buildRoot=(Join-Path $contextRoot 'b')
    rootBuild=(Join-Path $contextRoot 'b\_root')
    appBuild=(Join-Path $contextRoot 'b\app')
    projectCache=(Join-Path $contextRoot 'g')
    kotlinProject=(Join-Path $contextRoot 'k')
    publishedAppBuild=(Join-Path $sourceRoot 'app\build')
    publishedRootBuild=(Join-Path $sourceRoot 'build')
}
$layoutPath=Join-Path $workspaceRoot 'layout-v2.json'
if (Test-Path -LiteralPath $layoutPath) {
    $previousLayout=Get-Content -LiteralPath $layoutPath -Raw | ConvertFrom-Json
    if ($previousLayout.version -ne 2 -or $previousLayout.workspaceId -ne $workspaceId -or
        ![string]::Equals($previousLayout.sourceRoot,$sourceRoot,[StringComparison]::OrdinalIgnoreCase)) {throw 'Workspace cache identity differs; inspect its layout before building.'}
}
$mutex=[Threading.Mutex]::new($false,('Local\SignalGroveBuild.'+$workspaceId))
$locked=$false
try {
    try {$locked=$mutex.WaitOne(0)}
    catch [Threading.AbandonedMutexException] {$locked=$true;throw 'An earlier wrapper stopped unexpectedly. Inspect its receipt and build processes before retrying.'}
    if (!$locked) {throw 'Another Windows build is using this checkout. Inspect that build before starting another.'}

    foreach ($directory in @($WorkRoot,$workspaceRoot,$layout.sourceContext,$layout.buildRoot,$layout.projectCache,$layout.kotlinProject,$DebugSigningHome)) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
    }
    $env:TEMP=$WorkRoot
    $env:TMP=$WorkRoot
    $env:GRADLE_USER_HOME=Join-Path $WorkRoot 'gradle-home'
    $env:ANDROID_HOME=$AndroidSdk
    $env:ANDROID_USER_HOME=Join-Path $WorkRoot 'android-user'
    $env:ANDROID_AVD_HOME=Join-Path $WorkRoot 'avd'
    $env:HERMES_BUILD_HOME=$DebugSigningHome
    $env:SIGNAL_GROVE_BUILD_ROOT=$layout.buildRoot
    $kotlinDaemonRoot=Join-Path $WorkRoot 'kotlin-daemon'
    New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME,$env:ANDROID_AVD_HOME,$kotlinDaemonRoot | Out-Null
    $env:JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=`"$WorkRoot`" -Dkotlin.daemon.options=`"runFilesPath=$kotlinDaemonRoot`""

    function Write-JsonAtomically([string]$Path,$Value) {
        $pending=$Path+'.pending'
        [IO.File]::WriteAllText($pending,($Value | ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $pending -Destination $Path -Force
    }
    if (Test-Path -LiteralPath $layout.contextManifest) {
        $known=Get-Content -LiteralPath $layout.contextManifest -Raw | ConvertFrom-Json
        if ($known.version -ne 1 -or $known.digest -ne $snapshot.digest -or $known.workspaceId -ne $workspaceId) {throw 'Build-context identity differs; inspect the recorded snapshot.'}
        Assert-ContextInputs $snapshot $layout.sourceContext
    } else {
        # A partial copy can resume only when every existing file matches this exact input set.
        foreach ($file in (Get-ChildItem -LiteralPath $layout.sourceContext -File -Force -Recurse)) {
            $relative=$file.FullName.Substring($layout.sourceContext.Length+1).Replace('\','/')
            if (!$snapshot.files.Contains($relative) -or (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -ne $snapshot.files[$relative]) {throw 'Unbound content exists in the temporary snapshot.'}
        }
        foreach ($name in $snapshot.files.Keys) {
            $target=Join-Path $layout.sourceContext $name
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
            if (!(Test-Path -LiteralPath $target)) {[IO.File]::Copy((Join-Path $sourceRoot $name),$target,$false)}
        }
        Assert-ContextInputs $snapshot $layout.sourceContext
        Write-JsonAtomically $layout.contextManifest ([ordered]@{version=1;workspaceId=$workspaceId;digest=$snapshot.digest;files=$snapshot.files})
    }
    Write-JsonAtomically $layoutPath $layout
    $receiptDirectory=Join-Path $workspaceRoot 'receipts'
    New-Item -ItemType Directory -Force -Path $receiptDirectory | Out-Null
    $buildId=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N')
    $receiptPath=Join-Path $receiptDirectory ($buildId+'.json')
    $receipt=[ordered]@{
        version=1;id=$buildId;state='running';phase='gradle';pid=$PID;tasks=$Tasks
        startedAt=[DateTime]::UtcNow.ToString('o');layout=$layout
        gradleExitCode=$null;gradleSeconds=$null;publishSeconds=$null;publishedFiles=@();retiredReports=@()
    }
    function Save-Receipt {
        $receipt.updatedAt=[DateTime]::UtcNow.ToString('o')
        Write-JsonAtomically $receiptPath $receipt
        Write-JsonAtomically (Join-Path $workspaceRoot 'latest-build.json') $receipt
    }
    Save-Receipt

    $published=[Collections.Generic.List[object]]::new()
    $checkedDirectories=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $sourcePrefix=$sourceRoot+'\'
    function Ensure-PublishDirectory([string]$Directory) {
        $full=[IO.Path]::GetFullPath($Directory)
        if (!$full.StartsWith($sourcePrefix,[StringComparison]::OrdinalIgnoreCase)) {throw 'Published output escaped the checkout.'}
        $cursor=$full
        while ($cursor.StartsWith($sourcePrefix,[StringComparison]::OrdinalIgnoreCase) -and $checkedDirectories.Add($cursor)) {
            if (Test-Path -LiteralPath $cursor) {
                $item=Get-Item -LiteralPath $cursor -Force
                if (!$item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {throw 'Published output has an unexpected directory target.'}
            }
            $cursor=[IO.Path]::GetDirectoryName($cursor)
        }
        [IO.Directory]::CreateDirectory($full) | Out-Null
    }
    function Publish-Tree([string]$From,[string]$To,[string]$Kind) {
        if (!(Test-Path -LiteralPath $From)) {return}
        $prefix=[IO.Path]::GetFullPath($From).TrimEnd('\')+'\'
        foreach ($file in (Get-ChildItem -LiteralPath $From -File -Recurse | Sort-Object FullName)) {
            if ($Kind -eq 'apk' -and $file.Extension -ne '.apk' -and !$file.Name.EndsWith('.apk.idsig') -and $file.Name -ne 'output-metadata.json') {continue}
            if ($Kind -eq 'xml' -and ($file.Extension -ne '.xml' -or !$file.Name.StartsWith('TEST-'))) {continue}
            if ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) {throw 'Generated output contains a reparse point.'}
            if (!$file.FullName.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)) {throw 'Generated output escaped its declared directory.'}
            $relative=$file.FullName.Substring($prefix.Length)
            $target=[IO.Path]::GetFullPath((Join-Path $To $relative))
            Ensure-PublishDirectory ([IO.Path]::GetDirectoryName($target))
            if ((Test-Path -LiteralPath $target) -and ((Get-Item -LiteralPath $target -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {throw 'Published output has an unexpected file target.'}
            $digest=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            $same=(Test-Path -LiteralPath $target) -and ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -eq $digest)
            if (!$same) {[IO.File]::Copy($file.FullName,$target,$true)}
            if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -ne $digest) {throw 'Published output hash differs; inspect this receipt before consuming it.'}
            $published.Add([ordered]@{path=$target.Substring($sourcePrefix.Length);sha256=$digest;bytes=$file.Length;copied=(!$same)})
        }
    }
    $elapsed=[Diagnostics.Stopwatch]::StartNew()
    try {
        $gitEnvironment=[ordered]@{}
        $gitNames=@('GIT_DIR','GIT_WORK_TREE','GIT_OPTIONAL_LOCKS','GIT_CONFIG_COUNT')
        $gitCount=if ($env:GIT_CONFIG_COUNT) {[int]$env:GIT_CONFIG_COUNT} else {0}
        foreach ($i in $gitCount..($gitCount+2)) {$gitNames+=@(('GIT_CONFIG_KEY_'+$i),('GIT_CONFIG_VALUE_'+$i))}
        foreach ($name in $gitNames) {$gitEnvironment[$name]=[Environment]::GetEnvironmentVariable($name)}
        $env:GIT_DIR=$snapshot.gitDirectory;$env:GIT_WORK_TREE=$sourceRoot;$env:GIT_OPTIONAL_LOCKS='0'
        [Environment]::SetEnvironmentVariable(('GIT_CONFIG_KEY_'+$gitCount),'safe.directory')
        [Environment]::SetEnvironmentVariable(('GIT_CONFIG_VALUE_'+$gitCount),$canonicalSource.Replace('\','/'))
        [Environment]::SetEnvironmentVariable(('GIT_CONFIG_KEY_'+($gitCount+1)),'safe.directory')
        [Environment]::SetEnvironmentVariable(('GIT_CONFIG_VALUE_'+($gitCount+1)),$sourceRoot.Replace('\','/'))
        [Environment]::SetEnvironmentVariable(('GIT_CONFIG_KEY_'+($gitCount+2)),'core.fsmonitor')
        [Environment]::SetEnvironmentVariable(('GIT_CONFIG_VALUE_'+($gitCount+2)),'false')
        $env:GIT_CONFIG_COUNT=[string]($gitCount+3)
        Push-Location $layout.sourceContext
        try {
            & .\gradlew.bat --no-daemon --console=plain --dependency-verification strict --project-cache-dir $layout.projectCache "-Pkotlin.project.persistent.dir=$($layout.kotlinProject)" "-Pkotlin.project.persistent.dir.gradle.disableWrite=true" @Tasks
            $receipt.gradleExitCode=$LASTEXITCODE
            if ($LASTEXITCODE) {throw "Gradle failed with exit code $LASTEXITCODE"}
        } finally {Pop-Location;foreach ($name in $gitEnvironment.Keys) {[Environment]::SetEnvironmentVariable($name,$gitEnvironment[$name])}}
        Assert-ContextInputs $snapshot $layout.sourceContext
        $after=Get-SourceSnapshot
        if ($after.digest -ne $snapshot.digest -or $after.revision -ne $snapshot.revision -or $after.dirty -ne $snapshot.dirty) {throw 'Canonical source changed during the build; published outputs are held.'}
        $receipt.gradleSeconds=$elapsed.Elapsed.TotalSeconds
        $receipt.state='built';$receipt.phase='publish';Save-Receipt
        $elapsed.Restart()
        Publish-Tree (Join-Path $layout.appBuild 'outputs\apk') (Join-Path $layout.publishedAppBuild 'outputs\apk') 'apk'
        Publish-Tree (Join-Path $layout.appBuild 'reports') (Join-Path $layout.publishedAppBuild 'reports') 'reports'
        Publish-Tree (Join-Path $layout.appBuild 'test-results') (Join-Path $layout.publishedAppBuild 'test-results') 'xml'
        Publish-Tree (Join-Path $layout.rootBuild 'reports') (Join-Path $layout.publishedRootBuild 'reports') 'reports'

        # Preserve obsolete generated XML before retiring it, so old test classes cannot inflate a later count.
        $testSource=Join-Path $layout.appBuild 'test-results\testDebugUnitTest'
        $testTarget=Join-Path $layout.publishedAppBuild 'test-results\testDebugUnitTest'
        if (Test-Path -LiteralPath $testSource) {
            Ensure-PublishDirectory $testTarget
            $retired=[Collections.Generic.List[object]]::new()
            foreach ($old in (Get-ChildItem -LiteralPath $testTarget -File -Filter 'TEST-*.xml')) {
                if (!(Test-Path -LiteralPath (Join-Path $testSource $old.Name))) {
                    if ($old.Attributes -band [IO.FileAttributes]::ReparsePoint) {throw 'Obsolete report has an unexpected file target.'}
                    $backupDirectory=Join-Path $workspaceRoot ('retired-reports\'+$buildId)
                    New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
                    $backup=Join-Path $backupDirectory $old.Name
                    [IO.File]::Copy($old.FullName,$backup,$false)
                    $hash=(Get-FileHash -LiteralPath $old.FullName -Algorithm SHA256).Hash
                    if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $hash) {throw 'Obsolete report backup differs.'}
                    Remove-Item -LiteralPath $old.FullName
                    $retired.Add([ordered]@{name=$old.Name;sha256=$hash.ToLowerInvariant();backup=$backup})
                }
            }
            $receipt.retiredReports=$retired.ToArray()
        }
        $receipt.publishSeconds=$elapsed.Elapsed.TotalSeconds
        $receipt.publishedFiles=$published.ToArray()
        $receipt.state='published';$receipt.phase='complete';Save-Receipt
        Write-Output "Temporary build/cache layout: $layoutPath"
        Write-Output "Verified published outputs: $receiptPath"
    } catch {
        $receipt.state='failed';$receipt.errorType=$_.Exception.GetType().FullName
        $receipt.publishedFiles=$published.ToArray()
        try {Save-Receipt} catch {Write-Warning 'Could not retain the failed build receipt; inspect the original command output.'}
        throw
    }
} finally {
    if ($locked) {$mutex.ReleaseMutex()}
    $mutex.Dispose()
}
