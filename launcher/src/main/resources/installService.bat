@echo off
REM This script needs administrator privileges to execute successfully.
REM Change the paths below to match your installation.

set SERVICE_NAME=LISARobotServices

REM Absolute path to Apache Commons Daemon prunsrv.exe
set PR_INSTALL=C:\Program Files\Apache\commons-daemon\amd64\prunsrv.exe

REM Service log configuration
set PR_LOGPREFIX=%SERVICE_NAME%
set PR_LOGPATH=c:\logs
set PR_STDOUTPUT=c:\logs\stdout.txt
set PR_STDERROR=c:\logs\stderr.txt
set PR_LOGLEVEL=Debug

REM Absolute path to Java jvm.dll
set PR_JVM=C:\Program Files\Java\jre1.8.0_92\bin\server\jvm.dll

REM Absolute path to the assembled launcher jar.
set PR_CLASSPATH=C:\Users\Daniel\Utveckling\robot-services\launcher\target\scala-2.11\launcher-assembly-1.0.jar

REM Startup configuration
set PR_STARTUP=auto
set PR_STARTMODE=jvm
set PR_STARTCLASS=robotServices.launcher.launcher
set PR_STARTMETHOD=start

REM Shutdown configuration
set PR_STOPMODE=jvm
set PR_STOPCLASS=robotServices.launcher.launcher
set PR_STOPMETHOD=stop

REM JVM configuration
set PR_JVMMS=256
set PR_JVMMX=1024
set PR_JVMSS=4000
set PR_JVMOPTIONS=-Duser.language=SV;-Duser.region=se

REM Install the service
"C:\Program Files\Apache\commons-daemon\amd64\prunsrv.exe" //IS//%SERVICE_NAME%