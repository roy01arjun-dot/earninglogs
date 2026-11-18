# Creates the Google Play upload key for Earninglogs and the gitignored keystore.properties
# that the release build reads. Run it yourself: it asks for a password, and that password
# only ever goes into the keystore and keystore.properties on this PC.
#
#   powershell -ExecutionPolicy Bypass -File scripts\create-upload-key.ps1
#
# Keep a backup of the .jks file and the password (e.g. in a password manager). With Play App
# Signing a lost upload key can be reset through Play Console support, but it takes days.

$ErrorActionPreference = "Stop"
$logFile = Join-Path $env:USERPROFILE ".earninglogs-signing\create-key.log"
# Any failure is written to create-key.log (the password is never logged) and the window stays open.
trap {
    $msg = "$(Get-Date -Format s) ERROR: $($_.Exception.Message) (line $($_.InvocationInfo.ScriptLineNumber))"
    New-Item -ItemType Directory -Force (Split-Path $logFile) | Out-Null
    Add-Content -Path $logFile -Value $msg
    Write-Host ""
    Write-Host $msg -ForegroundColor Red
    Write-Host "Details saved to $logFile" -ForegroundColor Yellow
    Read-Host "Press Enter to close"
    exit 1
}
function Log($text) {
    New-Item -ItemType Directory -Force (Split-Path $logFile) | Out-Null
    Add-Content -Path $logFile -Value "$(Get-Date -Format s) $text"
}
Log "started"
$projectRoot = Split-Path -Parent $PSScriptRoot
$keyDir = Join-Path $env:USERPROFILE ".earninglogs-signing"
$keystore = Join-Path $keyDir "upload-key.jks"
$propsFile = Join-Path $projectRoot "keystore.properties"

$keytool = "keytool"
if ($env:JAVA_HOME) { $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe" }
elseif (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    $jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $keytool = Join-Path $jdk.FullName "bin\keytool.exe" }
}

if (Test-Path $keystore) {
    Write-Host "An upload key already exists at $keystore - not overwriting it." -ForegroundColor Yellow
    exit 1
}
New-Item -ItemType Directory -Force $keyDir | Out-Null

function Plain($secure) {
    [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

# Ask until the two passwords match (typing is hidden, so mistakes are easy).
for ($attempt = 1; $attempt -le 5; $attempt++) {
    Write-Host ""
    Write-Host "Choose a password for the upload key (at least 6 characters)." -ForegroundColor Cyan
    Write-Host "Nothing appears while you type - that's normal." -ForegroundColor DarkGray
    $plain1 = Plain (Read-Host "Password" -AsSecureString)
    if ($plain1.Length -lt 6) { Write-Host "Too short - use at least 6 characters. Try again." -ForegroundColor Red; Log "password too short"; continue }
    $plain2 = Plain (Read-Host "Repeat password" -AsSecureString)
    if ($plain1 -ne $plain2) { Write-Host "The two passwords didn't match. Try again." -ForegroundColor Red; Log "passwords did not match"; continue }
    break
}
if ($plain1 -ne $plain2 -or $plain1.Length -lt 6) { throw "No matching password after 5 tries" }

$name = Read-Host "Your name or business name (shown in the certificate)"
if ([string]::IsNullOrWhiteSpace($name)) { $name = "Earninglogs" }
$dname = "CN=$($name -replace '[,=+<>#;\\"]', ' '), O=Earninglogs"

# Passing the password through the environment keeps it out of the process list.
$env:EARNINGLOGS_KS_PASS = $plain1
Write-Host "Creating the key (this takes a few seconds)..." -ForegroundColor Cyan
Log "running keytool: $keytool"
try {
    # keytool prints progress on stderr; don't let PowerShell treat that as a failure.
    $ErrorActionPreference = "Continue"
    & $keytool -genkeypair -keystore $keystore -alias upload -keyalg RSA -keysize 4096 -validity 10000 `
        -storetype PKCS12 -dname $dname -storepass:env EARNINGLOGS_KS_PASS -keypass:env EARNINGLOGS_KS_PASS
    $code = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($code -ne 0) { throw "keytool failed with exit code $code" }
} finally {
    Remove-Item Env:EARNINGLOGS_KS_PASS -ErrorAction SilentlyContinue
}

# .properties files need each backslash written twice. String.Replace is literal (no regex).
$escapedPath = $keystore.Replace('\', '\\')
$escapedPass = $plain1.Replace('\', '\\')
@"
# Upload key for Google Play. NEVER commit this file (it's in .gitignore).
storeFile=$escapedPath
storePassword=$escapedPass
keyAlias=upload
keyPassword=$escapedPass
"@ | Set-Content -Path $propsFile -Encoding ascii

# Only the current user can read the key and the properties file.
foreach ($f in @($keystore, $propsFile)) {
    icacls $f /inheritance:r /grant:r "$($env:USERNAME):(R,W)" | Out-Null
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
Write-Host "  Key:        $keystore"
Write-Host "  Build file: $propsFile"
Write-Host "Back up the key file and the password now (e.g. password manager + a USB stick)."
Log "finished OK"
Read-Host "Press Enter to close"
