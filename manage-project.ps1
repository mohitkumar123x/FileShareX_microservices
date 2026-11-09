# FileShareX - Single Command to Start/Stop All Services
# Usage: .\manage-project.ps1 start | .\manage-project.ps1 stop | .\manage-project.ps1 status

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("start", "stop", "status")]
    [string]$Action
)

$root = $PSScriptRoot
if (-not $root) { $root = (Get-Location).Path }
$logDir = Join-Path $root "logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

$javaServices = @(
    @{ Name = "Eureka Server";     Port = 8761; Jar = "eureka-server\build\libs\eureka-server-1.0.0-SNAPSHOT.jar";      HealthPath = "/actuator/health" },
    @{ Name = "Config Server";     Port = 9999; Jar = "config-server\build\libs\config-server-1.0.0-SNAPSHOT.jar";      HealthPath = "/actuator/health" },
    @{ Name = "Gateway";           Port = 8080; Jar = "gateway\build\libs\gateway-1.0.0-SNAPSHOT.jar";                  HealthPath = "/actuator/health" },
    @{ Name = "User Service";      Port = 8081; Jar = "user-service\build\libs\user-service-1.0.0-SNAPSHOT.jar";        HealthPath = "/api/v1.0/actuator/health" },
    @{ Name = "File Service";      Port = 8082; Jar = "file-service\build\libs\file-service-1.0.0-SNAPSHOT.jar";        HealthPath = "/api/v1.0/actuator/health" },
    @{ Name = "Payment Service";   Port = 8083; Jar = "payment-service\build\libs\payment-service-1.0.0-SNAPSHOT.jar";  HealthPath = "/api/v1.0/actuator/health" },
    @{ Name = "Notification Svc";  Port = 8084; Jar = "notification-service\build\libs\notification-service-1.0.0-SNAPSHOT.jar"; HealthPath = "/api/v1.0/actuator/health" }
)

$dockerServices = @("filesharex-postgres-users", "filesharex-postgres-files", "filesharex-postgres-payments", "filesharex-postgres-notifications", "filesharex-prometheus", "filesharex-grafana")

function Stop-AllJavaServices {
    Write-Host "`nStopping Java services..." -ForegroundColor Yellow
    foreach ($svc in $javaServices) {
        $conns = Get-NetTCPConnection -LocalPort $svc.Port -State Listen -ErrorAction SilentlyContinue
        foreach ($conn in $conns) {
            Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
            Write-Host "  Stopped $($svc.Name) (PID: $($conn.OwningProcess))"
        }
    }
    Start-Sleep -Seconds 2
    Write-Host "Java services stopped." -ForegroundColor Green
}

function Stop-Frontend {
    Write-Host "`nStopping Frontend..." -ForegroundColor Yellow
    $conns = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
    foreach ($conn in $conns) {
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
        Write-Host "  Stopped Frontend (PID: $($conn.OwningProcess))"
    }
    $conns = Get-NetTCPConnection -LocalPort 5174 -State Listen -ErrorAction SilentlyContinue
    foreach ($conn in $conns) {
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
        Write-Host "  Stopped Frontend Docker (PID: $($conn.OwningProcess))"
    }
}

function Start-DockerServices {
    Write-Host "`nStarting Docker containers..." -ForegroundColor Cyan
    docker start $dockerServices 2>$null | Out-Null
    $running = docker ps --format "{{.Names}}" 2>$null
    foreach ($svc in $dockerServices) {
        if ($running -contains $svc) {
            Write-Host "  [OK] $svc" -ForegroundColor Green
        } else {
            Write-Host "  [!!] $svc (not found)" -ForegroundColor Yellow
        }
    }
}

function Start-Monitoring {
    Write-Host "`nStarting Monitoring stack..." -ForegroundColor Cyan
    $promRunning = docker ps --format "{{.Names}}" 2>$null | Select-String "filesharex-prometheus"
    $grafRunning = docker ps --format "{{.Names}}" 2>$null | Select-String "filesharex-grafana"

    if (-not $promRunning) {
        docker network create filesharex-monitoring 2>$null | Out-Null
        docker rm -f filesharex-prometheus 2>$null | Out-Null
        docker run -d --name filesharex-prometheus --network filesharex-monitoring -p 9090:9090 `
            -v "${root}/monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml" `
            prom/prometheus:latest --config.file=/etc/prometheus/prometheus.yml --storage.tsdb.retention.time=15d 2>$null | Out-Null
        Write-Host "  [OK] Prometheus started" -ForegroundColor Green
    } else {
        Write-Host "  [OK] Prometheus already running" -ForegroundColor Green
    }

    if (-not $grafRunning) {
        docker network create filesharex-monitoring 2>$null | Out-Null
        docker rm -f filesharex-grafana 2>$null | Out-Null
        docker run -d --name filesharex-grafana --network filesharex-monitoring -p 3000:3000 `
            -e GF_SECURITY_ADMIN_USER=admin -e GF_SECURITY_ADMIN_PASSWORD=admin `
            -v "${root}/monitoring/grafana/provisioning:/etc/grafana/provisioning" `
            -v "${root}/monitoring/grafana/dashboards:/var/lib/grafana/dashboards" `
            grafana/grafana:latest 2>$null | Out-Null
        Write-Host "  [OK] Grafana started" -ForegroundColor Green
    } else {
        Write-Host "  [OK] Grafana already running" -ForegroundColor Green
    }
}

