$ErrorActionPreference = 'SilentlyContinue'

Write-Host "============================================"
Write-Host " Auta Search - Starting..."
Write-Host "============================================"

Write-Host "[1/2] Checking for old auta-search on port 8777..."
$pids = netstat -aon | Select-String ":8777\s+.*LISTENING\s+(\d+)" | ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
foreach ($p in $pids) {
    Write-Host "       Killing old process PID $p"
    Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2

Write-Host "[2/2] Starting auta-search server (port 8777)..."
Start-Process cmd -ArgumentList '/c', 'cd /d c:\Workspace\ichimoku\auta-search && py server.py' -WindowStyle Minimized

Write-Host "       Waiting for server to be ready..."
do {
    Start-Sleep -Seconds 2
} while (-not (netstat -aon | Select-String ":8777\s+.*LISTENING"))
Write-Host "       Server is up!"

Write-Host "============================================"
Write-Host " Auta Search running!"
Write-Host " URL: http://localhost:8777"
Write-Host "============================================"
