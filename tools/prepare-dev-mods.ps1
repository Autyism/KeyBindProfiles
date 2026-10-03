# Puts copies of malilib, Litematica and Meteor Client into libs\ so the dev client (and the
# self-test) runs with them and editing their hotkeys can be exercised for real.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\prepare-dev-mods.ps1 [-ModsDir <folder with the jars>]
#
# The jars are only read from -ModsDir (by default the PCL2 instance's mods folder); nothing there is
# changed. In the copies the "Fabric-Loom-Version" line of the manifest is lowered to this project's
# Loom: those mods are built with a newer Loom, which this project's Loom otherwise refuses to load
# in the dev client. Meteor's bundled libraries are unpacked next to it, because the dev client does
# not unpack a mod's bundled jars by itself. libs\ is not committed.

param(
    [string]$ModsDir = 'D:\Games\PCL2\.minecraft\versions\1.21.11-Fabric 0.19.5 Main\mods'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path -Parent $PSScriptRoot
$loomVersion = (Select-String -Path (Join-Path $root 'gradle.properties') -Pattern '^loom_version=(.+)$').Matches[0].Groups[1].Value
# The dev Loom compares against its own version number; any version not newer than it is accepted.
$loweredVersion = ($loomVersion -replace '-SNAPSHOT$', '') + '.0'

function Set-LoomVersion([string]$jar) {
    $zip = [System.IO.Compression.ZipFile]::Open($jar, 'Update')
    try {
        $entry = $zip.GetEntry('META-INF/MANIFEST.MF')
        if ($null -eq $entry) { return }
        $reader = New-Object System.IO.StreamReader($entry.Open())
        $text = $reader.ReadToEnd()
        $reader.Dispose()
        $patched = [regex]::Replace($text, '(?m)^Fabric-Loom-Version: .*$', "Fabric-Loom-Version: $loweredVersion`r")
        $entry.Delete()
        $writer = New-Object System.IO.StreamWriter($zip.CreateEntry('META-INF/MANIFEST.MF').Open())
        $writer.Write($patched)
        $writer.Dispose()
    } finally { $zip.Dispose() }
}

function Copy-Mod([string]$pattern, [string]$target) {
    $jar = Get-ChildItem -LiteralPath $ModsDir -File | Where-Object { $_.Name -like $pattern } | Select-Object -First 1
    if ($null -eq $jar) { Write-Host "[prepare] not found in mods folder: $pattern"; return $null }
    New-Item -ItemType Directory -Force $target | Out-Null
    # PCL2 puts a [display name] in front of some file names; the copy goes without it.
    $copy = Join-Path $target ($jar.Name -replace '^\[[^\]]*\]\s*', '')
    Copy-Item -LiteralPath $jar.FullName $copy -Force
    Set-LoomVersion $copy
    Write-Host "[prepare] $(Split-Path -Leaf $copy) -> $target"
    return $copy
}

$malilibDir = Join-Path $root 'libs\malilib'
$meteorDir = Join-Path $root 'libs\meteor'
foreach ($dir in @($malilibDir, $meteorDir)) {
    if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
}

Copy-Mod '*malilib-fabric-*.jar' $malilibDir | Out-Null
Copy-Mod '*litematica-fabric-*.jar' $malilibDir | Out-Null

# The libraries a mod carries inside its jar (malilib: conditional-mixin; Meteor: orbit, starscript...) are
# unpacked next to it. Fabric API's own modules are left out: the dev client has Fabric API already.
function Expand-Bundled([string]$jar, [string]$target) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -like 'META-INF/jars/*.jar' -and $entry.Name -notlike 'fabric-*') {
                $out = Join-Path $target $entry.Name
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $out, $true)
                Set-LoomVersion $out
                Write-Host "[prepare]   bundled $($entry.Name)"
            }
        }
    } finally { $zip.Dispose() }
}

foreach ($jar in @(Get-ChildItem -LiteralPath $malilibDir -Filter '*.jar' -ErrorAction SilentlyContinue)) {
    Expand-Bundled $jar.FullName $malilibDir
}
$meteor = Copy-Mod 'meteor-client-*.jar' $meteorDir
if ($meteor) {
    Expand-Bundled $meteor $meteorDir
}
Write-Host "[prepare] done (Loom version in the copies: $loweredVersion)"