function Start-JavaServices {
    Write-Host "`nStarting Java services..." -ForegroundColor Cyan

    # Kill existing processes on service ports
    foreach ($svc in $javaServices) {
        $conns = Get-NetTCPConnection -LocalPort $svc.Port -State Listen -ErrorAction SilentlyContinue
        foreach ($conn in $conns) {
            Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
        }
    }
    Start-Sleep -Seconds 2

    # Start services in order
    $order = @(0, 1, 2, 3, 4, 5, 6)  # Eureka, Config, Gateway, then business services
    foreach ($idx in $order) {
        $svc = $javaServices[$idx]
        $jarPath = Join-Path $root $svc.Jar
        if (Test-Path $jarPath) {
            $logFile = Join-Path $logDir "$($svc.Name.ToLower().Replace(' ', '-').Replace('svc', 'service'))-stdout.log"
            $errFile = Join-Path $logDir "$($svc.Name.ToLower().Replace(' ', '-').Replace('svc', 'service'))-stderr.log"
            Start-Process -FilePath "java" -ArgumentList "-jar","`"$jarPath`"" -WindowStyle Hidden `
                -RedirectStandardOutput $logFile -RedirectStandardError $errFile
            Write-Host "  Starting $($svc.Name)..."
        } else {
            Write-Host "  [SKIP] $($svc.Name) - JAR not found" -ForegroundColor Yellow
        }
    }

    # Wait for startup
    Write-Host "`n  Waiting for services to start..." -ForegroundColor Gray
    Start-Sleep -Seconds 30

    # Health check
    Write-Host "`n  Health Check:" -ForegroundColor Cyan
    foreach ($svc in $javaServices) {
        $up = $false
        try {
            $r = Invoke-WebRequest -Uri "http://localhost:$($svc.Port)$($svc.HealthPath)" -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -eq 200) { $up = $true }
        } catch {}
        $status = if ($up) { "[OK]" } else { "[--]" }
        $color = if ($up) { "Green" } else { "Red" }
        Write-Host "  $status $($svc.Name) (port $($svc.Port))" -ForegroundColor $color
    }
}

function Start-Frontend {
    Write-Host "`nStarting Frontend..." -ForegroundColor Cyan
    Start-Process -FilePath "cmd" -ArgumentList "/c","cd /d `"$root\frontend`" && npm run dev" -WindowStyle Minimized
    Start-Sleep -Seconds 5
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:5173" -UseBasicParsing -TimeoutSec 5
        Write-Host "  [OK] Frontend (port 5173)" -ForegroundColor Green
    } catch {
        Write-Host "  [OK] Frontend starting at http://localhost:5173" -ForegroundColor Green
    }
}

function Show-Status {
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host " FileShareX Service Status" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan

    Write-Host "`nJava Services:" -ForegroundColor Yellow
    foreach ($svc in $javaServices) {
        $up = $false
        try {
            $r = Invoke-WebRequest -Uri "http://localhost:$($svc.Port)$($svc.HealthPath)" -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -eq 200) { $up = $true }
        } catch {}
        $status = if ($up) { "[UP]" } else { "[DOWN]" }
        $color = if ($up) { "Green" } else { "Red" }
        Write-Host "  $status $($svc.Name) (port $($svc.Port))" -ForegroundColor $color
    }

    Write-Host "`nDocker Containers:" -ForegroundColor Yellow
    foreach ($svc in $dockerServices) {
        $running = docker ps --format "{{.Names}}" 2>$null | Select-String $svc
        $status = if ($running) { "[UP]" } else { "[DOWN]" }
        $color = if ($running) { "Green" } else { "Red" }
        Write-Host "  $status $svc" -ForegroundColor $color
    }

    Write-Host "`nFrontend:" -ForegroundColor Yellow
    $feUp = $false
    try { $r = Invoke-WebRequest -Uri "http://localhost:5173" -UseBasicParsing -TimeoutSec 2; $feUp = $true } catch {}
    $feStatus = if ($feUp) { "[UP]" } else { "[DOWN]" }
    $feColor = if ($feUp) { "Green" } else { "Red" }
    Write-Host "  $feStatus Frontend (port 5173)" -ForegroundColor $feColor

    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host " URLs:" -ForegroundColor Cyan
    Write-Host "  Frontend:    http://localhost:5173" -ForegroundColor White
    Write-Host "  Gateway:     http://localhost:8080" -ForegroundColor White
    Write-Host "  Eureka:      http://localhost:8761" -ForegroundColor White
    Write-Host "  Prometheus:  http://localhost:9090" -ForegroundColor White
    Write-Host "  Grafana:     http://localhost:3000 (admin/admin)" -ForegroundColor White
    Write-Host "========================================" -ForegroundColor Cyan
}

# ==================== MAIN ====================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " FileShareX Project Manager" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

switch ($Action) {
    "start" {
        Write-Host "`nStarting all services..." -ForegroundColor Green
        Start-DockerServices
        Start-Monitoring
        Start-JavaServices
        Start-Frontend
        Show-Status
        Write-Host "`nAll services started! Use '.\manage-project.ps1 status' to check." -ForegroundColor Green
    }
    "stop" {
        Write-Host "`nStopping all services..." -ForegroundColor Red
        Stop-AllJavaServices
        Stop-Frontend
        Write-Host "`nAll services stopped." -ForegroundColor Green
    }
    "status" {
        Show-Status
    }
}
