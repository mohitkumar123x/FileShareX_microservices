$root = $PSScriptRoot
if (-not $root) { $root = (Get-Location).Path }
$logDir = Join-Path $root "logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

$javaExe = "C:\Users\mohit\.antigravity-ide\extensions\redhat.java-1.56.0-win32-x64\jre\21.0.12.1-win32-x86_64\bin\java.exe"
if (-not (Test-Path $javaExe)) {
    $javaExe = "java"
}

Write-Output "=== Starting FileShareX Microservices ==="
Write-Output "Root directory : $root"
Write-Output "Java runtime   : $javaExe"

# Ensure Docker PostgreSQL containers are running
Write-Output "`n[0/7] Ensuring PostgreSQL containers are started..."
try {
    docker start filesharex-postgres-users filesharex-postgres-files filesharex-postgres-payments filesharex-postgres-notifications 2>$null | Out-Null
    Write-Output "PostgreSQL containers: STARTED"
} catch {
    Write-Output "PostgreSQL check skipped or Docker not accessible."
}

# Stop any processes on FileShareX service ports
$servicePorts = @(8761, 9999, 8080, 8081, 8082, 8083, 8084)
foreach ($port in $servicePorts) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($conn in $conns) {
        if ($conn.OwningProcess -gt 0) {
            Write-Output "Stopping existing process $($conn.OwningProcess) on port $port..."
            Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
        }
    }
}
Start-Sleep -Seconds 2

# Step 1: Eureka Server (port 8761)
Write-Output "`n[1/7] Starting Eureka Server on port 8761..."
Start-Process $javaExe -ArgumentList "-jar", "eureka-server\build\libs\eureka-server-1.0.0-SNAPSHOT.jar" -WorkingDirectory $root -NoNewWindow -RedirectStandardOutput "$logDir\eureka-stdout.log" -RedirectStandardError "$logDir\eureka-stderr.log"

# Wait for Eureka
$eurekaReady = $false
for ($i = 1; $i -le 15; $i++) {
    Start-Sleep -Seconds 3
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8761/actuator/health" -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $eurekaReady = $true; break }
    } catch {}
}
if ($eurekaReady) { Write-Output "[1/7] Eureka Server: UP" } else { Write-Output "[1/7] Eureka Server: FAILED TO START" }

# Step 2: Config Server (port 9999)
Write-Output "`n[2/7] Starting Config Server on port 9999..."
Start-Process $javaExe -ArgumentList "-jar", "config-server\build\libs\config-server-1.0.0-SNAPSHOT.jar" -WorkingDirectory $root -NoNewWindow -RedirectStandardOutput "$logDir\config-stdout.log" -RedirectStandardError "$logDir\config-stderr.log"

$cfgReady = $false
for ($i = 1; $i -le 15; $i++) {
    Start-Sleep -Seconds 3
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:9999/actuator/health" -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $cfgReady = $true; break }
    } catch {}
}
if ($cfgReady) { Write-Output "[2/7] Config Server: UP" } else { Write-Output "[2/7] Config Server: FAILED TO START" }

# Step 3: Gateway (port 8080)
Write-Output "`n[3/7] Starting Gateway on port 8080..."
Start-Process $javaExe -ArgumentList "-jar", "gateway\build\libs\gateway-1.0.0-SNAPSHOT.jar" -WorkingDirectory $root -NoNewWindow -RedirectStandardOutput "$logDir\gateway-stdout.log" -RedirectStandardError "$logDir\gateway-stderr.log"

# Step 4: User Service (port 8081)
Write-Output "`n[4/7] Starting User Service on port 8081..."
Start-Process $javaExe -ArgumentList "-jar", "user-service\build\libs\user-service-1.0.0-SNAPSHOT.jar" -WorkingDirectory $root -NoNewWindow -RedirectStandardOutput "$logDir\user-stdout.log" -RedirectStandardError "$logDir\user-stderr.log"

# Step 5: File Service (port 8082)
Write-Output "`n[5/7] Starting File Service on port 8082..."
Start-Process $javaExe -ArgumentList "-jar", "file-service\build\libs\file-service-1.0.0-SNAPSHOT.jar" -WorkingDirectory $root -NoNewWindow -RedirectStandardOutput "$logDir\file-stdout.log" -RedirectStandardError "$logDir\file-stderr.log"

# Step 6: Payment Service (port 8083)
Write-Output "`n[6/7] Starting Payment Service on port 8083..."
Start-Process $javaExe -ArgumentList "-jar", "payment-service\build\libs\payment-service-1.0.0-SNAPSHOT.jar" -WorkingDirectory $root -NoNewWindow -RedirectStandardOutput "$logDir\payment-stdout.log" -RedirectStandardError "$logDir\payment-stderr.log"

# Step 7: Notification Service (port 8084)
Write-Output "`n[7/7] Starting Notification Service on port 8084..."
Start-Process $javaExe -ArgumentList "-jar", "notification-service\build\libs\notification-service-1.0.0-SNAPSHOT.jar" -WorkingDirectory $root -NoNewWindow -RedirectStandardOutput "$logDir\notification-stdout.log" -RedirectStandardError "$logDir\notification-stderr.log"

# Wait for services
Write-Output "`nWaiting 25s for services to initialize..."
Start-Sleep -Seconds 25

# Health check
Write-Output "`n=== Service Health Check ==="
$services = @(
    @{ Name = "Eureka Server"; Port = 8761 },
    @{ Name = "Config Server"; Port = 9999 },
    @{ Name = "Gateway"; Port = 8080 },
    @{ Name = "User Service"; Port = 8081 },
    @{ Name = "File Service"; Port = 8082 },
    @{ Name = "Payment Service"; Port = 8083 },
    @{ Name = "Notification Service"; Port = 8084 }
)
foreach ($s in $services) {
    $up = $false
    $paths = @("/actuator/health", "/api/v1.0/actuator/health")
    foreach ($p in $paths) {
        try {
            $r = Invoke-WebRequest -Uri "http://localhost:$($s.Port)$p" -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200) { $up = $true; break }
        } catch {}
    }
    if ($up) {
        Write-Output "  [OK] $($s.Name) (port $($s.Port)): UP"
    } else {
        Write-Output "  [WAIT] $($s.Name) (port $($s.Port)): Starting or check logs"
    }
}

Write-Output "`n=== Log files available in: $logDir ==="
Write-Output "FileShareX microservices running. Entering background monitoring..."

while ($true) {
    Start-Sleep -Seconds 60
}
