@echo off
setlocal EnableDelayedExpansion

echo ================================
echo Vinor Auto Commit & Push
echo ================================

REM اگر فایل version.txt وجود ندارد، بساز
if not exist version.txt (
    echo 1.0.0 > version.txt
)

REM خواندن ورژن فعلی
set /p VERSION=<version.txt

REM جدا کردن ورژن
for /f "tokens=1-3 delims=." %%a in ("%VERSION%") do (
    set MAJOR=%%a
    set MINOR=%%b
    set PATCH=%%c
)

REM افزایش PATCH
set /a PATCH+=1

REM ورژن جدید
set NEW_VERSION=%MAJOR%.%MINOR%.%PATCH%

echo New version: %NEW_VERSION%

REM ذخیره ورژن جدید
echo %NEW_VERSION%>version.txt

REM اضافه کردن همه تغییرات
git add -A

REM اگر تغییری برای کامیت نیست، از ساخت کامیت عبور کن
git diff --cached --quiet && git diff --quiet && (
    echo No changes to commit.
) || (
    REM ساخت کامیت
    git commit -m "Vinor Android WebView v%NEW_VERSION%"
)

REM تشخیص نام شاخه جاری (در صورت عدم دسترسی، پیش‌فرض main)
set "BRANCH=main"
for /f "delims=" %%b in ('git rev-parse --abbrev-ref HEAD 2^>nul') do set "BRANCH=%%b"

REM پوش به main
git push origin %BRANCH%

echo ================================
echo DONE - v%NEW_VERSION% pushed 🚀
echo ================================

pause
