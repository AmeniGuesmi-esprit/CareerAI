# Réindexe la base de connaissances via la passerelle (compte ADMIN requis).
# Usage : .\scripts\rag-reindex.ps1 -Email admin@exemple.com -Password '***'
param(
    [string]$BaseUrl = 'http://localhost:8222',
    [Parameter(Mandatory = $true)][string]$Email,
    [Parameter(Mandatory = $true)][string]$Password
)
$ErrorActionPreference = 'Stop'

$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/users/auth/login" `
    -ContentType 'application/json' -Body (@{ email = $Email; motDePasse = $Password } | ConvertTo-Json)
$token = $login.data.token

$stats = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/interview/admin/rag/reindex" `
    -Headers @{ Authorization = "Bearer $token" }
$stats | ConvertTo-Json -Depth 5
