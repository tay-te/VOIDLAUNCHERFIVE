<#
.SYNOPSIS
  void-pvp — run every local check, in order, with one pass/fail summary.

.DESCRIPTION
  The PowerShell twin of scripts/verify-all.sh. Same checks, same order, same
  exit codes: 0 when everything passed, 1 when anything failed.

  What it does NOT do, on purpose:
    * nothing here needs a display, so no OpenGL path is exercised. The GL
      GPUDriver, the menu backdrop blur and the crosshair are only provable with
      a real game on a real machine — see docs/TESTING.md, step 4.
    * it never downloads Minecraft and never launches a JVM.

.PARAMETER KeepGoing
  Run every check even after one fails, and report at the end.

.PARAMETER WithNative
  Also CMake-build mod/native (downloads the Ultralight SDK on a cold machine).

.EXAMPLE
  .\scripts\verify-all.ps1
  .\scripts\verify-all.ps1 -KeepGoing
  .\scripts\verify-all.ps1 -WithNative
#>
[CmdletBinding()]
param(
  [switch]$KeepGoing,
  [switch]$WithNative
)

$ErrorActionPreference = 'Continue'

$pvp = Split-Path -Parent $PSScriptRoot
Set-Location $pvp

$script:Steps = @()
$script:Failed = 0
$script:StepNo = 0

function Write-Section([string]$Title) {
  $script:StepNo++
  Write-Host ''
  Write-Host ("== {0}. {1}" -f $script:StepNo, $Title) -ForegroundColor White
}

function Invoke-Check {
  param(
    [Parameter(Mandatory)][string]$Label,
    [Parameter(Mandatory)][string]$Command,
    [string]$WorkDir = '.'
  )
  Write-Host "`$ $Command" -ForegroundColor DarkGray
  $started = Get-Date
  Push-Location $WorkDir
  $global:LASTEXITCODE = 0
  try {
    # Run in this session (no child powershell.exe) so pnpm/gradlew shims and
    # the caller's PATH apply. A PowerShell-level error counts as exit 1.
    Invoke-Expression $Command
    $code = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
  } catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    $code = 1
  } finally {
    Pop-Location
  }
  $elapsed = [int]((Get-Date) - $started).TotalSeconds
  if ($code -eq 0) {
    $script:Steps += [pscustomobject]@{ Result = 'PASS'; Label = $Label; Time = "${elapsed}s" }
    Write-Host ("  OK   {0} ({1}s)" -f $Label, $elapsed) -ForegroundColor Green
  } else {
    $script:Steps += [pscustomobject]@{ Result = 'FAIL'; Label = $Label; Time = "${elapsed}s" }
    $script:Failed++
    Write-Host ("  FAIL {0} (exit {1})" -f $Label, $code) -ForegroundColor Red
    if (-not $KeepGoing) {
      Write-Summary
      exit 1
    }
  }
}

function Add-Skip([string]$Label, [string]$Why) {
  $script:Steps += [pscustomobject]@{ Result = 'SKIP'; Label = $Label; Time = '-' }
  Write-Host ("  --   {0} skipped: {1}" -f $Label, $Why) -ForegroundColor Yellow
}

function Write-Summary {
  Write-Host ''
  Write-Host '== summary' -ForegroundColor White
  foreach ($s in $script:Steps) {
    $colour = switch ($s.Result) { 'PASS' { 'Green' } 'FAIL' { 'Red' } default { 'Yellow' } }
    Write-Host ("{0,-5} {1,-46} {2,6}" -f $s.Result, $s.Label, $s.Time) -ForegroundColor $colour
  }
  Write-Host ''
  if ($script:Failed -eq 0) {
    Write-Host 'All checks passed.' -ForegroundColor Green
  } else {
    Write-Host ("{0} check(s) failed." -f $script:Failed) -ForegroundColor Red
  }
}

function Test-Tool([string]$Name) { $null -ne (Get-Command $Name -ErrorAction SilentlyContinue) }

