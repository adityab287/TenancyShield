@echo off
echo ========================================================
echo Installing Android Emulator Hypervisor Driver (AEHD)...
echo ========================================================
cd /d "C:\Users\LENOVO\AppData\Local\Android\Sdk\extras\google\Android_Emulator_Hypervisor_Driver"
call silent_install.bat
echo.
sc query aehd
echo.
echo ========================================================
echo If service state is RUNNING, the emulator is ready!
echo ========================================================
pause
