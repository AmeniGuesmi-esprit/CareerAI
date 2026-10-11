# Charge les variables du fichier .env dans la session PowerShell courante (Spring ne lit pas .env seul).
# Usage : . .\scripts\dev-env.ps1 ; cd interview-service ; mvn spring-boot:run
$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '..\.env'
if (-not (Test-Path $envFile)) {
    throw "Fichier .env introuvable. Copiez .env.example vers .env et remplissez les valeurs."
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    $idx = $line.IndexOf('=')
    if ($idx -lt 1) { return }
    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    Set-Item -Path "Env:$key" -Value $value
}

Write-Host 'Variables .env chargées dans la session.'
