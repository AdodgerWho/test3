@if "%DEBUG%" == "" @echo off
@rem Gradle startup script for Windows
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_HOME=%DIRNAME%
java -Xmx64m -Xms64m -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*
