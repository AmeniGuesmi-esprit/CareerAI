# Test de bout en bout du module entretien à travers la passerelle (PowerShell 7+ requis pour -Form).
# Usage : .\scripts\smoke-test.ps1 -Email admin@exemple.com -Password '***' -CvPath .\cv-test.pdf
param(
    [string]$BaseUrl = 'http://localhost:8222',
    [Parameter(Mandatory = $true)][string]$Email,
    [Parameter(Mandatory = $true)][string]$Password,
    [Parameter(Mandatory = $true)][string]$CvPath,
    [int]$TimeoutSeconds = 900
)
$ErrorActionPreference = 'Stop'

function Step($label) { Write-Host "-> $label" -ForegroundColor Cyan }
function Wait-Until($label, [scriptblock]$condition) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $result = & $condition
        if ($result) { return $result }
        Start-Sleep -Seconds 3
    }
    throw "Délai dépassé : $label"
}

$token = (Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/users/auth/login" -ContentType 'application/json' `
    -Body (@{ email = $Email; motDePasse = $Password } | ConvertTo-Json)).data.token
$h = @{ Authorization = "Bearer $token" }
$json = 'application/json'
Step 'Connexion OK'

Step 'Upload du CV'
$cv = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/interview/cvs" -Headers $h `
    -Form @{ file = Get-Item $CvPath; label = 'smoke-test' }
$cvId = $cv.id

Step 'Création de la session'
$jd = "Nous recherchons un développeur backend Java Spring Boot pour des microservices avec Eureka, JPA et Docker. Expérience de 2 ans minimum."
$session = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/interview/sessions" -Headers $h -ContentType $json `
    -Body (@{ cvId = $cvId; jobTitle = 'Dev backend Java'; jobDescription = $jd; questionCount = 4 } | ConvertTo-Json)
$sid = $session.id

Step 'Attente des questions (READY)'
$session = Wait-Until 'génération des questions' {
    $s = Invoke-RestMethod -Uri "$BaseUrl/api/interview/sessions/$sid" -Headers $h
    if ($s.status -eq 'FAILED') { throw "Génération en échec : $($s.errorCode)" }
    if ($s.status -eq 'READY') { $s }
}
if ($session.questions.Count -ne 4) { throw "4 questions attendues, reçu $($session.questions.Count)" }
$qid = $session.questions[0].id

Step 'Réponse et attente du feedback'
Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/interview/sessions/$sid/questions/$qid/answer" -Headers $h `
    -ContentType $json -Body (@{ content = 'Dans mon projet, j''ai utilisé une HashMap pour un cache, puis TreeMap pour un ordre trié.' } | ConvertTo-Json) | Out-Null
$fb = Wait-Until 'feedback' {
    $s = Invoke-RestMethod -Uri "$BaseUrl/api/interview/sessions/$sid" -Headers $h
    $f = ($s.questions | Where-Object { $_.id -eq $qid }).answer.feedback
    if ($f.status -eq 'FAILED') { throw "Feedback en échec : $($f.errorCode)" }
    if ($f.status -eq 'READY') { $f }
}
if ($null -eq $fb.score -or $fb.score -lt 0 -or $fb.score -gt 10) { throw 'Score de feedback invalide' }

Step 'Modification de la réponse (le feedback doit être recalculé)'
Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/interview/sessions/$sid/questions/$qid/answer" -Headers $h `
    -ContentType $json -Body (@{ content = 'Version modifiée : j''ai comparé HashMap, TreeMap et LinkedHashMap selon l''ordre attendu.' } | ConvertTo-Json) | Out-Null
Wait-Until 'nouveau feedback' {
    $s = Invoke-RestMethod -Uri "$BaseUrl/api/interview/sessions/$sid" -Headers $h
    ($s.questions | Where-Object { $_.id -eq $qid }).answer.feedback.status -eq 'READY'
} | Out-Null

Step 'Suppression de la réponse'
Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/interview/sessions/$sid/questions/$qid/answer" -Headers $h | Out-Null
$after = Invoke-RestMethod -Uri "$BaseUrl/api/interview/sessions/$sid" -Headers $h
if (($after.questions | Where-Object { $_.id -eq $qid }).answer) { throw 'La réponse existe encore' }

Step 'Non-régression : compatibilité (liste)'
Invoke-RestMethod -Uri "$BaseUrl/api/compatibility/analyses" -Headers $h | Out-Null

Step 'Suppression de la session et du CV'
Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/interview/sessions/$sid" -Headers $h | Out-Null
Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/interview/cvs/$cvId" -Headers $h | Out-Null

Write-Host 'Smoke test OK' -ForegroundColor Green
