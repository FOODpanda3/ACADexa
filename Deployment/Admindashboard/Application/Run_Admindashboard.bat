@echo off
cd /d "%~dp0"
set "JAVA_EXE=javaw"
if exist "C:\Program Files\BellSoft\LibericaJDK-25-Full\bin\javaw.exe" set "JAVA_EXE=C:\Program Files\BellSoft\LibericaJDK-25-Full\bin\javaw.exe"
if exist "C:\Program Files\BellSoft\LibericaJDK-21-Full\bin\javaw.exe" set "JAVA_EXE=C:\Program Files\BellSoft\LibericaJDK-21-Full\bin\javaw.exe"
"%JAVA_EXE%" -Xms64m -Xmx512m -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1 --enable-native-access=javafx.graphics --add-modules javafx.controls,javafx.fxml,javafx.swing -cp "Admindashboard.jar;lib/*" admindashboard.Admindashboard
