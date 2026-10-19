# Télécharge les modèles épinglés du module entretien (mêmes versions que docker-compose / application.yml).
$ErrorActionPreference = 'Stop'
$models = @('qwen3.5:4b', 'bge-m3')

foreach ($m in $models) {
    Write-Host "Téléchargement de $m ..."
    ollama pull $m
    if ($LASTEXITCODE -ne 0) { throw "ollama pull $m a échoué" }
}

Write-Host ''
ollama list
