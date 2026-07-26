@echo off
REM ============================================================
REM  release.bat - All-in-One Release Wrapper cho LichKipSDV
REM  Cú pháp sử dụng:
REM    release.bat                           -> Tự động build & release phiên bản hiện tại
REM    release.bat "mô tả thay đổi"          -> Thêm mô tả thay đổi tiếng Việt
REM    release.bat "mô tả thay đổi" -AutoBump -> Tự nâng phiên bản (VD: 4.68 -> 4.69)
REM ============================================================

cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release.ps1" -Note "%~1" %2 %3 %4 %5
if errorlevel 1 (
    echo.
    echo *** BÀI VIẾT RELEASE GẶP LỖI - VUI LÒNG KIỂM TRA LOG Ở TRÊN ***
    pause
    exit /b 1
)
pause
