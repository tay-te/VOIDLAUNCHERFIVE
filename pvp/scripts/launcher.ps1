# Build the VOID client and open the real launcher (Tauri, real Rust backend) on Windows.
#
#   powershell -ExecutionPolicy Bypass -File pvp\scripts\launcher.ps1           # open the launcher
#   powershell -ExecutionPolicy Bypass -File pvp\scripts\launcher.ps1 -Build    # rebuild the client first
#
# -Build rebuilds the in-game bundle (packages/ingame) into the mod, then the mod jar with its
# Ultralight natives, and points ~/.void-pvp/config.json at that jar so every Launch installs it.
# Run setup-windows.ps1 once before the first use.
#
# Sign-in: Microsoft needs an Azure application id that VOID does not ship (see
# apps/desktop/README.md); use Settings -> Play offline with any name. Offline accounts cannot
# join online-mode servers such as Hypixel — launch to the Title screen and play singleplayer.
param([switch]$Build, [switch]$NoRun)
$ErrorActionPreference = 'Stop'

$Pvp = Split-Path -Parent $PSScriptRoot
$Root = Split-Path -Parent $Pvp
$Tools = Join-Path $Root '.tools'

function Say($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }

$env:RUSTUP_HOME = Join-Path $Tools 'rustup'
$env:CARGO_HOME = Join-Path $Tools 'cargo'
$env:PATH = "$(Join-Path $Tools 'bin');$(Join-Path $env:CARGO_HOME 'bin');$(Join-Path $Tools 'cmake\bin');$env:PATH"
if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) { throw 'No Rust toolchain — run scripts\setup-windows.ps1 first.' }

$jar = Join-Path $Pvp 'mod\build\libs\void-client-0.1.0-windows-x64.jar'

if ($Build) {
	Say 'Building the in-game bundle'
	Push-Location $Pvp
	try {
		& pnpm --filter @void/ingame build
		if ($LASTEXITCODE -ne 0) { throw 'in-game build failed' }
	} finally { Pop-Location }

	Say 'Building the mod jar'
	$env:JAVA_HOME = Join-Path $Tools 'jdk-21'
	Push-Location (Join-Path $Pvp 'mod')
	try {
		& .\gradlew.bat build --no-daemon
		if ($LASTEXITCODE -ne 0) { throw 'gradle build failed' }
	} finally { Pop-Location }
}

# Point the launcher at the jar, keeping anything else already in config.json.
if (Test-Path $jar) {
	$home_ = if ($env:VOID_PVP_HOME) { $env:VOID_PVP_HOME } else { Join-Path $env:USERPROFILE '.void-pvp' }
	New-Item -ItemType Directory -Force -Path $home_ | Out-Null
	$configFile = Join-Path $home_ 'config.json'
	$config = if (Test-Path $configFile) { Get-Content $configFile -Raw | ConvertFrom-Json } else { New-Object PSObject }
	$config | Add-Member -NotePropertyName mod_jar -NotePropertyValue ($jar -replace '\\', '/') -Force
	# UTF-8 without a BOM: serde_json refuses a leading BOM.
	[System.IO.File]::WriteAllText($configFile, ($config | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding $false))
	Write-Host "Launches will install $jar"
} else {
	Write-Warning 'No mod jar yet — run with -Build. Launches will run vanilla 1.8.9 until there is one.'
}

if ($NoRun) { return }

Say 'Opening the launcher (first run compiles the Rust side, a few minutes)'
Push-Location (Join-Path $Pvp 'apps\desktop')
try {
	& npx tauri dev
} finally { Pop-Location }
