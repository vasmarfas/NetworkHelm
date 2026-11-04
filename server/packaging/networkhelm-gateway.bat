@echo off
setlocal
rem The gateway of NetworkHelm. The password of the first start is printed in this window once; closing it stops the gateway.
rem With an option, as --reset-password, it does that and ends with the exit code of the program.
"%~dp0runtime\bin\java.exe" %JAVA_OPTS% -jar "%~dp0networkhelm-gateway.jar" %*
set code=%ERRORLEVEL%
if "%~1"=="" pause
exit /b %code%
