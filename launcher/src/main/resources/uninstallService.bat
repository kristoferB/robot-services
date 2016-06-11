@echo off
REM This script needs administrator privileges to execute successfully.
REM Change the paths below to match your installation.

set SERVICE_NAME=LISARobotServices

REM Absolute path to Apache Commons Daemon prunsrv.exe
set PR_INSTALL=C:\Program Files\Apache\commons-daemon\amd64\prunsrv.exe

REM Uninstall the service
"%PR_INSTALL%" //DS//%SERVICE_NAME%