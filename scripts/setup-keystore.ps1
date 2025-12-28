Param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Resolve project root relative to this script folder
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Resolve-Path (Join-Path $ScriptDir '..')
$KeystoreDir = Join-Path $RootDir 'app/keystore'
$KeystoreFile = Join-Path $KeystoreDir 'vinor-release.jks'
$SecretsOut = Join-Path $ScriptDir 'github-secrets.txt'

if (-not (Test-Path $KeystoreDir)) {
    New-Item -ItemType Directory -Force -Path $KeystoreDir | Out-Null
}

if (Test-Path $KeystoreFile) {
    Write-Host "Keystore already exists at '$KeystoreFile'. Nothing to do."
    Write-Host "This script is intended to be run only once to generate secrets for GitHub."
    exit 0
}

function New-RandomString([int]$Length) {
    # Use a safe character set for BAT/property files (avoid shell metacharacters)
    $chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] ($Length)
    $sb = New-Object System.Text.StringBuilder
    $rng.GetBytes($bytes)
    for ($i = 0; $i -lt $Length; $i++) {
        [void]$sb.Append($chars[ $bytes[$i] % $chars.Length ])
    }
    $sb.ToString()
}

$StorePass = New-RandomString -Length 32
$KeyPass = New-RandomString -Length 32
$KeyAlias = 'vinor_release'
$DName = 'CN=vinor,O=vinor,L=Tehran,C=IR'

# Find keytool: prefer JAVA_HOME, fall back to PATH
$Keytool = $null
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
    if (Test-Path $candidate) { $Keytool = $candidate }
}
if (-not $Keytool) {
    $cmd = Get-Command keytool -ErrorAction SilentlyContinue
    if ($cmd) { $Keytool = $cmd.Path }
}
if (-not $Keytool) {
    Write-Error "keytool not found. Install JDK and set JAVA_HOME or add keytool to PATH."
}

Write-Host "Using keytool: $Keytool"
Write-Host "Generating keystore at '$KeystoreFile' ..."

& $Keytool `
  '-genkeypair' '-v' `
  '-keystore' $KeystoreFile `
  '-alias' $KeyAlias `
  '-keyalg' 'RSA' '-keysize' '2048' '-validity' '36500' `
  '-storepass' $StorePass `
  '-keypass' $KeyPass `
  '-dname' $DName

if (-not (Test-Path $KeystoreFile)) {
    Write-Error "Failed to generate keystore."
}

# Produce GitHub Secrets output file (gitignored)
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeystoreFile))
$content = @(
    "ANDROID_KEYSTORE_BASE64=$b64"
    "ANDROID_KEYSTORE_PASSWORD=$StorePass"
    "ANDROID_KEY_ALIAS=$KeyAlias"
    "ANDROID_KEY_PASSWORD=$KeyPass"
) -join [Environment]::NewLine

Set-Content -Path $SecretsOut -NoNewline -Value $content -Encoding UTF8

Write-Host ""
Write-Host "Secrets file written to: $SecretsOut"
Write-Host "Copy these values into GitHub → Settings → Secrets and variables → Actions as repository secrets."
Write-Host ""
Write-Host "Done. Remember: do not commit the keystore or secrets file. CI workflow will decode the keystore and build the APK."


