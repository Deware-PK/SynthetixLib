<#
.SYNOPSIS
  Create a new Paper plugin project from the SynthetixLib consumer template.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File new-plugin.ps1 -Target ..\auction -Package com.acme.auction -Name Auction
.NOTES
  -Id defaults to the lower-cased -Name (plugin id, command name, jar name).
  Copies templates/consumer-plugin, renames the package, the MyPlugin* classes and the id, and removes the
  "@snippet:" / "@end" marker lines (they only matter inside the SynthetixLib repo).
#>
param(
  [Parameter(Mandatory)] [string] $Target,    # folder to create (must not exist or be empty)
  [Parameter(Mandatory)] [string] $Package,   # e.g. com.acme.auction
  [Parameter(Mandatory)] [string] $Name,      # plugin name and class prefix, e.g. Auction
  [string] $Id                                # e.g. auction
)
$ErrorActionPreference = 'Stop'
function Fail($msg) { Write-Host "new-plugin: $msg" -ForegroundColor Red; exit 1 }

if (-not $Id) { $Id = $Name.ToLowerInvariant() }
if ($Package -notmatch '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$') { Fail "package '$Package' must be lower-case with at least two parts (com.acme.auction)" }
if ($Package -eq 'com.github.dewarepk.synthetixlib' -or $Package.StartsWith('com.github.dewarepk.synthetixlib.')) {
  Fail "package '$Package' must not start with com.github.dewarepk.synthetixlib (relocation would rewrite it)"
}
if ($Name -notmatch '^[A-Z][A-Za-z0-9]*$') { Fail "name '$Name' must be a Java class prefix starting with a capital (Auction)" }
if ($Id -notmatch '^[a-z][a-z0-9_-]*$') { Fail "id '$Id' must be lower-case letters, digits, - or _" }

$template = Join-Path $PSScriptRoot '..\templates\consumer-plugin'
if (-not (Test-Path $template)) { Fail "template not found: $template" }
if ((Test-Path $Target) -and (Get-ChildItem $Target -Force | Select-Object -First 1)) { Fail "target '$Target' exists and is not empty" }
New-Item -ItemType Directory -Force $Target | Out-Null
$targetRoot = (Resolve-Path $Target).Path.TrimEnd('\')
$templateRoot = (Resolve-Path $template).Path.TrimEnd('\')

$skipDirs = @('build', '.gradle', '.kotlin', '.idea', 'run', 'out')
$textExt = @('.java', '.kts', '.yml', '.yaml', '.properties', '.md')
$markerLine = '^\s*(//|#)\s*@(snippet:\S*|end)\s*$'
$token = 'com\.example\.myplugin|MyPlugin|myplugin'
$utf8 = New-Object System.Text.UTF8Encoding($false)

# One pass over the text, so a replacement is never replaced again.
$replace = {
  param($m)
  switch -CaseSensitive ($m.Value) {
    'com.example.myplugin' { $Package }
    'MyPlugin' { $Name }
    default { $Id }
  }
}
function Rename([string] $relative) {
  [regex]::Replace($relative, $token, $replace)
}

Get-ChildItem $templateRoot -Recurse -File -Force | ForEach-Object {
  $relative = $_.FullName.Substring($templateRoot.Length + 1)
  if ($relative.Split('\') | Where-Object { $skipDirs -contains $_ } | Select-Object -First 1) { return }

  # com/example/myplugin/MyPlugin.java -> <package path>/<Name>.java
  $mapped = $relative -replace '^src\\main\\java\\com\\example\\myplugin\\', ('src\main\java\' + ($Package -replace '\.', '\') + '\')
  $mapped = (Split-Path $mapped -Parent) + '\' + (Rename (Split-Path $mapped -Leaf))
  $mapped = $mapped.TrimStart('\')
  $destination = Join-Path $targetRoot $mapped
  New-Item -ItemType Directory -Force (Split-Path $destination -Parent) | Out-Null

  if ($textExt -contains $_.Extension.ToLowerInvariant()) {
    $lines = [System.IO.File]::ReadAllText($_.FullName, $utf8) -split "(?<=\n)"
    $kept = $lines | Where-Object { $_ -notmatch $markerLine }
    $text = [regex]::Replace(($kept -join ''), $token, $replace)
    [System.IO.File]::WriteAllText($destination, $text, $utf8)
  } else {
    Copy-Item $_.FullName $destination
  }
}

Write-Host "Created $targetRoot  (package $Package, plugin $Name, id $Id)" -ForegroundColor Green
Write-Host "Next: cd `"$targetRoot`"; .\gradlew.bat build     (add -PsynthetixLocal=true for a local SynthetixLib build)"
