$ErrorActionPreference = 'SilentlyContinue'

Write-Host "============================================"
Write-Host " Auta Search - Stopping..."
Write-Host "============================================"

Write-Host "[1/1] Stopping auta-search (port 8777)..."
$pids = netstat -aon | Select-String ":8777\s+.*LISTENING\s+(\d+)" | ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
if ($pids) {
    foreach ($p in $pids) {
        Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
        Write-Host "       Killed PID $p"
    }
} else {
    Write-Host "       Not running."
}

Write-Host "============================================"
Write-Host " Auta Search stopped."
Write-Host "============================================"
