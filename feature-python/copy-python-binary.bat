@echo off
REM Copy python3 binary from jniLibs to assets for each ABI

setlocal enabledelayedexpansion

set "PROJECT_ROOT=%~dp0"
set "JNILIBS_DIR=%PROJECT_ROOT%impl\src\main\jniLibs"
set "ASSETS_DIR=%PROJECT_ROOT%src\main\assets\python3.14\bin"

echo Copying python3 binaries from jniLibs to assets...
echo Project root: %PROJECT_ROOT%
echo JNILIBS_DIR: %JNILIBS_DIR%
echo ASSETS_DIR: %ASSETS_DIR%
echo.

REM Create destination directory
if not exist "%ASSETS_DIR%" mkdir "%ASSETS_DIR%"

REM Copy for each ABI
for %%a in (arm64-v8a x86_64) do (
    set "src=%JNILIBS_DIR%\%%a\prefix\bin\python3"
    set "dst=%ASSETS_DIR%\python3.%%a"
    
    if exist "!src!" (
        copy /Y "!src!" "!dst!" >nul
        echo [OK] Copied %%a: !dst!
    ) else (
        echo [FAIL] Not found: !src!
    )
)

echo.
echo Done!
pause
