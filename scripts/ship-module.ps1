<#
.SYNOPSIS
  Ship a finished module: verify, commit, tag, push. Run by the agent at the end of a `Ship: yes` task.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/ship-module.ps1 -Module command -Version 0.3.0
#>
param(
  [Parameter(Mandatory)] [string] $Module,    # e.g. command  (matches "Module: synthetixlib-command" in task headers)
  [Parameter(Mandatory)] [string] $Version,   # e.g. 0.3.0
  [switch] $NoPush
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
function Fail($msg) { Write-Host "SHIP ABORTED: $msg" -ForegroundColor Red; exit 1 }

# 1. Every task of this module must be done (or review = the wrap-up task itself)
$open = Get-ChildItem docs/plan/tasks -Filter 'P*.md' | Where-Object {
  $t = (Get-Content $_.FullName -TotalCount 6) -join "`n"
  ($t -match "Module:[^\r\n]*synthetixlib-$Module\b") -and ($t -notmatch 'Status:\s*(done|review)')
}
if ($open) { Fail ("unfinished tasks: " + ($open.Name -join ', ')) }

# 2. Must be on main, in sync with origin
$branch = (git rev-parse --abbrev-ref HEAD).Trim()
if ($branch -ne 'main') { Fail "on branch '$branch', expected main" }
git fetch origin main --quiet
if ((git rev-list --count HEAD..origin/main).Trim() -ne '0') { Fail 'origin/main has commits you do not have; pull first' }
if (git tag -l "v$Version") { Fail "tag v$Version already exists" }

# 3. Verify (never ship red)
& ./gradlew.bat check smokeTest publishToMavenLocal
if ($LASTEXITCODE -ne 0) { Fail 'gradle verify failed' }
foreach ($v in Get-ChildItem synthetixlib-example/run -Directory -Filter 'smoke-*') {
  $r = Get-Content (Join-Path $v.FullName 'selftest-result.json') -Raw | ConvertFrom-Json
  if ($r.status -ne 'PASS') { Fail "smoke $($v.Name): $($r.failed) failed" }
  Write-Host "smoke $($v.Name): $($r.total)/$($r.total) PASS"
}

# 4. Commit, tag, push
git add -A
if (-not (git diff --cached --name-only)) { Fail 'nothing to commit' }
git commit -m "feat($Module): module complete, $Version" -m "Verified: gradlew check smokeTest publishToMavenLocal (Paper 1.21.11 + 26.x)."
git tag -a "v$Version" -m "SynthetixLib $Version ($Module)"
if ($NoPush) { Write-Host "Committed + tagged v$Version (not pushed)"; exit 0 }
git push origin main --follow-tags
if ($LASTEXITCODE -ne 0) { Fail 'push failed (commit + tag are local; run: git push origin main --follow-tags)' }
Write-Host "SHIPPED $Module v$Version" -ForegroundColor Green
