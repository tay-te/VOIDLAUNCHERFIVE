# One-command local setup for the Minecraft mods in this repo (Windows, PowerShell 5+).
#
#   powershell -ExecutionPolicy Bypass -File scripts\setup-dev.ps1             # Java 25 + build + tests
#   powershell -ExecutionPolicy Bypass -File scripts\setup-dev.ps1 -Launcher   # ...and the launcher's npm packages
#
# Java 25 goes into .tools\ inside the repo (no admin rights, nothing installed system-wide) unless a
# Java 25 or newer is already on your PATH. Then: scripts\play.ps1 to launch Minecraft with both mods.
param([switch]$Launcher)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$Root = Split-Path -Parent $PSScriptRoot
$Tools = Join-Path $Root '.tools'
$JdkDir = Join-Path $Tools 'jdk-25'

function Say($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }

function Get-JavaMajor($java) {
	$out = & $java -version 2>&1 | Out-String
	if ($out -match 'version "(\d+)') { return [int]$Matches[1] }
	return 0
}

Say 'Checking tools'
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'git is required: https://git-scm.com/downloads' }

# ---- Java 25 ---------------------------------------------------------------------------------------
$JavaHome = $null
$onPath = Get-Command java -ErrorAction SilentlyContinue
if ($onPath -and (Get-JavaMajor $onPath.Source) -ge 25) {
	$JavaHome = Split-Path -Parent (Split-Path -Parent $onPath.Source)
	Write-Host "Using the Java on your PATH: $JavaHome"
} elseif (Test-Path (Join-Path $JdkDir 'bin\java.exe')) {
	$JavaHome = $JdkDir
	Write-Host 'Java 25 already in .tools\'
} else {
	$arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'aarch64' } else { 'x64' }
	Say "Downloading Java 25 (Eclipse Temurin, windows/$arch) into .tools\"
	New-Item -ItemType Directory -Force -Path $Tools | Out-Null
	$zip = Join-Path $Tools 'jdk25.zip'
	Invoke-WebRequest -Uri "https://api.adoptium.net/v3/binary/latest/25/ga/windows/$arch/jdk/hotspot/normal/eclipse" -OutFile $zip
	$unpack = Join-Path $Tools 'jdk-unpack'
	if (Test-Path $unpack) { Remove-Item -Recurse -Force $unpack }
	Expand-Archive -Path $zip -DestinationPath $unpack
	if (Test-Path $JdkDir) { Remove-Item -Recurse -Force $JdkDir }
	Move-Item -Path (Get-ChildItem $unpack -Directory | Select-Object -First 1).FullName -Destination $JdkDir
	Remove-Item -Recurse -Force $unpack, $zip
	$JavaHome = $JdkDir
}
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"
Write-Host "JAVA_HOME=$JavaHome ($(Get-JavaMajor "$JavaHome\bin\java.exe"))"

# ---- the mods --------------------------------------------------------------------------------------
Say 'Building Expanse and VOID LOD (first run downloads Minecraft 26.3 and Fabric, a few minutes)'
Push-Location (Join-Path $Root 'lod')
try {
	& .\gradlew.bat build
	if ($LASTEXITCODE -ne 0) { throw "Gradle build failed ($LASTEXITCODE)" }
} finally { Pop-Location }
Get-ChildItem (Join-Path $Root 'lod\build\libs'), (Join-Path $Root 'expanse\build\libs') -Filter *.jar -ErrorAction SilentlyContinue |
	Where-Object { $_.Name -notmatch 'sources' } | ForEach-Object { Write-Host "  $($_.FullName)" }

# ---- the launcher (optional) -----------------------------------------------------------------------
if ($Launcher) {
	Say "Installing the launcher's npm packages"
	if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { throw 'Node.js 20+ is required for the launcher: https://nodejs.org' }
	Push-Location $Root
	try { npm install; if ($LASTEXITCODE -ne 0) { throw 'npm install failed' } } finally { Pop-Location }
	Write-Host 'Start the launcher with: npm start'
}

Say 'Done'
Write-Host '  Play:   scripts\play.ps1                  (Minecraft 26.3 + Expanse + VOID LOD, F8 toggles the LOD)'
Write-Host '  Bench:  cd lod; .\gradlew.bat lodBench     (the horizon benchmark, no game)'
Write-Host '  Tests:  cd lod; .\gradlew.bat test'
