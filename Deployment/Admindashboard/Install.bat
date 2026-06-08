@echo off
setlocal
title ACADexa Installer

net session >nul 2>nul
if errorlevel 1 (
    echo Requesting Administrator permission for ACADexa setup...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

set "ROOT=%~dp0"
set "APP_DIR=%ROOT%Application"
set "APP_EXE=%APP_DIR%\Admindashboard.exe"
set "APP_JAR=%APP_DIR%\Admindashboard.jar"
set "WEB_SRC=%APP_DIR%\quiz_system"
set "WEB_DEST=C:\xampp\htdocs\quiz_system"
set "DB_SQL=%ROOT%Database\setup_database.sql"
set "MYSQL_EXE=C:\xampp\mysql\bin\mysql.exe"
set "XAMPP_INSTALLER="

for %%F in ("%ROOT%XAMPPInstaller\xampp*.exe") do (
    if exist "%%~fF" set "XAMPP_INSTALLER=%%~fF"
)

echo.
echo ==========================================
echo   ACADexa Easy Installer
echo ==========================================
echo.

if not exist "%APP_EXE%" (
    echo ERROR: Application\Admindashboard.exe was not found.
    echo Make sure you are running this installer from the extracted Admindashboard folder.
    pause
    exit /b 1
)

if not exist "%APP_JAR%" (
    echo ERROR: Application\Admindashboard.jar was not found.
    echo Make sure the Java application package is complete.
    pause
    exit /b 1
)

echo [1/5] Cleaning old temporary package files...
del /f /q "%APP_DIR%\Admindashboard.new.jar" >nul 2>nul
del /f /q "%APP_DIR%\Admindashboard.fixed.jar" >nul 2>nul
del /f /q "%APP_DIR%\Admindashboard.home.jar" >nul 2>nul
del /f /q "%APP_DIR%\Admindashboard.restore.jar" >nul 2>nul
del /f /q "%APP_DIR%\Admindashboard.route.jar" >nul 2>nul
del /f /q "%APP_DIR%\jartmp*.tmp" >nul 2>nul
del /f /q "%APP_DIR%\CSC*.TMP" >nul 2>nul

echo [2/5] Checking XAMPP folder...
if not exist "C:\xampp\htdocs" (
    echo XAMPP was not found at C:\xampp.
    if defined XAMPP_INSTALLER (
        echo Found bundled XAMPP installer:
        echo %XAMPP_INSTALLER%
        echo.
        choice /M "Install XAMPP now"
        if errorlevel 2 (
            echo Installation cancelled. Please install XAMPP, then run this installer again.
            pause
            exit /b 1
        )
        start /wait "" "%XAMPP_INSTALLER%"
    ) else (
        echo ERROR: C:\xampp\htdocs was not found.
        echo Please install XAMPP first, then run this installer again.
        pause
        exit /b 1
    )
)

if not exist "C:\xampp\htdocs" (
    echo ERROR: C:\xampp\htdocs is still missing.
    echo Install XAMPP to C:\xampp, then run this installer again.
    pause
    exit /b 1
)

echo [3/5] Copying quiz_system files to XAMPP...
if not exist "%WEB_DEST%" mkdir "%WEB_DEST%"
xcopy "%WEB_SRC%\*" "%WEB_DEST%\" /E /I /Y >nul
if errorlevel 1 (
    echo ERROR: Could not copy quiz_system files.
    echo Try running Install.bat as Administrator.
    pause
    exit /b 1
)

echo [4/5] Importing database setup...
if exist "%MYSQL_EXE%" (
    "%MYSQL_EXE%" -u root < "%DB_SQL%"
    if errorlevel 1 (
        echo WARNING: Database import failed.
        echo Start MySQL in XAMPP, then import Database\setup_database.sql using phpMyAdmin.
    ) else (
        echo Database setup completed.
    )
) else (
    echo WARNING: MySQL command was not found at %MYSQL_EXE%.
    echo Import Database\setup_database.sql manually in phpMyAdmin.
)

echo [5/5] Creating desktop shortcut...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$desktop=[Environment]::GetFolderPath('Desktop'); $shortcut=Join-Path $desktop 'ACADexa.lnk'; if (Test-Path $shortcut) { Remove-Item $shortcut -Force }; $s=(New-Object -COM WScript.Shell).CreateShortcut($shortcut); $s.TargetPath='%APP_EXE%'; $s.WorkingDirectory='%APP_DIR%'; $s.IconLocation='%APP_EXE%,0'; $s.Save()"
if errorlevel 1 (
    echo ERROR: Could not create the desktop shortcut.
    echo Try running Install.bat as Administrator.
    pause
    exit /b 1
)

echo.
echo Installation steps completed.
echo.
echo Before running the app, open XAMPP Control Panel and start:
echo - Apache
echo - MySQL
echo.
echo Then double-click the ACADexa shortcut on your desktop,
echo or open Application\Admindashboard.exe.
echo.
pause
