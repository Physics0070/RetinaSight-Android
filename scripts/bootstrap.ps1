<#
.SYNOPSIS
  Check and install everything this project needs (Windows).

.EXAMPLE
  pwsh -File scripts\bootstrap.ps1
  pwsh -File scripts\bootstrap.ps1 -CheckOnly
  powershell -ExecutionPolicy Bypass -File scripts\bootstrap.ps1 -Yes

.NOTES
  Reads project-manifest.yml. No modules required. Uses winget, falling back
  to choco. Exit code 0 = all required deps satisfied, 1 = still missing.
#>

param(
    [switch]$CheckOnly,
    [switch]$Yes
)

$ErrorActionPreference = 'Continue'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root      = Split-Path -Parent $ScriptDir
$Manifest  = Join-Path $Root 'project-manifest.yml'
$script:Failed = $false

function Write-Head($t) { Write-Host ""; Write-Host $t -ForegroundColor White }
function Write-Ok($t)   { Write-Host $t -ForegroundColor Green }
function Write-Warn($t) { Write-Host $t -ForegroundColor Yellow }
function Write-Bad($t)  { Write-Host $t -ForegroundColor Red }

# --- minimal YAML block reader ---------------------------------------------
# Returns an array of hashtables for list items under the given top-level key.
function Read-Block([string]$Block) {
    $items = @()
    $cur   = $null
    $inb   = $false
    foreach ($raw in (Get-Content -LiteralPath $Manifest)) {
        if ($raw -match '^\s*#') { continue }
        if ($raw -match "^${Block}:\s*$") { $inb = $true; continue }
        if ($inb -and $raw -match '^[a-z_]+:') { $inb = $false }
        if (-not $inb) { continue }

        $line = $raw
        if ($line -match '^\s*-\s') {
            if ($cur) { $items += ,$cur }
            $cur  = @{}
            $line = $line -replace '^\s*-\s*', ''
        }
        $line = $line.Trim()
        if ($line -eq '') { continue }
        if ($null -eq $cur) { continue }

        if ($line -match '^([a-z_]+):\s*(.*)$') {
            $k = $Matches[1]
            $v = $Matches[2] -replace '\s+#.*$', ''
            $v = $v.Trim().Trim('"')
            if ($v -ne '') { $cur[$k] = $v }
        }
    }
    if ($cur) { $items += ,$cur }
    return $items
}

function Read-EnvKeys {
    $keys = @()
    $inb  = $false
    foreach ($raw in (Get-Content -LiteralPath $Manifest)) {
        if ($raw -match '^\s*#') { continue }
        if ($raw -match '^env:\s*$') { $inb = $true; continue }
        if ($inb -and $raw -match '^[a-z_]+:') { $inb = $false }
        if ($inb -and $raw -match '^\s*-\s*(\S+)') { $keys += $Matches[1] }
    }
    return $keys
}

function Get-VersionNumber([string]$text) {
    if ($text -match '(\d+)\.(\d+)(\.\d+)?') { return "$($Matches[1]).$($Matches[2])" }
    return $null
}

function Test-VersionOk([string]$have, [string]$want) {
    if (-not $have -or -not $want) { return $true }
    try { return ([version]$have) -ge ([version]$want) } catch { return $true }
}

# --- platform --------------------------------------------------------------
$pkgMgr = $null
if (Get-Command winget -ErrorAction SilentlyContinue) { $pkgMgr = 'winget' }
elseif (Get-Command choco -ErrorAction SilentlyContinue) { $pkgMgr = 'choco' }

Write-Head "Environment"
Write-Host ("  OS             : {0}" -f [System.Environment]::OSVersion.VersionString)
Write-Host ("  PowerShell     : {0}" -f $PSVersionTable.PSVersion)
Write-Host ("  Package manager: {0}" -f $(if ($pkgMgr) { $pkgMgr } else { 'none detected' }))
Write-Host ("  Project root   : {0}" -f $Root)

if (-not (Test-Path -LiteralPath $Manifest)) {
    Write-Bad "ERROR: $Manifest not found."
    exit 1
}

# --- system tools ----------------------------------------------------------
Write-Head "System tools"
"  {0,-14} {1,-10} {2,-14} {3}" -f 'TOOL','REQUIRED','FOUND','ACTION' | Write-Host
"  {0,-14} {1,-10} {2,-14} {3}" -f '----','--------','-----','------' | Write-Host

