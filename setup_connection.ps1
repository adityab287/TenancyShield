Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " TenancyShield - Establishing ADB Reverse Port Forwarding" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCmd) {
        $adb = "adb"
    } else {
        Write-Error "ADB was not found at $env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe or in system PATH."
        exit 1
    }
}

Write-Host "Checking connected devices..." -ForegroundColor Yellow
& $adb devices

Write-Host "`nSetting up reverse forwarding for ports 5173 (Vite) and 8000 (FastAPI)..." -ForegroundColor Yellow
& $adb reverse tcp:5173 tcp:5173
& $adb reverse tcp:8000 tcp:8000

Write-Host "`nActive Reverse Port Forwards:" -ForegroundColor Green
& $adb reverse --list

Write-Host "`n[SUCCESS] Connected! Device localhost (127.0.0.1) is now routed to your PC's dev servers." -ForegroundColor Green
