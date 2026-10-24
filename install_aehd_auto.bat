@echo off
echo Starting AEHD install > "C:\Users\LENOVO\Desktop\TenancyShield\aehd_install.log"
cd /d "C:\Users\LENOVO\AppData\Local\Android\Sdk\extras\google\Android_Emulator_Hypervisor_Driver"
call silent_install.bat >> "C:\Users\LENOVO\Desktop\TenancyShield\aehd_install.log" 2>&1
echo ExitCode=%ERRORLEVEL% >> "C:\Users\LENOVO\Desktop\TenancyShield\aehd_install.log"
sc query aehd >> "C:\Users\LENOVO\Desktop\TenancyShield\aehd_install.log" 2>&1
echo Done >> "C:\Users\LENOVO\Desktop\TenancyShield\aehd_install.log"
