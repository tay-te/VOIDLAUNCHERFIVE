<#
.SYNOPSIS
  Builds voidultralight.dll for Windows x64.

.DESCRIPTION
  Requires Visual Studio 2019 or newer with the C++ workload, CMake >= 3.20 and a JDK
  (JAVA_HOME, or on PATH). The Ultralight SDK is downloaded and cached under mod/native/sdk/ by
  the CMake step — nothing else to install, CMake's bundled libarchive reads the .7z.

  Only x64 is supported: Minecraft 1.8.9's LWJGL 2 natives and the Ultralight SDK are both x64,
  so make sure the generator platform stays x64 (it is set explicitly below).

.EXAMPLE
  scripts\build.ps1
  scripts\build.ps1 -Config Debug -Clean
#>
[CmdletBinding()]
param(
  [ValidateSet('Release', 'Debug')]
  [string]$Config = 'Release',
  [switch]$Clean,
  [switch]$NoJava
)

$ErrorActionPreference = 'Stop'

$native = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $native 'build'

if ($Clean -and (Test-Path $buildDir)) {
  Remove-Item -Recurse -Force $buildDir
}

if (-not $env:JAVA_HOME) {
  $javac = Get-Command javac -ErrorAction SilentlyContinue
  if ($javac) {
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javac.Source)
    Write-Host "JAVA_HOME not set; using $env:JAVA_HOME"
  } else {
    throw 'Set JAVA_HOME to a JDK (8 or newer) before building.'
  }
}

$javaOption = if ($NoJava) { 'OFF' } else { 'ON' }

# -A x64 pins the platform; the SDK ships no 32-bit build and neither does the game.
cmake -S $native -B $buildDir -A x64 `
  "-DCMAKE_BUILD_TYPE=$Config" `
  "-DVOIDUL_BUILD_JAVA=$javaOption"
if ($LASTEXITCODE -ne 0) { throw 'cmake configure failed' }

cmake --build $buildDir --config $Config --parallel
if ($LASTEXITCODE -ne 0) { throw 'cmake build failed' }

Write-Host ''
Write-Host "natives staged in: $buildDir\natives\windows-x64"
Get-ChildItem -Recurse (Join-Path $buildDir 'natives\windows-x64') | Select-Object -ExpandProperty FullName
