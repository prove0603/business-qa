@echo off
REM MinIO 本地启动脚本 (business-qa 开发环境)
REM 双击即可运行

set MINIO_HOME=C:\minio

if not exist "%MINIO_HOME%\minio.exe" (
    echo [ERROR] MinIO not found at %MINIO_HOME%\minio.exe
    echo Please download from https://min.io/download
    pause
    exit /b 1
)

if not exist "%MINIO_HOME%\minio.license" (
    echo [ERROR] License not found at %MINIO_HOME%\minio.license
    pause
    exit /b 1
)

if not exist "%MINIO_HOME%\data" mkdir "%MINIO_HOME%\data"

if "%MINIO_ROOT_USER%"=="" set MINIO_ROOT_USER=minioadmin
if "%MINIO_ROOT_PASSWORD%"=="" set MINIO_ROOT_PASSWORD=minioadmin

echo ============================================
echo   MinIO Server (business-qa dev)
echo ============================================
echo   API:     http://localhost:9000
echo   Console: http://localhost:9001
echo   User:    %MINIO_ROOT_USER%
echo   Pass:    %MINIO_ROOT_PASSWORD%
echo ============================================
echo.

"%MINIO_HOME%\minio.exe" server "%MINIO_HOME%\data" --console-address ":9001" --license "%MINIO_HOME%\minio.license"
