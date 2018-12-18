@echo off

REM ###########################################################################
REM #  Script that runs a specified sample                                    #
REM #                                                                         #
REM #  The argument must be the fully qualified sample class to execute       #
REM #                                                                         #
REM #  runsample <sample-to-run>                                              #
REM #                                                                         #
REM #  For example:                                                           #
REM #                                                                         #
REM #  runsample sample.basic.EventSample                                     #
REM ###########################################################################

IF "%1"=="" GOTO help

setLocal EnableDelayedExpansion

SET SCRIPT_PATH=%~dp0
SET SOURCE_DIR=%SCRIPT_PATH%\src
SET CLASSPATH="%SCRIPT_PATH%\lib\*;%SCRIPT_PATH%\src"

REM Find source files
 for /R "%SOURCE_DIR%" %%a in (*.java) do (
   set JAVA_FILES=!JAVA_FILES! "%%a"
 )

:main
REM Remove existing classes
del /s /q "%SOURCE_DIR%\*.class"

REM Perform compilation
javac -classpath %CLASSPATH% %JAVA_FILES%

REM Run sample
java -classpath %CLASSPATH% %1 "%SCRIPT_PATH%\dxlclient.config"
GOTO end

:help
ECHO Usage: runsample ^<sample-to-run^>
ECHO.
ECHO Example: runsample sample.basic.EventSample
GOTO end

:end
