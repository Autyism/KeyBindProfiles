# Runs the dev client in self-test mode and waits for it to finish.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\selftest.ps1 [-Version 1.21.11]
#
# What it does:
#   1. Backs up the game folder's options.txt and the mod's current_profile.txt.
#   2. Starts `gradlew :<version>:runSelfTest` (dev client with -Dkbp.selftest=true) hidden in the background.
#   3. Polls the game folder's logs\latest.log until "[SelfTest] DONE", a crash, or the timeout.
#   4. Makes sure the game process is gone (only processes started with -Dkbp.selftest=true are touched).
#   5. Restores the backups and prints the [SelfTest] log lines and the screenshot list.
#
# The game folder is run\ for 1.21.11 and versions\<version>\run for the other Minecraft versions.
# Exit code 0 = self-test finished with no failed step. Anything else = failure.

param(
    [string]$Version = '1.21.11',
    [int]$TimeoutSec = 300
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if ($Version -eq '1.21.11') { $runDir = Join-Path $root 'run' } else { $runDir = Join-Path $root "versions\$Version\run" }
$logFile = Join-Path $runDir 'logs\latest.log'
$shotDir = Join-Path $runDir 'screenshots'
$outDir = Join-Path $root "build\selftest\$Version"
$backupDir = Join-Path $outDir 'backup'
$gradleLog = Join-Path $outDir 'gradle.log'
$gradleErr = Join-Path $outDir 'gradle.err.log'

# Gradle (Loom 1.18) runs on Java 25; the game itself gets the JDK of its Minecraft version from the
# Gradle toolchain. The Gradle home stays off the system drive unless the caller chose another one.
$jdk25 = $env:KBP_JDK25
if (-not $jdk25) { $jdk25 = Join-Path $env:USERPROFILE 'scoop\apps\temurin25-jdk\current' }
if (Test-Path (Join-Path $jdk25 'bin\java.exe')) { $env:JAVA_HOME = $jdk25 }
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = 'D:/Dev/Projects/tools/gradle-home' }

function Get-SelfTestGames {
    Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine -match 'kbp\.selftest=true' }
}

