# Launches Minecraft 26.3 with VOID Expanse and VOID LOD from source (Windows).
# Run scripts\setup-dev.ps1 once first. Extra arguments go to Gradle; LOD settings pass through, e.g.:
#   powershell -ExecutionPolicy Bypass -File scripts\play.ps1 -Pvoid.lod.radius=16384 -Pvoid.lod.detail=4
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$JdkDir = Join-Path $Root '.tools\jdk-25'
if (Test-Path (Join-Path $JdkDir 'bin\java.exe')) {
	$env:JAVA_HOME = $JdkDir
	$env:Path = "$JdkDir\bin;$env:Path"
}
Push-Location (Join-Path $Root 'lod')
try { & .\gradlew.bat runClient @args } finally { Pop-Location }
