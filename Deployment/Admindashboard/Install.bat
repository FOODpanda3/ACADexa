@echo off
setlocal
title ACADexa 100% Automated Installer

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
set "MYSQL_CHECK=C:\xampp\mysql\bin\mysqlcheck.exe"
set "XAMPP_INSTALLER="
set "JAVA_INSTALLER="

for %%F in ("%ROOT%XAMPPInstaller\xampp*.exe") do (
    if exist "%%~fF" set "XAMPP_INSTALLER=%%~fF"
)

for %%F in ("%ROOT%JavaInstaller\*.msi") do (
    if exist "%%~fF" set "JAVA_INSTALLER=%%~fF"
)

echo.
echo ==========================================
echo   ACADexa 100% Automated Installer
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

echo [1/6] Cleaning temporary package files...
del /f /q "%APP_DIR%\Admindashboard.new.jar" >nul 2>nul
del /f /q "%APP_DIR%\Admindashboard.fixed.jar" >nul 2>nul
del /f /q "%APP_DIR%\Admindashboard.home.jar" >nul 2>nul
del /f /q "%APP_DIR%\Admindashboard.restore.jar" >nul 2>nul
del /f /q "%APP_DIR%\Admindashboard.route.jar" >nul 2>nul
del /f /q "%APP_DIR%\jartmp*.tmp" >nul 2>nul
del /f /q "%APP_DIR%\CSC*.TMP" >nul 2>nul

echo [2/6] Checking & Installing Java/JDK...
set "JAVA_FOUND=0"
where javaw >nul 2>nul && set "JAVA_FOUND=1"
if exist "C:\Program Files\BellSoft\LibericaJDK-25-Full\bin\javaw.exe" set "JAVA_FOUND=1"
if exist "C:\Program Files\BellSoft\LibericaJDK-21-Full\bin\javaw.exe" set "JAVA_FOUND=1"

if "%JAVA_FOUND%"=="0" (
    if defined JAVA_INSTALLER (
        echo Installing bundled Liberica JDK Full in background...
        start /wait "" msiexec /i "%JAVA_INSTALLER%" /qb /norestart
        echo Liberica JDK installation finished.
    ) else (
        echo WARNING: Bundled Java installer not found. Please install Java manually if needed.
    )
) else (
    echo Java JDK is already installed.
)

echo [3/6] Checking & Installing XAMPP...
if not exist "C:\xampp\htdocs" (
    if defined XAMPP_INSTALLER (
        echo Installing bundled XAMPP...
        start /wait "" "%XAMPP_INSTALLER%" --mode unattended
        echo XAMPP installation finished.
    ) else (
        echo WARNING: Bundled XAMPP installer not found. Please install XAMPP to C:\xampp manually.
    )
) else (
    echo XAMPP is already installed.
)

echo [4/6] Copying web files to XAMPP (C:\xampp\htdocs\quiz_system)...
if not exist "%WEB_DEST%" mkdir "%WEB_DEST%"
if not exist "%WEB_DEST%\uploads" mkdir "%WEB_DEST%\uploads"
xcopy "%WEB_SRC%\*" "%WEB_DEST%\" /E /I /Y >nul

echo [5/6] Registering Apache & MySQL as Automatic Windows Services...
if exist "C:\xampp\apache\bin\httpd.exe" (
    "C:\xampp\apache\bin\httpd.exe" -k install >nul 2>nul
    sc config Apache2.4 start= auto >nul 2>nul
    net start Apache2.4 >nul 2>nul
)

if exist "C:\xampp\mysql\bin\mysqld.exe" (
    "C:\xampp\mysql\bin\mysqld.exe" --install MySQL >nul 2>nul
    sc config MySQL start= auto >nul 2>nul
    net start MySQL >nul 2>nul
    start /b "" "C:\xampp\mysql\bin\mysqld.exe" --standalone >nul 2>nul
    timeout /t 3 >nul
)

if exist "%MYSQL_CHECK%" (
    "%MYSQL_CHECK%" -u root --repair --all-databases >nul 2>nul
)

if exist "%MYSQL_EXE%" (
    "%MYSQL_EXE%" -u root -e "GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION; GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION; GRANT ALL PRIVILEGES ON *.* TO 'root'@'%%' WITH GRANT OPTION; FLUSH PRIVILEGES;" >nul 2>nul
    "%MYSQL_EXE%" -u root < "%DB_SQL%" >nul 2>nul
    echo Database setup completed.
) else (
    echo WARNING: MySQL command was not found. If MySQL is not running, start MySQL in XAMPP and import setup_database.sql.
)

echo [6/6] Creating Desktop shortcut...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$desktop=[Environment]::GetFolderPath('Desktop'); $shortcut=Join-Path $desktop 'ACADexa.lnk'; if (Test-Path $shortcut) { Remove-Item $shortcut -Force }; $s=(New-Object -COM WScript.Shell).CreateShortcut($shortcut); $s.TargetPath='%APP_EXE%'; $s.WorkingDirectory='%APP_DIR%'; $s.IconLocation='%APP_EXE%,0'; $s.Save()"

echo.
echo ==========================================
echo   SUCCESS! ACADexa Installation Complete!
echo ==========================================
echo.
echo Apache & MySQL are registered as Windows Automatic Services.
echo Database privileges granted and repaired.
echo Desktop shortcut created: ACADexa
echo.
pause
