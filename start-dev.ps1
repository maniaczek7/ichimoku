$ErrorActionPreference = 'SilentlyContinue'

Write-Host "============================================"
Write-Host " Ichimoku Screener - Starting..."
Write-Host "============================================"

$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Users\RMIC\.jdks\corretto-17.0.9" }
$env:PATH = "$env:JAVA_HOME\bin;C:\Program Files\Volta;C:\Program Files\nodejs;$env:PATH"

# Kill old backend on port 8081
Write-Host "[1/4] Checking for old backend on port 8081..."
$pids = netstat -aon | Select-String ":8081\s+.*LISTENING\s+(\d+)" | ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
foreach ($p in $pids) {
    Write-Host "       Killing old backend PID $p"
    Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2

# Kill old frontend on port 4201
Write-Host "[2/4] Checking for old frontend on port 4201..."
$pids = netstat -aon | Select-String ":4201\s+.*LISTENING\s+(\d+)" | ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
foreach ($p in $pids) {
    Write-Host "       Killing old frontend PID $p"
    Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2

# Start backend
Write-Host "[3/4] Starting backend (Spring Boot on port 8081)..."
Start-Process cmd -ArgumentList '/c', 'cd /d c:\Workspace\ichimoku\backend && gradlew.bat bootRun' -WindowStyle Minimized

Write-Host "       Waiting for backend to be ready..."
do {
    Start-Sleep -Seconds 3
} while (-not (netstat -aon | Select-String ":8081\s+.*LISTENING"))
Write-Host "       Backend is up!"

# Start frontend
Write-Host "[4/4] Starting frontend (Angular on port 4201)..."
Start-Process cmd -ArgumentList '/c', 'cd /d c:\Workspace\ichimoku\frontend && npm start' -WindowStyle Minimized

Write-Host "       Waiting for frontend to be ready..."
do {
    Start-Sleep -Seconds 3
} while (-not (netstat -aon | Select-String ":4201\s+.*LISTENING"))
Write-Host "       Frontend is up!"

Write-Host "============================================"
Write-Host " Both services running!"
Write-Host " Backend:  http://localhost:8081"
Write-Host " Frontend: http://localhost:4201"
Write-Host "============================================"
