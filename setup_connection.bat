@echo off
echo ========================================================
echo  TenancyShield - Establishing ADB Reverse Port Forwarding
echo ========================================================
set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe

if not exist "%ADB_PATH%" (
    where adb >nul 2>&1
    if %errorlevel% neq 0 (
        echo [ERROR] ADB not found in %LOCALAPPDATA%\Android\Sdk\platform-tools or system PATH.
        pause
        exit /b 1
    ) else (
        set ADB_PATH=adb
    )
)

echo Checking connected devices...
"%ADB_PATH%" devices

echo.
echo Forwarding Vite Frontend (Port 5173)...
"%ADB_PATH%" reverse tcp:5173 tcp:5173

echo Forwarding FastAPI Backend (Port 8000)...
"%ADB_PATH%" reverse tcp:8000 tcp:8000

echo.
echo Active Reverse Forwards:
"%ADB_PATH%" reverse --list

echo.
echo [SUCCESS] Connections established! You can now run or reload the TenancyShield Android App without Connection Refused.
pause
