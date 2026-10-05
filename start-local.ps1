Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$settingsPath = Join-Path $PSScriptRoot '.env.local'
if (-not (Test-Path -LiteralPath $settingsPath)) {
    throw 'Missing .env.local. See SECURITY_HARDENING.md for local setup.'
}

$allowed = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'REDIS_HOST', 'REDIS_PORT',
    'SPRING_DATA_REDIS_PASSWORD', 'HERB_JWT_SECRET', 'OSS_ACCESS_KEY_ID',
    'OSS_ACCESS_KEY_SECRET', 'ZHIPU_API_KEY', 'HERB_BIND_ADDRESS',
    'HERB_API_DOCS_ENABLED', 'HERB_ALLOWED_ORIGINS')
foreach ($line in Get-Content -LiteralPath $settingsPath) {
    if ($line.Trim().Length -eq 0 -or $line.TrimStart().StartsWith('#')) { continue }
    $parts = $line -split '=', 2
    if ($parts.Count -ne 2 -or $parts[0] -notin $allowed) {
        throw "Invalid setting in .env.local: $($parts[0])"
    }
    [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
}

Push-Location $PSScriptRoot
try {
    mvn -gs maven-settings.xml -s maven-settings.xml spring-boot:run
    $runExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $runExitCode
