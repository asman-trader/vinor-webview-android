@echo off
setlocal EnableDelayedExpansion
set SKIP_BUILD=0

echo ================================
echo Vinor Direct Install Build
echo ================================

REM اطمینان از وجود version.txt
if not exist version.txt (
    echo 1.0.0> version.txt
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
echo %NEW_VERSION%> version.txt

echo Triggering GitHub Actions (CI) via push...
goto GIT_PUSH

REM مسیر keystore و پراپرتی
set KEYSTORE_DIR=app\keystore
set KEYSTORE_FILE=%KEYSTORE_DIR%\vinor-release.jks
set KEYPROPS=key.properties

if not exist "%KEYSTORE_DIR%" (
    mkdir "%KEYSTORE_DIR%"
)

REM اگر key.properties نبود، تلاش برای ساخت خودکار مقادیر امن و keystore
if not exist "%KEYPROPS%" (
    echo key.properties not found. Running one-time secure keystore setup...
    set "PS_EXE="
    for /f "delims=" %%i in ('where pwsh 2^>nul') do set "PS_EXE=%%i"
    if not defined PS_EXE (
        for /f "delims=" %%i in ('where powershell 2^>nul') do set "PS_EXE=%%i"
    )
    if not defined PS_EXE (
        echo PowerShell not found. Will skip local build and push to trigger GitHub Actions.
        set SKIP_BUILD=1
        goto GIT_PUSH
    )
    "%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -File "scripts\setup-keystore.ps1"
    if errorlevel 1 (
        echo Keystore setup script failed. Will skip local build and push to trigger GitHub Actions.
        set SKIP_BUILD=1
        goto GIT_PUSH
    )
    if not exist "scripts\github-secrets.txt" (
        echo Keystore setup did not produce secrets (scripts\github-secrets.txt). Skipping local build; pushing to trigger CI.
        set SKIP_BUILD=1
        goto GIT_PUSH
    )
    for /f "usebackq tokens=1* delims==" %%a in ("scripts\github-secrets.txt") do (
        if /I "%%a"=="ANDROID_KEYSTORE_PASSWORD" set "KP_STORE_PASS=%%b"
        if /I "%%a"=="ANDROID_KEY_ALIAS" set "KP_ALIAS=%%b"
        if /I "%%a"=="ANDROID_KEY_PASSWORD" set "KP_KEY_PASS=%%b"
    )
    if "%KP_STORE_PASS%"=="" (
        echo Failed to parse secrets. Skipping local build; pushing to trigger CI.
        set SKIP_BUILD=1
        goto GIT_PUSH
    )
    > "%KEYPROPS%" echo storeFile=app/keystore/vinor-release.jks
    >> "%KEYPROPS%" echo storePassword=%KP_STORE_PASS%
    >> "%KEYPROPS%" echo keyAlias=%KP_ALIAS%
    >> "%KEYPROPS%" echo keyPassword=%KP_KEY_PASS%
    echo key.properties created from generated secrets.
)

REM اگر key.properties placeholder دارد، متوقف شو
set NEED_EDIT=0
for /f "tokens=1,2 delims==" %%a in ('type "%KEYPROPS%"') do (
    if /I "%%a"=="storePassword" (
        echo %%b | findstr /C:"CHANGE_ME" >nul && set NEED_EDIT=1
    )
    if /I "%%a"=="keyPassword" (
        echo %%b | findstr /C:"CHANGE_ME" >nul && set NEED_EDIT=1
    )
    if /I "%%a"=="keyAlias" (
        if "%%b"=="" set NEED_EDIT=1
    )
)
if %NEED_EDIT%==1 (
    echo Detected placeholder passwords. Attempting one-time secure keystore setup...
    set "PS_EXE="
    for /f "delims=" %%i in ('where pwsh 2^>nul') do set "PS_EXE=%%i"
    if not defined PS_EXE (
        for /f "delims=" %%i in ('where powershell 2^>nul') do set "PS_EXE=%%i"
    )
    if not defined PS_EXE (
        echo PowerShell not found. Will skip local build and push to trigger GitHub Actions.
        set SKIP_BUILD=1
        goto GIT_PUSH
    )
    "%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -File "scripts\setup-keystore.ps1"
    if errorlevel 1 (
        echo Keystore setup script failed. Will skip local build and push to trigger GitHub Actions.
        set SKIP_BUILD=1
        goto GIT_PUSH
    )
    if not exist "scripts\github-secrets.txt" (
        echo Keystore setup did not produce secrets (scripts\github-secrets.txt). Skipping local build; pushing to trigger CI.
        set SKIP_BUILD=1
        goto GIT_PUSH
    )
    for /f "usebackq tokens=1* delims==" %%a in ("scripts\github-secrets.txt") do (
        if /I "%%a"=="ANDROID_KEYSTORE_PASSWORD" set "KP_STORE_PASS=%%b"
        if /I "%%a"=="ANDROID_KEY_ALIAS" set "KP_ALIAS=%%b"
        if /I "%%a"=="ANDROID_KEY_PASSWORD" set "KP_KEY_PASS=%%b"
    )
    if "%KP_STORE_PASS%"=="" (
        echo Failed to parse secrets. Skipping local build; pushing to trigger CI.
        set SKIP_BUILD=1
        goto GIT_PUSH
    )
    > "%KEYPROPS%" echo storeFile=app/keystore/vinor-release.jks
    >> "%KEYPROPS%" echo storePassword=%KP_STORE_PASS%
    >> "%KEYPROPS%" echo keyAlias=%KP_ALIAS%
    >> "%KEYPROPS%" echo keyPassword=%KP_KEY_PASS%
    echo key.properties updated from generated secrets. Continuing build...
)

REM استخراج مقادیر برای ساخت احتمالی keystore
for /f "tokens=1* delims==" %%a in ('type "%KEYPROPS%"') do (
    if /I "%%a"=="storePassword" set "KP_STORE_PASS=%%b"
    if /I "%%a"=="keyAlias" set "KP_ALIAS=%%b"
    if /I "%%a"=="keyPassword" set "KP_KEY_PASS=%%b"
)

REM اگر keystore نبود، تلاش برای ساخت با keytool (فقط یک‌بار)
if not exist "%KEYSTORE_FILE%" (
    REM --- Find keytool correctly ---
    set "KEYTOOL="
    if defined JAVA_HOME (
      if exist "%JAVA_HOME%\bin\keytool.exe" (
        set "KEYTOOL=%JAVA_HOME%\bin\keytool.exe"
      )
    )
    if not defined KEYTOOL (
      for /f "delims=" %%i in ('where keytool 2^>nul') do set "KEYTOOL=%%i"
    )
    if not defined KEYTOOL (
      echo ERROR: keytool not found. Will skip local build and push to trigger GitHub Actions.
      set SKIP_BUILD=1
      goto GIT_PUSH
    )
    echo Using keytool: "%KEYTOOL%"

    echo Generating keystore at %KEYSTORE_FILE% ...
    "%KEYTOOL%" -genkeypair -v ^
      -keystore "%KEYSTORE_FILE%" ^
      -alias "%KP_ALIAS%" ^
      -keyalg RSA -keysize 2048 -validity 36500 ^
      -storepass "%KP_STORE_PASS%" ^
      -keypass "%KP_KEY_PASS%" ^
      -dname "CN=vinor,O=vinor,L=Tehran,C=IR"
    if errorlevel 1 (
        echo Failed to generate keystore. Skipping local build; pushing to trigger CI.
        set SKIP_BUILD=1
        goto GIT_PUSH
    )
)

if "%SKIP_BUILD%"=="0" (
    REM بیلد Release APK برای نصب مستقیم (با چک‌های پیش‌نیاز Gradle)
    call .\gradlew.bat assembleDirectRelease -x test
    if errorlevel 1 (
        echo Gradle build failed. Skipping local build; will push changes to trigger CI.
        set SKIP_BUILD=1
    )
)

if "%SKIP_BUILD%"=="0" (
    set APK=app\build\outputs\apk\direct
    echo.
    echo APKs at: %APK%
    for %%f in (%APK%\*.apk) do echo   %%f

    REM نصب اختیاری روی دستگاه متصل با adb (در صورت وجود adb و دستگاه)
    set "LASTAPK="
    for %%f in ("%APK%\*.apk") do set "LASTAPK=%%~ff"
    if defined LASTAPK (
        where adb >nul 2>&1
        if errorlevel 1 (
            echo adb not found. Skipping device install. You can install manually: adb install -r -d "%LASTAPK%"
        ) else (
            adb get-state 1>nul 2>nul
            if errorlevel 1 (
                echo No device detected. Skipping adb install.
            ) else (
                echo Installing on connected device: "%LASTAPK%"
                adb install -r -d "%LASTAPK%"
                if errorlevel 1 (
                    echo adb install failed. You can retry manually: adb install -r -d "%LASTAPK%"
                ) else (
                    echo Installed successfully via adb.
                )
            )
        )
    )
)

:GIT_PUSH
REM گیت: اضافه کردن تغییرات، ساخت کامیت و پوش
git add -A
git commit -m "Vinor Android WebView v%NEW_VERSION%"
git push origin main

echo ================================
echo DONE - v%NEW_VERSION% built and pushed 🚀
echo ================================

:END
pause
