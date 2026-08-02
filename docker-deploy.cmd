@echo off
setlocal

set IMAGE_NAME=chat-diet-backend
set CONTAINER_NAME=chat-diet-backend
set HOST_PORT=8080
set DATA_DIR=%~dp0backend\data

if not exist "%~dp0backend\src\main\resources\application.yml" (
    echo ERROR: backend\src\main\resources\application.yml is missing.
    echo Copy application.yml.example to application.yml and fill in your Anthropic API key.
    exit /b 1
)

if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"

echo Building %IMAGE_NAME% ...
call "%~dp0backend\gradlew.bat" -p "%~dp0backend" dockerBuild
if errorlevel 1 (
    echo Docker build failed.
    exit /b 1
)

echo Stopping existing container (if any) ...
docker stop %CONTAINER_NAME% >nul 2>&1
docker rm %CONTAINER_NAME% >nul 2>&1

echo Starting %CONTAINER_NAME% on port %HOST_PORT% ...
docker run -d ^
    --name %CONTAINER_NAME% ^
    -p %HOST_PORT%:8080 ^
    -v "%DATA_DIR%:/app/data" ^
    --restart unless-stopped ^
    %IMAGE_NAME%

if errorlevel 1 (
    echo Failed to start container.
    exit /b 1
)

echo Deployed. Backend running at http://localhost:%HOST_PORT%
endlocal
