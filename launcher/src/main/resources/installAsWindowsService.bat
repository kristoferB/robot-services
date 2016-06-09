REM This script needs administrator privileges to execute successfully.
REM Change the paths below to match your installation.

set SERVICE_NAME=LISARobotServices
set PR_INSTALL=C:\workspaces\blog\procrun-demo\prunsrv.exe

REM Service log configuration
set PR_LOGPREFIX=%SERVICE_NAME%
set PR_LOGPATH=c:\logs
set PR_STDOUTPUT=c:\logs\stdout.txt
set PR_STDERROR=c:\logs\stderr.txt
set PR_LOGLEVEL=Error

REM Path to java installation
set PR_JVM=C:\Program Files (x86)\Java\jre7\bin\client\jvm.dll
set PR_CLASSPATH=launcher-assembly-1.0

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
prunsrv.exe //IS//%SERVICE_NAME%

REM Run the service in debugging mode
REM start prunsrv.exe //TS//%SERVICE_NAME%

REM Uninstall the service
REM prunsrv.exe //DS//%SERVICE_NAME%