# MinIO 本地启动脚本 (business-qa 开发环境)
# 用法: 右键 -> 使用 PowerShell 运行，或在 PowerShell 中执行: .\scripts\start-minio.ps1

$MINIO_HOME = "C:\minio"

# 检查 MinIO 是否已安装
if (-not (Test-Path "$MINIO_HOME\minio.exe")) {
    Write-Host "[ERROR] MinIO not found at $MINIO_HOME\minio.exe" -ForegroundColor Red
    Write-Host "Please download from https://min.io/download and place minio.exe in $MINIO_HOME" -ForegroundColor Yellow
    pause
    exit 1
}

# 检查 license 文件
if (-not (Test-Path "$MINIO_HOME\minio.license")) {
    Write-Host "[ERROR] License not found at $MINIO_HOME\minio.license" -ForegroundColor Red
    Write-Host "Please register at https://min.io/pricing (Free tier) and download the license file" -ForegroundColor Yellow
    pause
    exit 1
}

# 创建数据目录
if (-not (Test-Path "$MINIO_HOME\data")) {
    New-Item -ItemType Directory -Path "$MINIO_HOME\data" -Force | Out-Null
}

# 设置管理员凭证（可通过环境变量覆盖）
if (-not $env:MINIO_ROOT_USER) { $env:MINIO_ROOT_USER = "minioadmin" }
if (-not $env:MINIO_ROOT_PASSWORD) { $env:MINIO_ROOT_PASSWORD = "minioadmin" }

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  MinIO Server (business-qa dev)" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  API:     http://localhost:9000" -ForegroundColor Green
Write-Host "  Console: http://localhost:9001" -ForegroundColor Green
Write-Host "  User:    $($env:MINIO_ROOT_USER)" -ForegroundColor Gray
Write-Host "  Pass:    $($env:MINIO_ROOT_PASSWORD)" -ForegroundColor Gray
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

& "$MINIO_HOME\minio.exe" server "$MINIO_HOME\data" --console-address ":9001" --license "$MINIO_HOME\minio.license"
