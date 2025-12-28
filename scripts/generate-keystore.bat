@echo off
setlocal EnableDelayedExpansion

set ROOT_DIR=%~dp0..
set KEYSTORE_DIR=%ROOT_DIR%\app\keystore
set KEYSTORE_FILE=%KEYSTORE_DIR%\vinor-release.jks
set EXAMPLE_PROPS=%ROOT_DIR%\key.properties.example

if not exist "%KEYSTORE_DIR%" (
  mkdir "%KEYSTORE_DIR%"
)

if exist "%KEYSTORE_FILE%" (
  echo Keystore already exists at %KEYSTORE_FILE%. Nothing to do.
  exit /b 0
)

if "%STORE_PASS%"=="" set STORE_PASS=CHANGE_ME_STORE_PASSWORD
if "%KEY_PASS%"=="" set KEY_PASS=CHANGE_ME_KEY_PASSWORD
if "%KEY_ALIAS%"=="" set KEY_ALIAS=vinor_release
if "%DN%"=="" set DN=CN=vinor,O=vinor,L=Tehran,C=IR

REM find keytool
if "%JAVA_HOME%"=="" (
  where keytool >nul 2>&1
  if errorlevel 1 (
    echo keytool not found. Install JDK or set JAVA_HOME.
    exit /b 1
  ) else (
    set KEYTOOL=keytool
  )
) else (
  set KEYTOOL="%JAVA_HOME%\bin\keytool.exe"
)

echo Generating keystore at %KEYSTORE_FILE% ...
%KEYTOOL% -genkeypair -v -keystore "%KEYSTORE_FILE%" -alias "%KEY_ALIAS%" -keyalg RSA -keysize 2048 -validity 36500 -storepass "%STORE_PASS%" -keypass "%KEY_PASS%" -dname "%DN%"
if errorlevel 1 (
  echo Failed to generate keystore.
  exit /b 1
)

if not exist "%EXAMPLE_PROPS%" (
  > "%EXAMPLE_PROPS%" echo storeFile=app/keystore/vinor-release.jks
  >> "%EXAMPLE_PROPS%" echo storePassword=CHANGE_ME_STORE_PASSWORD
  >> "%EXAMPLE_PROPS%" echo keyAlias=vinor_release
  >> "%EXAMPLE_PROPS%" echo keyPassword=CHANGE_ME_KEY_PASSWORD
  echo Created %EXAMPLE_PROPS% (please update values).
)

echo.
echo Base64 of keystore (use this for ANDROID_KEYSTORE_BASE64 secret):
powershell -NoProfile -Command "$b=[Convert]::ToBase64String([IO.File]::ReadAllBytes('%KEYSTORE_FILE%')); Write-Output $b"
echo.
echo Done.

exit /b 0
*** End Patch```}ൈ_GRANTED_EDITOR_ABORTED_UNKNOWN_REASON codeblocks=```*** Begin Patch

