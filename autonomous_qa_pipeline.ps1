param(
    [string]$AvdName = "Medium_Phone_API_37.0"
)

$ErrorActionPreference = "Continue"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$emulator = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"
$apkPath = "C:\Users\LENOVO\Desktop\TenancyShield\android-app\app\build\outputs\apk\debug\app-debug.apk"
$destDir = "C:\Users\LENOVO\Desktop\TenancyShield"

Write-Host "======================================================="
Write-Host "STARTING AUTONOMOUS QA PIPELINE"
Write-Host "======================================================="

# Function to check if an emulator/device is connected
function Get-OnlineDevice {
    $lines = & $adb devices
    foreach ($line in $lines) {
        if ($line -match "^(emulator-\d+|[a-zA-Z0-9]+)\s+device$") {
            return $matches[1]
        }
    }
    return $null
}

# Step 1: Detect Device
$device = Get-OnlineDevice
if (-not $device) {
    Write-Host "Waiting for device to connect..."
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 2
        $device = Get-OnlineDevice
        if ($device) { break }
    }
}

if (-not $device) {
    Write-Host "ERROR: No online Android device or emulator found."
    exit 1
}

Write-Host "Target Device: $device"

# Ensure device screen is active and unlocked
& $adb -s $device shell input keyevent KEYCODE_WAKEUP
& $adb -s $device shell wm dismiss-keyguard

# Step 2: Install / Update APK
Write-Host "Installing APK: $apkPath..."
$installRes = & $adb -s $device install -r -g $apkPath
Write-Host "Install result: $installRes"

# Step 3: Run Automated Verification Intent
Write-Host "Clearing logcat buffer..."
& $adb -s $device logcat -c

Write-Host "Launching AUTO_VERIFY_ALL test harness..."
& $adb -s $device shell am start --user 0 -W -n com.tenancyshield.app/.MainActivity --ez AUTO_VERIFY_ALL true

Write-Host "Monitoring logcat for verification results..."
$passFound = $false
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep -Seconds 2
    $logs = & $adb -s $device logcat -d -s TenancyShieldQA:I
    foreach ($line in $logs) {
        Write-Host $line
        if ($line -match "AUTO_TEST_PASS") {
            Write-Host "SUCCESS: Found AUTO_TEST_PASS!"
            $passFound = $true
            break
        }
    }
    if ($passFound) { break }
}

# Step 4: Extract Real Screenshot
$screenshotTarget = Join-Path $destDir "REAL_EMULATOR_SCREENSHOT.png"
Write-Host "Capturing live screen to $screenshotTarget..."
& $adb -s $device shell screencap -p /sdcard/real_screen.png
& $adb -s $device pull /sdcard/real_screen.png $screenshotTarget
& $adb -s $device shell rm /sdcard/real_screen.png 2>$null

# Step 5: Extract Real Forensic PDF Report
$pdfTarget = Join-Path $destDir "REAL_EMULATOR_FORENSIC_REPORT.pdf"
Write-Host "Extracting latest generated PDF report to $pdfTarget..."
$pdfList = & $adb -s $device shell "ls -t /sdcard/Android/data/com.tenancyshield.app/files/Documents/*.pdf"
$latestPdf = ($pdfList -split "`r?`n" | Where-Object { $_ -match "\.pdf$" })[0]

if ($latestPdf) {
    $latestPdf = $latestPdf.Trim()
    Write-Host "Found report: $latestPdf -> Pulling to $pdfTarget"
    & $adb -s $device pull $latestPdf $pdfTarget
} else {
    Write-Host "WARNING: No PDF found in Documents folder."
}

Write-Host "======================================================="
Write-Host "AUTONOMOUS QA EXECUTION FINISHED."
Write-Host "Pass Status: $passFound"
Write-Host "Screenshot:  $screenshotTarget"
Write-Host "Report:      $pdfTarget"
Write-Host "======================================================="
