# Resume ERP Phase 1 Docker build after transient Maven Central download failures.
# Run from the ERP project root in Windows PowerShell or PowerShell 7.
# This does not delete data, edit source, or change .env.
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -Path '.\compose.yaml' -PathType Leaf)) {
    Write-Error 'compose.yaml not found. Run this script from your ERP project folder.'
    exit 1
}

& docker compose version
if ($LASTEXITCODE -ne 0) {
    Write-Error 'Docker Compose is not available. Start Docker Desktop and retry.'
    exit 1
}

$maxAttempts = 2
foreach ($service in @('notification', 'identity')) {
    $ok = $false
    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        Write-Host "`nBuilding $service (attempt $attempt of $maxAttempts)..." -ForegroundColor Cyan
        & docker compose build $service
        if ($LASTEXITCODE -eq 0) {
            $ok = $true
            break
        }
        if ($attempt -lt $maxAttempts) {
            Write-Warning "Build failed for $service. Waiting 10 seconds before retry."
            Start-Sleep -Seconds 10
        }
    }
    if (-not $ok) {
        Write-Error "Build of $service still fails. Check internet/VPN/proxy access to repo.maven.apache.org and share the latest Maven error."
        exit 1
    }
}

Write-Host "`nStarting previously built ERP containers without triggering another build..." -ForegroundColor Cyan
& docker compose up -d --no-build
if ($LASTEXITCODE -ne 0) {
    Write-Error 'Container start failed. Run: docker compose logs --tail=100'
    exit 1
}

Write-Host "`nContainers:" -ForegroundColor Green
& docker compose ps
Write-Host "`nIf all services are up, run: py scripts/smoke_test.py" -ForegroundColor Green
