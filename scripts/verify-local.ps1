param([switch]$Emulators, [switch]$Clean)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $taskRoot
try {
    function Invoke-Checked([string]$Program, [string[]]$Arguments) {
        & $Program @Arguments
        if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE" }
    }
    Invoke-Checked 'python' @('scripts/validate-source.py', '--skip-kotlin')
    if ($Clean) { Invoke-Checked '.\gradlew.bat' @('clean', '--console=plain') }
    Invoke-Checked '.\gradlew.bat' @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug', '--console=plain')
    Invoke-Checked 'python' @('scripts/verify-apk.py', 'app/build/outputs/apk/debug/app-debug.apk', '--project-dex-dir', 'app/build/intermediates/dex/debug/mergeProjectDexDebug')
    if ((& node --version) -notmatch '^v22\.') { throw 'Install/use Node 22 for backend verification, then rerun.' }
    Invoke-Checked 'npm.cmd' @('ci', '--ignore-scripts', '--prefix', 'functions')
    Invoke-Checked 'npm.cmd' @('test', '--prefix', 'functions')
    Invoke-Checked 'npm.cmd' @('run', 'check', '--prefix', 'functions')
    Invoke-Checked 'npm.cmd' @('audit', '--omit=dev', '--audit-level=high', '--prefix', 'functions')
    if ($Emulators) {
        Invoke-Checked 'npx.cmd' @('--yes', 'firebase-tools@15.34.2', 'emulators:exec', '--only', 'auth,firestore', '--project', 'demo-naholosy', 'node scripts/run-emulator-tests.mjs')
    }
} finally { Pop-Location }