foreach ($tool in (Read-Block 'system')) {
    $name = $tool['name']
    $cmd  = $tool['command']
    if (-not $cmd) { continue }

    # On Windows the interpreters are usually `python` / `pip`, not python3/pip3.
    $candidates = @($cmd)
    if ($cmd -eq 'python3') { $candidates = @('python','python3','py') }
    if ($cmd -eq 'pip3')    { $candidates = @('pip','pip3') }

    $resolved = $null
    foreach ($c in $candidates) {
        $g = Get-Command $c -ErrorAction SilentlyContinue
        # Skip the Windows Store python stub, which resolves but does nothing.
        if ($g -and $g.Source -notmatch 'WindowsApps') { $resolved = $c; break }
    }

    $found  = '-'
    $status = 'MISSING'
    if ($resolved) {
        $status = 'PRESENT'
        if ($tool['version']) {
            $out = (& $resolved $tool['version'] 2>&1 | Select-Object -First 1) -join ' '
            $v   = Get-VersionNumber $out
            $found = $(if ($v) { $v } else { 'present' })
            if ($tool['min'] -and $v -and -not (Test-VersionOk $v $tool['min'])) {
                $status = 'WRONG_VERSION'
            }
        } else {
            $found = 'present'
        }
    }

    $req  = ($tool['required'] -ne 'false')
    $need = $(if ($tool['min']) { $tool['min'] } else { 'any' })

    if ($status -eq 'PRESENT') {
        "  {0,-14} {1,-10} {2,-14} " -f $name,$need,$found | Write-Host -NoNewline
        Write-Ok "ok"
        continue
    }

    $pkgId = $null
    if     ($pkgMgr -eq 'winget') { $pkgId = $tool['winget'] }
    elseif ($pkgMgr -eq 'choco')  { $pkgId = $tool['choco']; if (-not $pkgId) { $pkgId = $tool['winget'] } }

    if ($CheckOnly -or -not $pkgId -or -not $pkgMgr) {
        "  {0,-14} {1,-10} {2,-14} " -f $name,$need,$found | Write-Host -NoNewline
        Write-Bad "$status - install manually"
        if ($req) { $script:Failed = $true }
        continue
    }

    $installCmd = if ($pkgMgr -eq 'winget') {
        "winget install --id $pkgId -e --accept-source-agreements --accept-package-agreements"
    } else {
        "choco install $pkgId -y"
    }

    Write-Host ""
    Write-Warn "  $name is $status. Installing with: $installCmd"
    $go = $true
    if (-not $Yes) {
        $reply = Read-Host "  Proceed? [Y/n]"
        if ($reply -match '^[nN]') { $go = $false }
    }

    if (-not $go) {
        "  {0,-14} {1,-10} {2,-14} " -f $name,$need,$found | Write-Host -NoNewline
        Write-Warn "skipped"
        if ($req) { $script:Failed = $true }
        continue
    }

    Invoke-Expression $installCmd | Out-Host
    # winget edits PATH for new processes only; refresh it for this session.
    $env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ';' +
                [System.Environment]::GetEnvironmentVariable('Path','User')

    $recheck = $null
    foreach ($c in $candidates) {
        if (Get-Command $c -ErrorAction SilentlyContinue) { $recheck = $c; break }
    }
    "  {0,-14} {1,-10} {2,-14} " -f $name,$need,$found | Write-Host -NoNewline
    if ($recheck) {
        Write-Ok "installed"
    } else {
        Write-Bad "install ran, not yet on PATH - reopen the terminal"
        if ($req) { $script:Failed = $true }
    }
}

# --- project dependencies --------------------------------------------------
Write-Head "Project dependencies"
Push-Location $Root
$npmDone = $false
$pyDone  = $false
$ranAny  = $false

foreach ($dep in (Read-Block 'project')) {
    $file = $dep['detect']
    $inst = $dep['install']
    if (-not $file -or -not $inst) { continue }

    $eco = 'other'
    if ($file -in @('package-lock.json','pnpm-lock.yaml','yarn.lock','package.json')) { $eco = 'npm' }
    if ($file -in @('uv.lock','requirements.txt','pyproject.toml'))                   { $eco = 'py'  }
    if ($eco -eq 'npm' -and $npmDone) { continue }
    if ($eco -eq 'py'  -and $pyDone)  { continue }
    if (-not (Test-Path -LiteralPath (Join-Path $Root $file))) { continue }

    # Manifest uses POSIX names; map them to what Windows actually has.
    $inst = $inst -replace '^pip3 ', 'pip '
    $inst = $inst -replace '^python3 ', 'python '

    Write-Host "  $file found -> $inst"
    $ranAny = $true
    if ($eco -eq 'npm') { $npmDone = $true }
    if ($eco -eq 'py')  { $pyDone  = $true }

    if ($CheckOnly) {
        Write-Warn "    (check-only, not run)"
        continue
    }
    Invoke-Expression $inst | Out-Host
    if ($LASTEXITCODE -eq 0 -or $null -eq $LASTEXITCODE) { Write-Ok "    ok" }
    else { Write-Bad "    failed"; $script:Failed = $true }
}
Pop-Location

if (-not $ranAny) { Write-Warn "  No recognised dependency file in $Root - nothing to install." }

# --- env vars --------------------------------------------------------------
Write-Head "Environment variables"
$dotenv = Join-Path $Root '.env'
if (Test-Path -LiteralPath $dotenv) {
    foreach ($l in (Get-Content -LiteralPath $dotenv)) {
        if ($l -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            [System.Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim('"'), 'Process')
        }
    }
}
$envKeys = Read-EnvKeys
if ($envKeys.Count -eq 0) {
    Write-Host "  (none declared)"
} else {
    foreach ($k in $envKeys) {
        $val = [System.Environment]::GetEnvironmentVariable($k)
        "  {0,-28} " -f $k | Write-Host -NoNewline
        if ($val) { Write-Ok "set" } else { Write-Bad "MISSING - add it to .env" }
    }
}

# --- verdict ---------------------------------------------------------------
Write-Head "Result"
if ($script:Failed) {
    Write-Bad "  Some required dependencies are still missing (see above)."
    exit 1
} else {
    Write-Ok "  Environment ready."
    exit 0
}
