$ErrorActionPreference = 'SilentlyContinue'

Write-Host "============================================"
Write-Host " Ichimoku Screener - Stopping..."
Write-Host "============================================"

Write-Host "[1/2] Stopping backend (port 8081)..."
$pids = netstat -aon | Select-String ":8081\s+.*LISTENING\s+(\d+)" | ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
if ($pids) {
    foreach ($p in $pids) {
        Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
        Write-Host "       Killed PID $p"
    }
} else {
    Write-Host "       Not running."
}

Write-Host "[2/2] Stopping frontend (port 4201)..."
$pids = netstat -aon | Select-String ":4201\s+.*LISTENING\s+(\d+)" | ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
if ($pids) {
    foreach ($p in $pids) {
        Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
        Write-Host "       Killed PID $p"
    }
} else {
    Write-Host "       Not running."
}

Write-Host "============================================"
Write-Host " All services stopped."
Write-Host "============================================"
