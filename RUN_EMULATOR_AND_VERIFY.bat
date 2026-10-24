@echo off
echo ========================================================
echo TenancyShield Autonomous Verification Runner
echo ========================================================

set ADB="C:\Users\LENOVO\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set EMULATOR="C:\Users\LENOVO\AppData\Local\Android\Sdk\emulator\emulator.exe"
set APK="c:\Users\LENOVO\Desktop\TenancyShield\android-app\app\build\outputs\apk\debug\app-debug.apk"

echo [1/5] Checking connected devices...
%ADB% devices

echo [2/5] Booting Android Studio Emulator (Medium_Phone_API_37.0)...
start "" %EMULATOR% -avd Medium_Phone_API_37.0 -camera-back virtualscene -camera-front webcam0 -no-snapshot-load

echo [3/5] Waiting for emulator to boot...
%ADB% wait-for-device
:wait_boot
for /f "tokens=*" %%a in ('%ADB% shell getprop sys.boot_completed 2^>nul') do set BOOT=%%a
if not "%BOOT%"=="1" (
    timeout /t 3 /nobreak >nul
    goto wait_boot
)
echo Emulator is booted and online!

echo [4/5] Installing app-debug.apk with all permissions...
%ADB% install -r -g %APK%

echo [5/5] Launching Automated Verification Test Harness...
%ADB% logcat -c
%ADB% shell am start -W -n com.tenancyshield.app/.MainActivity --ez AUTO_VERIFY_ALL true

echo.
echo ========================================================
echo Logcat verification output:
echo ========================================================
%ADB% logcat -d -s TenancyShieldQA:I TenancyShieldBridge:D MainActivity:D
echo.
pause
