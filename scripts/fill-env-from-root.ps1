# Remplit les clés vides de .env à partir des fichiers racine application.properties / application.yml.
# N'écrit que les clés vides et n'affiche jamais de valeur. Usage : .\scripts\fill-env-from-root.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
if (-not (Test-Path $envFile)) { throw ".env introuvable : copiez d'abord .env.example vers .env" }

function Get-Unquoted([string]$v) {
    if ($v.Length -ge 2 -and ($v[0] -eq '"' -or $v[0] -eq "'") -and $v[-1] -eq $v[0]) { return $v.Substring(1, $v.Length - 2) }
    return $v
}

function Read-Settings([string]$path) {
    $map = [ordered]@{}
    if (-not (Test-Path $path)) { return $map }
    $isYaml = $path -like '*.yml' -or $path -like '*.yaml'
    $stack = New-Object System.Collections.ArrayList
    foreach ($raw in [IO.File]::ReadAllLines($path)) {
        if ($raw -match '^\s*(#|!|$)') { continue }
        if ($isYaml) {
            if ($raw -match '^(\s*)([\w.\-]+):\s*(.*)$') {
                $indent = $matches[1].Length; $key = $matches[2]; $val = $matches[3].Trim()
                while ($stack.Count -gt 0 -and $stack[$stack.Count - 1].Indent -ge $indent) { $stack.RemoveAt($stack.Count - 1) }
                $full = (@($stack | ForEach-Object { $_.Key }) + $key) -join '.'
                if ($val -eq '') { [void]$stack.Add([pscustomobject]@{ Indent = $indent; Key = $key }) }
                else { $map[$full] = Get-Unquoted $val }
            }
        } elseif ($raw -match '^\s*([^=:]+?)\s*[=:]\s*(.*)$') {
            $map[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
    return $map
}

$sources = @(
    @{ File = 'application.properties'; Map = (Read-Settings (Join-Path $root 'application.properties')) },
    @{ File = 'application.yml';        Map = (Read-Settings (Join-Path $root 'application.yml')) }
)

$targets = [ordered]@{
    JWT_SECRET     = '(?i)jwt.*secret$'
    MAIL_USERNAME  = '(?i)mail\.username$'
    MAIL_PASSWORD  = '(?i)mail\.password$'
    ADMIN_EMAIL    = '(?i)admin\.email$'
    ADMIN_PASSWORD = '(?i)admin\.password$'
}

$found = [ordered]@{}
foreach ($envKey in $targets.Keys) {
    foreach ($src in $sources) {
        $hit = $src.Map.Keys | Where-Object { $_ -match $targets[$envKey] -and $src.Map[$_] -ne '' -and $src.Map[$_] -notmatch '^\$\{' } | Select-Object -First 1
        if ($hit) { $found[$envKey] = @{ Value = $src.Map[$hit]; File = $src.File; Key = $hit }; break }
    }
}

$text = [IO.File]::ReadAllText($envFile)
$nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
$lines = $text -split "\r?\n"
$report = @()
$changed = $false

for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -notmatch '^([A-Z_][A-Z0-9_]*)=(.*)$') { continue }
    $envKey = $matches[1]
    if (-not $targets.Contains($envKey) -or $matches[2] -ne '') { continue }
    if (-not $found.Contains($envKey)) { $report += "$envKey : introuvable dans les fichiers racine, à remplir à la main"; continue }
    $value = $found[$envKey].Value
    if ($value.Contains("'") -and ($value.Contains('"') -or $value.Contains('$'))) {
        $report += "$envKey : valeur avec guillemets, à remplir à la main"; continue
    }
    $quoted = if ($value.Contains("'")) { '"' + $value + '"' } else { "'" + $value + "'" }
    $lines[$i] = "$envKey=$quoted"
    $changed = $true
    $report += "$envKey : rempli depuis $($found[$envKey].File) (clé $($found[$envKey].Key))"
}

foreach ($envKey in $targets.Keys) {
    if (-not ($lines | Where-Object { $_ -like "$envKey=?*" })) { continue }
    if (-not ($report | Where-Object { $_ -like "$envKey :*" })) { $report += "$envKey : déjà renseigné, inchangé" }
}

if ($changed) {
    [IO.File]::WriteAllText($envFile, ($lines -join $nl), (New-Object System.Text.UTF8Encoding($false)))
}
$report
