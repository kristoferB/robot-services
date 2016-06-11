@echo off
REM Change the paths below to match your installation.

set SERVICE_NAME=LISARobotServices

REM Absolute path to Apache Commons Daemon prunsrv.exe
set PR_INSTALL=C:\Program Files\Apache\commons-daemon\amd64\prunsrv.exe

REM Run the service in debugging mode (in a console window, for testing purposes)
"%PR_INSTALL%" //TS//%SERVICE_NAME%