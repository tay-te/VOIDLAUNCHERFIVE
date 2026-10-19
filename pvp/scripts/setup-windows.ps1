# One-time toolchain setup for VOID PVP on Windows (PowerShell 5+), no admin rights.
#
#   powershell -ExecutionPolicy Bypass -File pvp\scripts\setup-windows.ps1
#
# Everything goes into the repository's .tools\ folder (gitignored) and nothing touches the
# system PATH, so deleting .tools\ undoes it. What it needs that it cannot fetch: Visual Studio
# 2022 or newer with the "Desktop development with C++" workload (MSVC + Windows SDK), and
# WebView2 (present on Windows 10/11).
#
#   .tools\rustup, .tools\cargo   Rust stable (minimal profile)       the launcher and void-pvp CLI
#   .tools\cmake                  CMake 4.x (knows VS 2026)           the Ultralight JNI binding
#   .tools\jdk-21                 Temurin 21                          Gradle 8.14 for the mod
#   .tools\bin\pnpm.cmd           pnpm 9 through npx                  Tauri's beforeDevCommand
#
# Then it installs the JS workspace, builds the native binding (downloads the Ultralight SDK the
# first time) and the mod jar. Run scripts\launcher.ps1 afterwards to open the launcher.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$Pvp = Split-Path -Parent $PSScriptRoot
$Root = Split-Path -Parent $Pvp
$Tools = Join-Path $Root '.tools'
New-Item -ItemType Directory -Force -Path $Tools, (Join-Path $Tools 'bin') | Out-Null

function Say($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }

if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node 22+ is required: https://nodejs.org' }

# ---- Rust ------------------------------------------------------------------------------------------
$env:RUSTUP_HOME = Join-Path $Tools 'rustup'
$env:CARGO_HOME = Join-Path $Tools 'cargo'
$cargo = Join-Path $env:CARGO_HOME 'bin\cargo.exe'
if (-not (Test-Path $cargo)) {
	Say 'Installing Rust (stable, minimal) into .tools\'
	$init = Join-Path $Tools 'rustup-init.exe'
	Invoke-WebRequest -Uri 'https://static.rust-lang.org/rustup/dist/x86_64-pc-windows-msvc/rustup-init.exe' -OutFile $init
	& $init -y --no-modify-path --default-toolchain stable --profile minimal
	if ($LASTEXITCODE -ne 0) { throw 'rustup-init failed' }
	Remove-Item $init
} else { Write-Host 'Rust already in .tools\' }

# ---- CMake -----------------------------------------------------------------------------------------
$cmakeDir = Join-Path $Tools 'cmake'
if (-not (Test-Path (Join-Path $cmakeDir 'bin\cmake.exe'))) {
	Say 'Installing CMake into .tools\'
	$rel = Invoke-RestMethod -Uri 'https://api.github.com/repos/Kitware/CMake/releases/latest'
	$asset = $rel.assets | Where-Object { $_.name -like '*windows-x86_64.zip' } | Select-Object -First 1
	$zip = Join-Path $Tools 'cmake.zip'
	Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $zip
	$unpack = Join-Path $Tools 'cmake-unpack'
	Expand-Archive -Path $zip -DestinationPath $unpack -Force
	Move-Item -Path (Get-ChildItem $unpack -Directory | Select-Object -First 1).FullName -Destination $cmakeDir
	Remove-Item -Recurse -Force $unpack, $zip
} else { Write-Host 'CMake already in .tools\' }

# ---- JDK 21 for Gradle -----------------------------------------------------------------------------
$jdk = Join-Path $Tools 'jdk-21'
if (-not (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
	Say 'Installing Temurin 21 into .tools\ (Gradle 8.14 cannot run on 25)'
	$zip = Join-Path $Tools 'jdk21.zip'
	Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $zip
	$unpack = Join-Path $Tools 'jdk21-unpack'
	Expand-Archive -Path $zip -DestinationPath $unpack -Force
	Move-Item -Path (Get-ChildItem $unpack -Directory | Select-Object -First 1).FullName -Destination $jdk
	Remove-Item -Recurse -Force $unpack, $zip
} else { Write-Host 'JDK 21 already in .tools\' }

# ---- pnpm shim -------------------------------------------------------------------------------------
Set-Content -Path (Join-Path $Tools 'bin\pnpm.cmd') -Value "@echo off`r`nnpx -y pnpm@9.12.3 %*" -Encoding ascii

$env:PATH = "$(Join-Path $Tools 'bin');$(Join-Path $env:CARGO_HOME 'bin');$(Join-Path $cmakeDir 'bin');$env:PATH"
$env:JAVA_HOME = $jdk

# ---- builds ----------------------------------------------------------------------------------------
Push-Location $Pvp
try {
	Say 'Installing the JS workspace'
	& pnpm install
	if ($LASTEXITCODE -ne 0) { throw 'pnpm install failed' }
	& pnpm --filter @void/protocol build
	& pnpm --filter @void/ui build

	Say 'Building the Ultralight binding (first run downloads the SDK)'
	& (Join-Path $Pvp 'mod\native\scripts\build.ps1')
} finally { Pop-Location }

& (Join-Path $PSScriptRoot 'launcher.ps1') -Build -NoRun

Say 'Done. Open the launcher with:  powershell -ExecutionPolicy Bypass -File pvp\scripts\launcher.ps1'