function Stop-SelfTestGames {
    foreach ($p in @(Get-SelfTestGames)) {
        Write-Host "[harness] killing self-test game process $($p.ProcessId)"
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

function Read-LogText {
    if (-not (Test-Path $logFile)) { return '' }
    try {
        $fs = [System.IO.File]::Open($logFile, 'Open', 'Read', 'ReadWrite')
        try {
            $sr = New-Object System.IO.StreamReader($fs, [System.Text.Encoding]::UTF8)
            return $sr.ReadToEnd()
        } finally { $fs.Dispose() }
    } catch { return '' }
}

New-Item -ItemType Directory -Force $outDir | Out-Null
if (Test-Path $backupDir) { Remove-Item -Recurse -Force $backupDir }
New-Item -ItemType Directory -Force $backupDir | Out-Null

Stop-SelfTestGames

# Files the self-test may touch; restored afterwards no matter how the run ends.
$backups = @()
$candidates = @('options.txt', 'config\keybindprofiles\current_profile.txt', 'config\keybindprofilesplus\current_profile.txt', 'config\keybindprofilesplus\settings.json', 'config\keybindprofilesplus\combos.json', 'config\keybindprofilesplus\config-import-result.json', 'config\keybindprofilesplus\pending-config-import.zip')
foreach ($rel in $candidates) {
    $src = Join-Path $runDir $rel
    if (Test-Path $src) {
        $dst = Join-Path $backupDir ($rel -replace '[\\/]', '__')
        Copy-Item $src $dst -Force
        $backups += [pscustomobject]@{ Source = $src; Backup = $dst }
    }
}

if (Test-Path $shotDir) { Get-ChildItem $shotDir -Filter 'selftest_*.png' | Remove-Item -Force }

# A mod config import staged before the start: the game must write it before any mod reads its settings
# (checked by the self-test, which also removes the written file and the backup it caused).
$ownDir = Join-Path $runDir 'config\keybindprofilesplus'
New-Item -ItemType Directory -Force $ownDir | Out-Null
$resultFile = Join-Path $ownDir 'config-import-result.json'
$hadResult = Test-Path $resultFile
$pendingFile = Join-Path $ownDir 'pending-config-import.zip'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path $pendingFile) { Remove-Item $pendingFile -Force }
$zip = [System.IO.Compression.ZipFile]::Open($pendingFile, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($entry in @(@('files/config/kbp_selftest_early.json', '{"selftest": "early-import"}'), @('source.txt', 'selftest_early.zip'))) {
        $writer = New-Object System.IO.StreamWriter(($zip.CreateEntry($entry[0])).Open(), (New-Object System.Text.UTF8Encoding($false)))
        $writer.Write($entry[1])
        $writer.Dispose()
    }
} finally { $zip.Dispose() }
Remove-Item (Join-Path $runDir 'selftest.pid') -Force -ErrorAction SilentlyContinue

$start = Get-Date
$proc = Start-Process -FilePath (Join-Path $root 'gradlew.bat') -ArgumentList ":${Version}:runSelfTest", '--console=plain' `
    -WorkingDirectory $root -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput $gradleLog -RedirectStandardError $gradleErr

$result = 'TIMEOUT'
while (((Get-Date) - $start).TotalSeconds -lt $TimeoutSec) {
    Start-Sleep -Seconds 2
    $fresh = (Test-Path $logFile) -and ((Get-Item $logFile).LastWriteTime -ge $start)
    $text = ''
    if ($fresh) { $text = Read-LogText }
    if ($text -match '\[SelfTest\] DONE') { $result = 'DONE'; break }
    if ($text -match '---- Minecraft Crash Report ----|Crash report saved to|Minecraft has crashed') { $result = 'CRASH'; break }
    if ($proc.HasExited) {
        Start-Sleep -Seconds 1
        if ($fresh) { $text = Read-LogText }
        if ($text -match '\[SelfTest\] DONE') { $result = 'DONE' } else { $result = 'EXITED_EARLY' }
        break
    }
}

# Give a clean shutdown a moment, then make sure nothing is left behind.
$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline -and @(Get-SelfTestGames).Count -gt 0 -and $result -eq 'DONE') { Start-Sleep -Seconds 1 }
Stop-SelfTestGames
$deadline = (Get-Date).AddSeconds(20)
while ((Get-Date) -lt $deadline -and -not $proc.HasExited) { Start-Sleep -Seconds 1 }
if (-not $proc.HasExited) {
    # The wrapper is only waiting on a game that is already gone; stop it and its launcher JVM.
    & taskkill /PID $proc.Id /T /F | Out-Null
}
Start-Sleep -Seconds 1
$left = @(Get-SelfTestGames).Count

# Whatever the run left of the config import test (normally nothing: the self-test cleans up itself).
if (Test-Path $pendingFile) { Remove-Item $pendingFile -Force -ErrorAction SilentlyContinue }
Remove-Item (Join-Path $runDir 'config\kbp_selftest_early.json') -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $runDir 'selftest_configs') -Recurse -Force -ErrorAction SilentlyContinue
if (-not $hadResult) { Remove-Item $resultFile -Force -ErrorAction SilentlyContinue }

foreach ($b in $backups) { Copy-Item $b.Backup $b.Source -Force }

# The throwaway flat world the self-test creates for its in-game checks.
$testWorld = Join-Path $runDir 'saves\selftest_world'
if ($left -eq 0 -and (Test-Path $testWorld)) { Remove-Item -Recurse -Force $testWorld -ErrorAction SilentlyContinue }

$text = Read-LogText
$lines = @($text -split "`r?`n" | Where-Object { $_ -match '\[SelfTest\]' })
$failed = @($lines | Where-Object { $_ -match '\[SelfTest\] FAIL' }).Count

Write-Host "===== SELFTEST RESULT: $result  (elapsed $([int]((Get-Date) - $start).TotalSeconds)s, failed steps: $failed, game processes left: $left) ====="
$lines | ForEach-Object { Write-Host ($_ -replace '^\[[^\]]*\] \[[^\]]*\] \([^)]*\) ', '') }
Write-Host '===== SCREENSHOTS ====='
if (Test-Path $shotDir) { Get-ChildItem $shotDir -Filter 'selftest_*.png' | Sort-Object Name | ForEach-Object { Write-Host "$($_.FullName)  $([int]($_.Length / 1KB)) KB" } }
if ($result -ne 'DONE') {
    Write-Host '===== LAST LOG LINES ====='
    @($text -split "`r?`n") | Select-Object -Last 40 | ForEach-Object { Write-Host $_ }
    Write-Host '===== GRADLE OUTPUT (tail) ====='
    if (Test-Path $gradleLog) { Get-Content $gradleLog -Tail 25 | ForEach-Object { Write-Host $_ } }
    if (Test-Path $gradleErr) { Get-Content $gradleErr -Tail 25 | ForEach-Object { Write-Host $_ } }
}

if ($result -eq 'DONE' -and $failed -eq 0 -and $left -eq 0) { exit 0 } else { exit 1 }
