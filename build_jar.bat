@echo off
setlocal
set GRADLE_CMD=D:\Server\releases\Plugins\HakuneAPI\gradle-8.10.2\bin\gradle.bat
"%GRADLE_CMD%" build --no-daemon
endlocal