# ---------------------------------------------------------------------------
Write-Section 'toolchain'
# ---------------------------------------------------------------------------
$missing = $false
foreach ($tool in 'cargo', 'node', 'pnpm', 'java') {
  if (Test-Tool $tool) {
    $v = (& $tool --version 2>&1 | Select-Object -First 1)
    Write-Host ("  {0,-6} {1}" -f $tool, $v)
  } else {
    Write-Host ("  {0,-6} MISSING" -f $tool) -ForegroundColor Red
    $missing = $true
  }
}
if (Test-Tool cmake) { Write-Host ("  {0,-6} {1}" -f 'cmake', ((cmake --version) | Select-Object -First 1)) }
if ($missing) {
  Write-Error 'Install the missing tools first — see pvp/README.md, Prerequisites.'
  exit 1
}
if (-not (Test-Path 'node_modules')) {
  Invoke-Check -Label 'pnpm install' -Command 'pnpm install --frozen-lockfile'
}

# ---------------------------------------------------------------------------
Write-Section 'schema — the contracts (schema/validate.mjs)'
# ---------------------------------------------------------------------------
node --input-type=module -e "await import.meta.resolve('ajv')" 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
  Invoke-Check -Label 'schema examples + cross-checks' -Command 'node validate.mjs' -WorkDir 'schema'
} else {
  Add-Skip 'schema examples + cross-checks' "ajv not resolvable — run 'cd schema; npm i --no-save ajv'"
}

# ---------------------------------------------------------------------------
Write-Section 'rust — crates/ (void-core, void-bridge, void-loadout)'
# ---------------------------------------------------------------------------
Invoke-Check -Label 'cargo build --workspace'  -Command 'cargo build --workspace'
Invoke-Check -Label 'cargo test --workspace'   -Command 'cargo test --workspace'
Invoke-Check -Label 'cargo clippy -D warnings' -Command 'cargo clippy --workspace --all-targets -- -D warnings'

# ---------------------------------------------------------------------------
Write-Section 'rust — apps/desktop/src-tauri (its own workspace)'
# ---------------------------------------------------------------------------
Invoke-Check -Label 'desktop cargo check'           -Command 'cargo check --all-targets'        -WorkDir 'apps/desktop/src-tauri'
Invoke-Check -Label 'desktop cargo check (no-deps)' -Command 'cargo check --no-default-features' -WorkDir 'apps/desktop/src-tauri'
Invoke-Check -Label 'desktop cargo test'            -Command 'cargo test'                        -WorkDir 'apps/desktop/src-tauri'

# ---------------------------------------------------------------------------
Write-Section 'web — protocol, ui, ingame, desktop'
# ---------------------------------------------------------------------------
Invoke-Check -Label 'pnpm -r typecheck'       -Command 'pnpm -r typecheck'
Invoke-Check -Label 'pnpm -r test'            -Command 'pnpm -r test'
Invoke-Check -Label 'pnpm -r build'           -Command 'pnpm -r build'
Invoke-Check -Label 'ingame Ultralight guard' -Command 'pnpm --filter @void/ingame lint:ultralight'
Invoke-Check -Label 'ingame size budget'      -Command 'pnpm --filter @void/ingame size'

# ---------------------------------------------------------------------------
Write-Section 'mod — Legacy Fabric / Java 8 bytecode'
# ---------------------------------------------------------------------------
$javaOut = (& java -version 2>&1) -join "`n"
$javaMajor = 0
if ($javaOut -match 'version "(\d+)') { $javaMajor = [int]$Matches[1] }
if ($javaMajor -ge 17) {
  Invoke-Check -Label 'gradlew build (compile + JUnit suite)' -Command '.\gradlew.bat build' -WorkDir 'mod'
} else {
  Add-Skip 'gradlew build' "needs a JDK 17+ to run Gradle (found $javaMajor)"
}

# ---------------------------------------------------------------------------
Write-Section 'native — the Ultralight JNI binding (CPU renderer only)'
# ---------------------------------------------------------------------------
if ($WithNative) {
  Invoke-Check -Label 'native cmake build' -Command '.\scripts\build.ps1' -WorkDir 'mod/native'
}
if ((Test-Tool cmake) -and (Test-Path 'mod/native/build/CMakeCache.txt')) {
  # CpuRenderTest: 21 checks against the real engine, no display needed.
  # Nothing here touches OpenGL — that is the M1 gate, on a real machine.
  Invoke-Check -Label 'ctest cpu_render' -Command 'ctest --test-dir mod/native/build -C Release --output-on-failure'
} else {
  Add-Skip 'ctest cpu_render' 'no configured build in mod/native/build — run scripts\verify-all.ps1 -WithNative'
}

Write-Summary
if ($script:Failed -ne 0) { exit 1 }
exit 0
