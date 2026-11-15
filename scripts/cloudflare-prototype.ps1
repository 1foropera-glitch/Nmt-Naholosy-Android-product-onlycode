param(
    [ValidateSet('Prepare', 'Login', 'Status', 'Check')]
    [string]$Action = 'Status'
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskPrototype = Join-Path $taskRoot 'workers/prototype'
$taskPrivate = Join-Path $taskRoot '.local-verification/cloudflare-local'
$taskWrangler = Join-Path $taskPrototype 'node_modules/wrangler/bin/wrangler.js'
if (-not (Test-Path -LiteralPath $taskWrangler)) { throw 'Run npm.cmd ci --prefix workers/prototype first.' }
$taskNode = (Get-Command node -ErrorAction Stop).Source
$taskSavedConfig = $env:XDG_CONFIG_HOME
$taskSavedMetrics = $env:WRANGLER_SEND_METRICS
$taskSavedLog = $env:WRANGLER_LOG_PATH
New-Item -ItemType Directory -Force -Path $taskPrivate | Out-Null
Push-Location -LiteralPath $taskPrototype
try {
    # Process-local paths. OAuth credentials stay in ignored workspace storage.
    $env:XDG_CONFIG_HOME = Join-Path $taskPrivate 'config'
    $env:WRANGLER_SEND_METRICS = 'false'
    $env:WRANGLER_LOG_PATH = Join-Path $taskPrivate 'wrangler.log'
    function Invoke-TaskWrangler([string[]]$Arguments) {
        & $taskNode $taskWrangler @Arguments
        if ($LASTEXITCODE -ne 0) { throw "Wrangler failed with exit code $LASTEXITCODE" }
    }
    switch ($Action) {
        'Prepare' {
            $taskVars = Join-Path $taskPrototype '.dev.vars'
            if (Test-Path -LiteralPath $taskVars) {
                if (([IO.File]::ReadAllText($taskVars)) -notmatch '(?m)^PROBE_TOKEN=[a-f0-9]{64}\s*$') {
                    throw 'Existing .dev.vars has no valid PROBE_TOKEN. It was not overwritten.'
                }
                Write-Output 'Existing private prototype secret preserved.'
            } else {
                $taskBytes = New-Object byte[] 32
                $taskRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
                try { $taskRandom.GetBytes($taskBytes) } finally { $taskRandom.Dispose() }
                $taskSecret = ([BitConverter]::ToString($taskBytes)).Replace('-', '').ToLowerInvariant()
                [IO.File]::WriteAllText($taskVars, "PROBE_TOKEN=$taskSecret`n", (New-Object Text.UTF8Encoding($false)))
                Write-Output 'Private prototype secret created locally; value is not displayed.'
            }
        }
        'Login' {
            Invoke-TaskWrangler -Arguments @('login', '--scopes', 'account:read', 'user:read', 'workers_scripts:write', 'workers_tail:read')
        }
        'Status' { Invoke-TaskWrangler -Arguments @('whoami') }
        'Check' {
            Invoke-TaskWrangler -Arguments @('deploy', '--dry-run', '--outdir', 'dist')
            & $taskNode '--test' 'test/prototype.test.mjs'
            if ($LASTEXITCODE -ne 0) { throw "Prototype tests failed with exit code $LASTEXITCODE" }
        }
    }
} finally {
    Pop-Location
    $env:XDG_CONFIG_HOME = $taskSavedConfig
    $env:WRANGLER_SEND_METRICS = $taskSavedMetrics
    $env:WRANGLER_LOG_PATH = $taskSavedLog
}
